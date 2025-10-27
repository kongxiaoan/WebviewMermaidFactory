package com.tcm.lib.webview.factory.example

import android.content.Context
import android.util.Log
import com.tcm.lib.webview.factory.*
import java.io.File

/**
 * 使用示例
 * 这是一个演示如何使用 WebViewFactory 的示例类
 */
class ExampleUsage(private val context: Context) {

    companion object {
        private const val TAG = "ExampleUsage"
    }

    /**
     * 示例1: 基本使用 - 渲染单个图表
     */
    fun example1_BasicUsage() {
        val factory = WebViewFactory.getInstance(context)

        val task = MermaidRenderTask(
            mermaidCode = """
                graph TD
                    A[开始] --> B{条件判断}
                    B -->|条件1| C[处理1]
                    B -->|条件2| D[处理2]
                    C --> E[结束]
                    D --> E
            """.trimIndent(),
            width = 1920,
            height = 1080,
            quality = 90,
            format = ImageFormat.PNG
        )

        factory.submitTask(task, object : RenderCallback {
            override fun onRenderComplete(result: RenderResult) {
                when (result) {
                    is RenderResult.Success -> {
                        // 保存图片到文件
                        val file = File(context.filesDir, "flowchart.png")
                        file.writeBytes(result.imageData)
                        Log.d(TAG, "✅ 图片已保存: ${file.absolutePath}")
                        Log.d(TAG, "图片大小: ${result.imageData.size / 1024}KB")
                    }
                    is RenderResult.Error -> {
                        Log.e(TAG, "❌ 渲染失败: ${result.errorMessage}", result.exception)
                    }
                }
            }

            override fun onProgress(taskId: String, progress: Int) {
                Log.d(TAG, "📊 渲染进度: $progress%")
            }
        })
    }

    /**
     * 示例2: 批量处理 - 渲染多个图表（按顺序返回）
     */
    fun example2_BatchProcessing() {
        val factory = WebViewFactory.getInstance(context)

        val tasks = listOf(
            MermaidRenderTask(
                mermaidCode = "graph TD\n    A[步骤1] --> B[步骤2]",
                format = ImageFormat.PNG
            ),
            MermaidRenderTask(
                mermaidCode = "graph LR\n    C[开始] --> D[结束]",
                format = ImageFormat.PNG
            ),
            MermaidRenderTask(
                mermaidCode = """
                    sequenceDiagram
                        Alice->>John: Hello John
                        John-->>Alice: Hi Alice
                """.trimIndent(),
                format = ImageFormat.PNG
            )
        )

        var completedCount = 0
        factory.submitTasksBatch(tasks, object : RenderCallback {
            override fun onRenderComplete(result: RenderResult) {
                completedCount++
                Log.d(TAG, "📦 批量处理: 完成 $completedCount/${tasks.size}")
                
                when (result) {
                    is RenderResult.Success -> {
                        val file = File(context.filesDir, "batch_${completedCount}.png")
                        file.writeBytes(result.imageData)
                        Log.d(TAG, "✅ 第 $completedCount 张图片已保存")
                    }
                    is RenderResult.Error -> {
                        Log.e(TAG, "❌ 第 $completedCount 张图片失败: ${result.errorMessage}")
                    }
                }
            }
        })
    }

    /**
     * 示例3: 流式提交（打字机模式）
     */
    fun example3_StreamSubmission() {
        val factory = WebViewFactory.getInstance(context)

        // 模拟打字机模式的数据流
        val mermaidCodeStream = listOf(
            "graph TD\n    A1-->B1",
            "graph TD\n    A2-->B2",
            "graph TD\n    A3-->B3",
            "graph TD\n    A4-->B4",
            "graph TD\n    A5-->B5"
        )

        // 先创建一个变量来保存已完成的计数
        var completedCount = 0
        
        val submitter = factory.createStreamSubmitter(object : RenderCallback {
            override fun onRenderComplete(result: RenderResult) {
                completedCount++
                
                Log.d(TAG, "⌨️ 打字机模式: 完成 $completedCount/${mermaidCodeStream.size}")
                
                when (result) {
                    is RenderResult.Success -> {
                        val file = File(context.filesDir, "stream_$completedCount.png")
                        file.writeBytes(result.imageData)
                        Log.d(TAG, "✅ 流式图片 $completedCount 已保存")
                    }
                    is RenderResult.Error -> {
                        Log.e(TAG, "❌ 流式图片失败: ${result.errorMessage}")
                    }
                }
            }

            override fun onProgress(taskId: String, progress: Int) {
                Log.d(TAG, "📊 任务 $taskId 进度: $progress%")
            }
        })

        // 模拟打字机效果，每隔100ms提交一个任务
        Thread {
            mermaidCodeStream.forEach { code ->
                submitter.submitNext(MermaidRenderTask(mermaidCode = code))
                Log.d(TAG, "📝 提交了新任务")
                Thread.sleep(100)
            }
        }.start()
    }

    /**
     * 示例4: 不同格式输出
     */
    fun example4_DifferentFormats() {
        val factory = WebViewFactory.getInstance(context)

        val mermaidCode = """
            graph TB
                A[PNG格式] --> B[JPEG格式]
                B --> C[WebP格式]
        """.trimIndent()

        // PNG 格式
        factory.submitTask(
            MermaidRenderTask(
                mermaidCode = mermaidCode,
                format = ImageFormat.PNG,
                quality = 100
            ),
            createSaveCallback("output.png")
        )

        // JPEG 格式（更小的文件大小）
        factory.submitTask(
            MermaidRenderTask(
                mermaidCode = mermaidCode,
                format = ImageFormat.JPG,
                quality = 85
            ),
            createSaveCallback("output.jpg")
        )

        // WebP 格式（最佳压缩率）
        factory.submitTask(
            MermaidRenderTask(
                mermaidCode = mermaidCode,
                format = ImageFormat.WEBP,
                quality = 80
            ),
            createSaveCallback("output.webp")
        )
    }

    /**
     * 示例5: 监控工厂状态
     */
    fun example5_MonitorFactoryStats() {
        val factory = WebViewFactory.getInstance(context)

        // 提交一些任务
        repeat(10) { index ->
            factory.submitTask(
                MermaidRenderTask(
                    mermaidCode = "graph TD\n    A$index-->B$index"
                ),
                object : RenderCallback {
                    override fun onRenderComplete(result: RenderResult) {
                        // 每完成一个任务，打印状态
                        val stats = factory.getFactoryStats()
                        Log.d(TAG, "📊 工厂状态:\n${stats}")
                    }
                }
            )
        }

        // 定期检查状态
        Thread {
            repeat(5) {
                Thread.sleep(1000)
                val stats = factory.getFactoryStats()
                Log.d(TAG, """
                    📊 实时状态:
                    - 工作中: ${stats.busyWorkerCount}/${stats.workerCount}
                    - 队列中: ${stats.queuedTaskCount}
                    - 活跃任务: ${stats.activeTaskCount}
                    - WebView池: ${stats.poolStats.availableSize}/${stats.poolStats.totalSize}
                """.trimIndent())
            }
        }.start()
    }

    /**
     * 示例6: 任务取消
     */
    fun example6_CancelTask() {
        val factory = WebViewFactory.getInstance(context)

        val taskId = factory.submitTask(
            MermaidRenderTask(
                mermaidCode = "graph TD\n    A-->B"
            ),
            object : RenderCallback {
                override fun onRenderComplete(result: RenderResult) {
                    Log.d(TAG, "这个回调不应该被调用")
                }
            }
        )

        // 立即尝试取消任务
        Thread.sleep(50)
        val cancelled = factory.cancelTask(taskId)
        Log.d(TAG, if (cancelled) "✅ 任务已取消" else "❌ 任务已在执行，无法取消")
    }

    /**
     * 示例7: 复杂的序列图
     */
    fun example7_ComplexDiagram() {
        val factory = WebViewFactory.getInstance(context)

        val sequenceDiagram = """
            sequenceDiagram
                participant 用户
                participant 前端
                participant 后端
                participant 数据库
                
                用户->>前端: 发起请求
                activate 前端
                前端->>后端: API调用
                activate 后端
                后端->>数据库: 查询数据
                activate 数据库
                数据库-->>后端: 返回结果
                deactivate 数据库
                后端-->>前端: 返回响应
                deactivate 后端
                前端-->>用户: 显示结果
                deactivate 前端
        """.trimIndent()

        factory.submitTask(
            MermaidRenderTask(
                mermaidCode = sequenceDiagram,
                width = 1920,
                height = 1080,
                quality = 95,
                format = ImageFormat.PNG
            ),
            createSaveCallback("sequence_diagram.png")
        )
    }

    /**
     * 示例8: 自定义 Worker 配置
     */
    fun example8_CustomConfiguration() {
        // 创建自定义配置的工厂
        val factory = WebViewFactory.getInstance(
            context = context,
            workerCount = 5,      // 5个工作线程
            maxPoolSize = 8       // 最多8个WebView实例
        )

        Log.d(TAG, "📐 自定义配置已应用:")
        val stats = factory.getFactoryStats()
        Log.d(TAG, "- Worker数量: ${stats.workerCount}")
        Log.d(TAG, "- WebView池最大值: ${stats.poolStats.maxSize}")
    }

    /**
     * 创建保存文件的回调
     */
    private fun createSaveCallback(filename: String): RenderCallback {
        return object : RenderCallback {
            override fun onRenderComplete(result: RenderResult) {
                when (result) {
                    is RenderResult.Success -> {
                        val file = File(context.filesDir, filename)
                        file.writeBytes(result.imageData)
                        Log.d(TAG, "✅ $filename 已保存 (${result.imageData.size / 1024}KB)")
                    }
                    is RenderResult.Error -> {
                        Log.e(TAG, "❌ $filename 保存失败: ${result.errorMessage}")
                    }
                }
            }

            override fun onProgress(taskId: String, progress: Int) {
                if (progress % 25 == 0) {  // 只记录 25%, 50%, 75%, 100%
                    Log.d(TAG, "📊 $filename 进度: $progress%")
                }
            }
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        WebViewFactory.destroyInstance()
        Log.d(TAG, "🧹 工厂已关闭，资源已释放")
    }
}

/**
 * 在 Activity 中使用示例
 */
/*
class MainActivity : AppCompatActivity() {
    private lateinit var example: ExampleUsage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        example = ExampleUsage(this)
        
        // 运行各种示例
        example.example1_BasicUsage()
        example.example2_BatchProcessing()
        example.example3_StreamSubmission()
    }

    override fun onDestroy() {
        super.onDestroy()
        example.cleanup()
    }
}
*/

