package com.tcm.lib.webview.factory

import java.util.UUID

/**
 * Mermaid 渲染任务
 * @param id 任务唯一标识
 * @param mermaidCode Mermaid 图表代码
 * @param width 输出图片宽度（像素）
 * @param height 输出图片高度（像素）
 * @param quality 图片质量 (0-100)
 * @param format 输出格式 (png, jpg, webp)
 */
data class MermaidRenderTask(
    val id: String = UUID.randomUUID().toString(),
    val mermaidCode: String,
    val width: Int = 1920,
    val height: Int = 1080,
    val quality: Int = 90,
    val format: ImageFormat = ImageFormat.PNG
)

/**
 * 渲染结果
 */
sealed class RenderResult {
    data class Success(
        val taskId: String,
        val imageData: ByteArray,
        val format: ImageFormat
    ) : RenderResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Success

            if (taskId != other.taskId) return false
            if (!imageData.contentEquals(other.imageData)) return false
            if (format != other.format) return false

            return true
        }

        override fun hashCode(): Int {
            var result = taskId.hashCode()
            result = 31 * result + imageData.contentHashCode()
            result = 31 * result + format.hashCode()
            return result
        }
    }
    
    data class Error(
        val taskId: String,
        val errorMessage: String,
        val exception: Throwable? = null
    ) : RenderResult()
}

/**
 * 图片格式
 */
enum class ImageFormat(val mimeType: String, val extension: String) {
    PNG("image/png", "png"),
    JPG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp")
}

/**
 * 渲染回调接口
 */
interface RenderCallback {
    /**
     * 渲染完成回调
     * @param result 渲染结果
     */
    fun onRenderComplete(result: RenderResult)
    
    /**
     * 渲染进度回调（可选）
     * @param taskId 任务ID
     * @param progress 进度 0-100
     */
    fun onProgress(taskId: String, progress: Int) {}
}

