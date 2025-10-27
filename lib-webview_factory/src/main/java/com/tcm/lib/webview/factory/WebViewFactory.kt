package com.tcm.lib.webview.factory

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * WebView 图片生产工厂
 * 使用对象池和 Worker 模式管理 WebView 渲染任务
 * 
 * 使用示例：
 * ```
 * val factory = WebViewFactory.getInstance(context)
 * 
 * // 提交任务
 * factory.submitTask(
 *     MermaidRenderTask(
 *         mermaidCode = "graph TD\n    A-->B",
 *         width = 1920,
 *         height = 1080
 *     ),
 *     object : RenderCallback {
 *         override fun onRenderComplete(result: RenderResult) {
 *             when (result) {
 *                 is RenderResult.Success -> {
 *                     // 处理图片数据
 *                     val imageData = result.imageData
 *                 }
 *                 is RenderResult.Error -> {
 *                     // 处理错误
 *                 }
 *             }
 *         }
 *     }
 * )
 * ```
 */
class WebViewFactory private constructor(
    private val context: Context,
    private val workerCount: Int = DEFAULT_WORKER_COUNT,
    private val maxPoolSize: Int = DEFAULT_MAX_POOL_SIZE
) {
    private val webViewPool: WebViewPool
    private val workers: Array<WebViewWorker>
    private val taskQueue = ConcurrentLinkedQueue<TaskWrapper>()
    private val activeTasks = ConcurrentHashMap<String, TaskWrapper>()
    private val isShutdown = AtomicBoolean(false)
    private val workerThread: HandlerThread
    private val workerHandler: Handler
    private val taskIdCounter = AtomicInteger(0)

    init {
        // 初始化 WebView 对象池
        webViewPool = WebViewPool(context, workerCount, maxPoolSize)

        // 创建工作线程（必须在创建 Worker 之前）
        workerThread = HandlerThread("WebViewFactory-Worker-Thread").apply {
            start()
        }
        workerHandler = Handler(workerThread.looper)
        
        // 创建主线程 Handler
        val mainHandler = Handler(Looper.getMainLooper())

        // 初始化 Workers
        workers = Array(workerCount) { index ->
            WebViewWorker(index, webViewPool, mainHandler)
        }

        // 启动任务调度
        startScheduler()
    }

    /**
     * 提交渲染任务
     * @param task 渲染任务
     * @param callback 回调接口
     * @return 任务ID
     */
    fun submitTask(task: MermaidRenderTask, callback: RenderCallback): String {
        if (isShutdown.get()) {
            callback.onRenderComplete(
                RenderResult.Error(
                    task.id,
                    "Factory is shutdown"
                )
            )
            return task.id
        }

        val wrapper = TaskWrapper(task, callback)
        taskQueue.offer(wrapper)
        activeTasks[task.id] = wrapper

        // 通知调度器
        workerHandler.post {
            scheduleNextTask()
        }

        return task.id
    }

    /**
     * 批量提交任务（打字机模式）
     * 按顺序处理任务，并按顺序返回结果
     * @param tasks 任务列表
     * @param callback 统一回调接口
     */
    fun submitTasksBatch(tasks: List<MermaidRenderTask>, callback: RenderCallback) {
        if (isShutdown.get()) {
            tasks.forEach { task ->
                callback.onRenderComplete(
                    RenderResult.Error(
                        task.id,
                        "Factory is shutdown"
                    )
                )
            }
            return
        }

        // 使用顺序回调包装器
        val sequentialCallback = SequentialCallbackWrapper(tasks.map { it.id }, callback)
        
        tasks.forEach { task ->
            val wrappedCallback = object : RenderCallback {
                override fun onRenderComplete(result: RenderResult) {
                    sequentialCallback.onTaskComplete(result)
                }

                override fun onProgress(taskId: String, progress: Int) {
                    callback.onProgress(taskId, progress)
                }
            }
            submitTask(task, wrappedCallback)
        }
    }

    /**
     * 流式提交任务（打字机模式）
     * 创建一个流式任务提交器
     * @param callback 回调接口
     * @return 流式任务提交器
     */
    fun createStreamSubmitter(callback: RenderCallback): StreamTaskSubmitter {
        return StreamTaskSubmitter(this, callback)
    }

    /**
     * 启动任务调度器
     */
    private fun startScheduler() {
        workerHandler.post(object : Runnable {
            override fun run() {
                if (!isShutdown.get()) {
                    scheduleNextTask()
                    // 每100ms检查一次任务队列
                    workerHandler.postDelayed(this, 100)
                }
            }
        })
    }

    /**
     * 调度下一个任务
     */
    private fun scheduleNextTask() {
        // 查找空闲的 Worker
        val idleWorker = workers.firstOrNull { it.isIdle() } ?: return

        // 从队列中取出任务
        val taskWrapper = taskQueue.poll() ?: return

        // 分配任务给 Worker
        idleWorker.executeTask(taskWrapper.task, taskWrapper.callback)
    }

    /**
     * 取消任务
     * @param taskId 任务ID
     * @return 是否成功取消
     */
    fun cancelTask(taskId: String): Boolean {
        val wrapper = activeTasks.remove(taskId) ?: return false
        return taskQueue.remove(wrapper)
    }

    /**
     * 获取工厂状态
     */
    fun getFactoryStats(): FactoryStats {
        val poolStats = webViewPool.getPoolStats()
        val idleWorkerCount = workers.count { it.isIdle() }
        val busyWorkerCount = workerCount - idleWorkerCount

        return FactoryStats(
            workerCount = workerCount,
            idleWorkerCount = idleWorkerCount,
            busyWorkerCount = busyWorkerCount,
            queuedTaskCount = taskQueue.size,
            activeTaskCount = activeTasks.size,
            poolStats = poolStats
        )
    }

    /**
     * 关闭工厂
     */
    fun shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            // 清空任务队列
            taskQueue.clear()
            activeTasks.clear()

            // 关闭对象池
            webViewPool.shutdown()

            // 停止工作线程
            workerThread.quitSafely()
        }
    }

    /**
     * 任务包装器
     */
    private data class TaskWrapper(
        val task: MermaidRenderTask,
        val callback: RenderCallback,
        val submitTime: Long = System.currentTimeMillis()
    )

    /**
     * 顺序回调包装器
     * 确保结果按照提交顺序返回
     */
    private class SequentialCallbackWrapper(
        private val taskIds: List<String>,
        private val callback: RenderCallback
    ) {
        private val results = ConcurrentHashMap<String, RenderResult>()
        private var nextIndex = 0
        private val lock = Any()

        fun onTaskComplete(result: RenderResult) {
            synchronized(lock) {
                results[result.getTaskId()] = result
                deliverSequentialResults()
            }
        }

        private fun deliverSequentialResults() {
            while (nextIndex < taskIds.size) {
                val taskId = taskIds[nextIndex]
                val result = results.remove(taskId) ?: break
                callback.onRenderComplete(result)
                nextIndex++
            }
        }

        private fun RenderResult.getTaskId(): String {
            return when (this) {
                is RenderResult.Success -> this.taskId
                is RenderResult.Error -> this.taskId
            }
        }
    }

    /**
     * 工厂统计信息
     */
    data class FactoryStats(
        val workerCount: Int,
        val idleWorkerCount: Int,
        val busyWorkerCount: Int,
        val queuedTaskCount: Int,
        val activeTaskCount: Int,
        val poolStats: WebViewPool.PoolStats
    ) {
        override fun toString(): String {
            return """
                Factory Stats:
                - Workers: $workerCount (Idle: $idleWorkerCount, Busy: $busyWorkerCount)
                - Tasks: Queued=$queuedTaskCount, Active=$activeTaskCount
                - WebView Pool: ${poolStats.availableSize}/${poolStats.totalSize} available (max: ${poolStats.maxSize})
            """.trimIndent()
        }
    }

    companion object {
        private const val DEFAULT_WORKER_COUNT = 3
        private const val DEFAULT_MAX_POOL_SIZE = 5

        @Volatile
        private var instance: WebViewFactory? = null

        /**
         * 获取单例实例
         */
        fun getInstance(context: Context): WebViewFactory {
            return instance ?: synchronized(this) {
                instance ?: WebViewFactory(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * 获取自定义配置的实例
         */
        fun getInstance(
            context: Context,
            workerCount: Int,
            maxPoolSize: Int
        ): WebViewFactory {
            return instance ?: synchronized(this) {
                instance ?: WebViewFactory(
                    context.applicationContext,
                    workerCount,
                    maxPoolSize
                ).also {
                    instance = it
                }
            }
        }

        /**
         * 销毁单例实例
         */
        fun destroyInstance() {
            instance?.shutdown()
            instance = null
        }
    }
}

/**
 * 流式任务提交器
 * 支持打字机模式逐个提交任务
 */
class StreamTaskSubmitter(
    private val factory: WebViewFactory,
    private val callback: RenderCallback
) {
    private val taskList = mutableListOf<String>()
    private val resultQueue = ConcurrentLinkedQueue<RenderResult>()
    private val lock = Any()
    private var nextDeliveryIndex = 0

    /**
     * 提交下一个任务
     */
    fun submitNext(task: MermaidRenderTask) {
        synchronized(lock) {
            taskList.add(task.id)
        }

        factory.submitTask(task, object : RenderCallback {
            override fun onRenderComplete(result: RenderResult) {
                onTaskComplete(result)
            }

            override fun onProgress(taskId: String, progress: Int) {
                callback.onProgress(taskId, progress)
            }
        })
    }

    /**
     * 批量提交
     */
    fun submitBatch(tasks: List<MermaidRenderTask>) {
        tasks.forEach { submitNext(it) }
    }

    private fun onTaskComplete(result: RenderResult) {
        synchronized(lock) {
            resultQueue.offer(result)
            deliverSequentialResults()
        }
    }

    private fun deliverSequentialResults() {
        while (nextDeliveryIndex < taskList.size) {
            val expectedTaskId = taskList[nextDeliveryIndex]
            val result = resultQueue.peek()
            
            if (result != null && result.getTaskId() == expectedTaskId) {
                resultQueue.poll()
                callback.onRenderComplete(result)
                nextDeliveryIndex++
            } else {
                break
            }
        }
    }

    private fun RenderResult.getTaskId(): String {
        return when (this) {
            is RenderResult.Success -> this.taskId
            is RenderResult.Error -> this.taskId
        }
    }

    /**
     * 获取已完成任务数量
     */
    fun getCompletedCount(): Int = synchronized(lock) { nextDeliveryIndex }

    /**
     * 获取总任务数量
     */
    fun getTotalCount(): Int = synchronized(lock) { taskList.size }
}

