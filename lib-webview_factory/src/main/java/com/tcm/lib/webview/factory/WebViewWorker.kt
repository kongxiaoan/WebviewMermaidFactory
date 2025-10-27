package com.tcm.lib.webview.factory

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebView Worker
 * 负责执行具体的渲染任务
 */
class WebViewWorker(
    private val workerId: Int,
    private val webViewPool: WebViewPool,
    private val mainHandler: Handler
) {
    @Volatile
    private var currentTask: MermaidRenderTask? = null
    
    @Volatile
    private var callback: RenderCallback? = null
    
    private val isWorking = AtomicBoolean(false)
    
    /**
     * 执行渲染任务
     */
    fun executeTask(task: MermaidRenderTask, callback: RenderCallback) {
        if (!isWorking.compareAndSet(false, true)) {
            callback.onRenderComplete(
                RenderResult.Error(
                    task.id,
                    "Worker $workerId is busy"
                )
            )
            return
        }
        
        this.currentTask = task
        this.callback = callback
        
        try {
            // WebView 必须在主线程操作
            mainHandler.post {
                val webView = webViewPool.acquire()
                if (webView == null) {
                    callback.onRenderComplete(
                        RenderResult.Error(
                            task.id,
                            "Failed to acquire WebView from pool"
                        )
                    )
                    isWorking.set(false)
                    return@post
                }
                renderMermaid(webView, task, callback)
            }
        } catch (e: Exception) {
            callback.onRenderComplete(
                RenderResult.Error(
                    task.id,
                    "Failed to execute task: ${e.message}",
                    e
                )
            )
            isWorking.set(false)
        }
    }
    
    /**
     * 使用 WebView 渲染 Mermaid 图表
     */
    private fun renderMermaid(
        webView: WebView,
        task: MermaidRenderTask,
        callback: RenderCallback
    ) {
        try {
            // 配置 WebView
            setupWebView(webView, task, callback)
            
            // 加载 HTML 模板
            val html = buildMermaidHtml(task)
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        } catch (e: Exception) {
            callback.onRenderComplete(
                RenderResult.Error(
                    task.id,
                    "Failed to render mermaid: ${e.message}",
                    e
                )
            )
            webViewPool.release(webView)
            isWorking.set(false)
        }
    }
    
    /**
     * 配置 WebView 设置
     */
    private fun setupWebView(
        webView: WebView,
        task: MermaidRenderTask,
        callback: RenderCallback
    ) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        
        // 添加 JavaScript 接口
        webView.addJavascriptInterface(
            RenderBridge(task, callback, webView),
            "AndroidBridge"
        )
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 页面加载完成后，开始渲染
                callback.onProgress(task.id, 50)
            }
        }
        
        webView.webChromeClient = WebChromeClient()
    }
    
    /**
     * 构建 Mermaid HTML 模板
     */
    private fun buildMermaidHtml(task: MermaidRenderTask): String {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            width: ${task.width}px;
            height: ${task.height}px;
            display: flex;
            justify-content: center;
            align-items: center;
            background: white;
            overflow: hidden;
        }
        #mermaid-container {
            width: 100%;
            height: 100%;
            display: flex;
            justify-content: center;
            align-items: center;
        }
    </style>
</head>
<body>
    <div id="mermaid-container">
        <div class="mermaid">
${task.mermaidCode}
        </div>
    </div>
    <script>
        mermaid.initialize({ 
            startOnLoad: true,
            theme: 'default',
            securityLevel: 'loose'
        });
        
        // 等待渲染完成
        setTimeout(() => {
            try {
                AndroidBridge.onProgress(75);
                captureImage();
            } catch (e) {
                AndroidBridge.onError('Render timeout: ' + e.message);
            }
        }, 2000);
        
        function captureImage() {
            try {
                // 通知 Android 端可以截图了
                AndroidBridge.onRenderComplete();
            } catch (e) {
                AndroidBridge.onError('Capture failed: ' + e.message);
            }
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
    
    /**
     * JavaScript 桥接对象
     */
    private inner class RenderBridge(
        private val task: MermaidRenderTask,
        private val callback: RenderCallback,
        private val webView: WebView
    ) {
        @JavascriptInterface
        fun onProgress(progress: Int) {
            mainHandler.post {
                callback.onProgress(task.id, progress)
            }
        }
        
        @JavascriptInterface
        fun onRenderComplete() {
            mainHandler.post {
                callback.onProgress(task.id, 90)
                // 截取 WebView 内容
                captureWebViewImage(webView, task, callback)
            }
        }
        
        @JavascriptInterface
        fun onError(error: String) {
            mainHandler.post {
                callback.onRenderComplete(
                    RenderResult.Error(
                        task.id,
                        error
                    )
                )
                webViewPool.release(webView)
                isWorking.set(false)
            }
        }
    }
    
    /**
     * 截取 WebView 内容为图片
     */
    private fun captureWebViewImage(
        webView: WebView,
        task: MermaidRenderTask,
        callback: RenderCallback
    ) {
        try {
            // 启用绘图缓存
            webView.isDrawingCacheEnabled = true
            webView.buildDrawingCache()
            
            // 创建 Bitmap
            val bitmap = Bitmap.createBitmap(
                task.width,
                task.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            webView.draw(canvas)
            
            // 转换为字节数组
            val outputStream = ByteArrayOutputStream()
            val compressFormat = when (task.format) {
                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                ImageFormat.JPG -> Bitmap.CompressFormat.JPEG
                ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP
            }
            
            bitmap.compress(compressFormat, task.quality, outputStream)
            val imageData = outputStream.toByteArray()
            
            // 清理资源
            webView.isDrawingCacheEnabled = false
            webView.destroyDrawingCache()
            bitmap.recycle()
            outputStream.close()
            
            // 返回结果
            callback.onProgress(task.id, 100)
            callback.onRenderComplete(
                RenderResult.Success(
                    task.id,
                    imageData,
                    task.format
                )
            )
            
        } catch (e: Exception) {
            callback.onRenderComplete(
                RenderResult.Error(
                    task.id,
                    "Failed to capture image: ${e.message}",
                    e
                )
            )
        } finally {
            webViewPool.release(webView)
            isWorking.set(false)
            this.currentTask = null
            this.callback = null
        }
    }
    
    /**
     * 检查 Worker 是否空闲
     */
    fun isIdle(): Boolean = !isWorking.get()
    
    /**
     * 获取当前任务
     */
    fun getCurrentTask(): MermaidRenderTask? = currentTask
}

