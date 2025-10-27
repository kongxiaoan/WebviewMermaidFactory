# WebView Factory - Mermaid 图片生产工厂

一个基于 WebView 的 Mermaid 图表渲染引擎，采用对象池和 Worker 模式实现高效的图片生产。

## 功能特点

- ✅ **对象池管理**: 复用 WebView 实例，减少创建销毁开销
- ✅ **Worker 线程池**: 类似线程池的任务调度机制
- ✅ **打字机模式**: 支持流式任务提交，按顺序返回结果
- ✅ **批量处理**: 支持批量提交任务，自动队列管理
- ✅ **并发控制**: 自动管理并发数，防止资源耗尽
- ✅ **进度回调**: 实时反馈渲染进度
- ✅ **多格式支持**: 支持 PNG、JPG、WEBP 输出

## 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                    WebViewFactory                        │
│  ┌─────────────────────────────────────────────────┐   │
│  │           Task Queue (任务队列)                  │   │
│  └─────────────────────────────────────────────────┘   │
│                          │                               │
│                          ▼                               │
│  ┌──────────────────────────────────────────────────┐  │
│  │              Task Scheduler (调度器)              │  │
│  └──────────────────────────────────────────────────┘  │
│         │          │          │                         │
│         ▼          ▼          ▼                         │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐                  │
│  │ Worker1 │ │ Worker2 │ │ Worker3 │  (Worker线程池)  │
│  └─────────┘ └─────────┘ └─────────┘                  │
│         │          │          │                         │
│         └──────────┴──────────┘                         │
│                    │                                     │
│                    ▼                                     │
│  ┌──────────────────────────────────────────────────┐  │
│  │            WebView Pool (对象池)                  │  │
│  │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐    │  │
│  │  │WebView1│ │WebView2│ │WebView3│ │WebView4│    │  │
│  │  └────────┘ └────────┘ └────────┘ └────────┘    │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## 使用示例

### 1. 基本使用

```kotlin
// 获取工厂实例
val factory = WebViewFactory.getInstance(context)

// 提交单个任务
factory.submitTask(
    MermaidRenderTask(
        mermaidCode = """
            graph TD
                A[开始] --> B{是否成功?}
                B -->|是| C[继续]
                B -->|否| D[结束]
                C --> D
        """.trimIndent(),
        width = 1920,
        height = 1080,
        quality = 90,
        format = ImageFormat.PNG
    ),
    object : RenderCallback {
        override fun onRenderComplete(result: RenderResult) {
            when (result) {
                is RenderResult.Success -> {
                    // 保存图片
                    val file = File(context.filesDir, "output.png")
                    file.writeBytes(result.imageData)
                    Log.d("Render", "图片已保存: ${file.absolutePath}")
                }
                is RenderResult.Error -> {
                    Log.e("Render", "渲染失败: ${result.errorMessage}")
                }
            }
        }

        override fun onProgress(taskId: String, progress: Int) {
            Log.d("Render", "任务 $taskId 进度: $progress%")
        }
    }
)
```

### 2. 批量处理（按顺序返回结果）

```kotlin
val tasks = listOf(
    MermaidRenderTask(mermaidCode = "graph TD\n    A-->B"),
    MermaidRenderTask(mermaidCode = "graph LR\n    C-->D"),
    MermaidRenderTask(mermaidCode = "sequenceDiagram\n    Alice->>John: Hello")
)

factory.submitTasksBatch(tasks, object : RenderCallback {
    override fun onRenderComplete(result: RenderResult) {
        // 结果会按照提交顺序返回
        println("收到结果: ${result.taskId}")
    }
})
```

### 3. 流式提交（打字机模式）

```kotlin
// 创建流式提交器
val submitter = factory.createStreamSubmitter(object : RenderCallback {
    override fun onRenderComplete(result: RenderResult) {
        // 结果会按照提交顺序依次返回
        when (result) {
            is RenderResult.Success -> {
                println("第 ${submitter.getCompletedCount()} 张图片完成")
            }
            is RenderResult.Error -> {
                println("错误: ${result.errorMessage}")
            }
        }
    }
})

// 模拟打字机模式，逐个提交任务
mermaidCodeList.forEach { code ->
    submitter.submitNext(MermaidRenderTask(mermaidCode = code))
    Thread.sleep(100) // 模拟打字机间隔
}
```

### 4. 监控工厂状态

```kotlin
val stats = factory.getFactoryStats()
println(stats.toString())

// 输出:
// Factory Stats:
// - Workers: 3 (Idle: 2, Busy: 1)
// - Tasks: Queued=5, Active=8
// - WebView Pool: 2/3 available (max: 5)
```

### 5. 取消任务

```kotlin
val taskId = factory.submitTask(task, callback)

// 取消任务（仅能取消队列中等待的任务）
val cancelled = factory.cancelTask(taskId)
if (cancelled) {
    println("任务已取消")
}
```

### 6. 关闭工厂

```kotlin
// 在 Application 或 Activity 销毁时调用
factory.shutdown()
// 或者
WebViewFactory.destroyInstance()
```

## API 文档

### WebViewFactory

主工厂类，负责任务调度和资源管理。

#### 方法

- `getInstance(context: Context): WebViewFactory`

  - 获取单例实例

- `submitTask(task: MermaidRenderTask, callback: RenderCallback): String`

  - 提交单个任务，返回任务 ID

- `submitTasksBatch(tasks: List<MermaidRenderTask>, callback: RenderCallback)`

  - 批量提交任务，按顺序返回结果

- `createStreamSubmitter(callback: RenderCallback): StreamTaskSubmitter`

  - 创建流式任务提交器

- `cancelTask(taskId: String): Boolean`

  - 取消任务

- `getFactoryStats(): FactoryStats`

  - 获取工厂状态

- `shutdown()`
  - 关闭工厂

### MermaidRenderTask

渲染任务数据类。

```kotlin
data class MermaidRenderTask(
    val id: String = UUID.randomUUID().toString(),
    val mermaidCode: String,              // Mermaid 代码
    val width: Int = 1920,                // 宽度（像素）
    val height: Int = 1080,               // 高度（像素）
    val quality: Int = 90,                // 质量 (0-100)
    val format: ImageFormat = ImageFormat.PNG  // 输出格式
)
```

### RenderCallback

渲染回调接口。

```kotlin
interface RenderCallback {
    fun onRenderComplete(result: RenderResult)
    fun onProgress(taskId: String, progress: Int)
}
```

### RenderResult

渲染结果。

```kotlin
sealed class RenderResult {
    data class Success(
        val taskId: String,
        val imageData: ByteArray,
        val format: ImageFormat
    ) : RenderResult()

    data class Error(
        val taskId: String,
        val errorMessage: String,
        val exception: Throwable? = null
    ) : RenderResult()
}
```

## 性能优化建议

1. **合理配置 Worker 数量**: 默认 3 个，可根据设备性能调整

   ```kotlin
   WebViewFactory.getInstance(context, workerCount = 5, maxPoolSize = 8)
   ```

2. **控制图片尺寸**: 较大的图片会占用更多内存和渲染时间

3. **使用 WebP 格式**: 相比 PNG 和 JPG 有更好的压缩率

   ```kotlin
   MermaidRenderTask(
       mermaidCode = code,
       format = ImageFormat.WEBP,
       quality = 85
   )
   ```

4. **及时释放资源**: 在不需要时调用 `shutdown()`

## 注意事项

1. ⚠️ **需要网络权限**: 加载 Mermaid CDN 需要网络访问

   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   ```

2. ⚠️ **线程安全**: 所有公开方法都是线程安全的

3. ⚠️ **内存管理**: WebView 占用内存较大，注意监控内存使用

4. ⚠️ **生命周期**: 建议在 Application 中管理单例，避免内存泄漏

## Mermaid 语法支持

支持所有 Mermaid 图表类型：

- 流程图 (Flowchart)
- 序列图 (Sequence Diagram)
- 类图 (Class Diagram)
- 状态图 (State Diagram)
- 甘特图 (Gantt Chart)
- 饼图 (Pie Chart)
- Git 图 (Git Graph)
- 用户旅程图 (User Journey)
- ER 图 (Entity Relationship Diagram)

详细语法请参考: https://mermaid.js.org/

## 许可证

MIT License
