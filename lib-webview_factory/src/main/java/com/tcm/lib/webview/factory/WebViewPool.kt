package com.tcm.lib.webview.factory

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * WebView 对象池
 * 管理 WebView 实例的创建、复用和销毁
 */
class WebViewPool(
    private val context: Context,
    private val minPoolSize: Int = 2,
    private val maxPoolSize: Int = 5
) {
    private val availableWebViews = ConcurrentLinkedQueue<WebView>()
    private val usedWebViews = ConcurrentLinkedQueue<WebView>()
    private val currentPoolSize = AtomicInteger(0)
    private val lock = ReentrantLock()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    @Volatile
    private var isShutdown = false

    init {
        // 预创建最小数量的 WebView
        mainHandler.post {
            repeat(minPoolSize) {
                createWebView()
            }
        }
    }

    /**
     * 获取一个可用的 WebView
     */
    fun acquire(): WebView? {
        if (isShutdown) {
            return null
        }

        return lock.withLock {
            // 先尝试从可用队列获取
            val webView = availableWebViews.poll()
            if (webView != null) {
                usedWebViews.offer(webView)
                return webView
            }

            // 如果池子没满，创建新的 WebView
            if (currentPoolSize.get() < maxPoolSize) {
                val newWebView = createWebView()
                if (newWebView != null) {
                    usedWebViews.offer(newWebView)
                    return newWebView
                }
            }

            // 池子已满，等待释放
            null
        }
    }

    /**
     * 释放 WebView 回到池中
     */
    fun release(webView: WebView) {
        lock.withLock {
            if (usedWebViews.remove(webView)) {
                // 清理 WebView 状态
                mainHandler.post {
                    cleanWebView(webView)
                }
                availableWebViews.offer(webView)
            }
        }
    }

    /**
     * 创建新的 WebView
     */
    private fun createWebView(): WebView? {
        try {
            val webView = WebView(context.applicationContext)
            currentPoolSize.incrementAndGet()
            availableWebViews.offer(webView)
            return webView
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 清理 WebView 状态
     */
    private fun cleanWebView(webView: WebView) {
        try {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.clearCache(true)
            webView.clearFormData()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取池子当前状态
     */
    fun getPoolStats(): PoolStats {
        return PoolStats(
            totalSize = currentPoolSize.get(),
            availableSize = availableWebViews.size,
            usedSize = usedWebViews.size,
            maxSize = maxPoolSize
        )
    }

    /**
     * 关闭池子，销毁所有 WebView
     */
    fun shutdown() {
        isShutdown = true
        lock.withLock {
            mainHandler.post {
                // 销毁所有 WebView
                while (availableWebViews.isNotEmpty()) {
                    val webView = availableWebViews.poll()
                    webView?.destroy()
                }
                while (usedWebViews.isNotEmpty()) {
                    val webView = usedWebViews.poll()
                    webView?.destroy()
                }
                currentPoolSize.set(0)
            }
        }
    }

    /**
     * 池子统计信息
     */
    data class PoolStats(
        val totalSize: Int,
        val availableSize: Int,
        val usedSize: Int,
        val maxSize: Int
    )
}

