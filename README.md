# WebView Factory - Mermaid 图片生产工厂

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-24%2B-green.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

一个高性能的 Android WebView 图片生产工厂，专为 Mermaid 图表渲染设计。采用对象池和 Worker 线程池架构，支持并发渲染、流式处理和批量生产。

## ✨ 核心特性

- 🏊 **对象池管理** - 智能复用 WebView 实例，减少创建销毁开销
- ⚡ **Worker 线程池** - 类似线程池的任务调度，高效并发处理
- ⌨️ **打字机模式** - 支持流式任务提交，按顺序返回结果
- 📦 **批量处理** - 一次提交多个任务，自动队列管理
- 🎯 **精准控制** - 可配置并发数、池大小、图片质量
- 📊 **实时监控** - 查看工厂状态、队列情况、池使用率
- 🖼️ **多格式支持** - PNG、JPG、WebP 输出格式

## 🏗️ 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                   WebViewFactory                         │
│                   (主工厂控制器)                          │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  ┌─────────────────────────────────────────────────┐   │
│  │           Task Queue (任务队列)                  │   │
│  │         [Task1] [Task2] [Task3] ...             │   │
│  └─────────────────────────────────────────────────┘   │
│                          ↓                               │
│  ┌──────────────────────────────────────────────────┐  │
│  │          Task Scheduler (智能调度器)              │  │
│  └──────────────────────────────────────────────────┘  │
│         │              │              │                  │
│         ↓              ↓              ↓                  │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐           │
│  │ Worker 1 │   │ Worker 2 │   │ Worker 3 │           │
│  └──────────┘   └──────────┘   └──────────┘           │
│         │              │              │                  │
│         └──────────────┴──────────────┘                 │
│                        ↓                                 │
│  ┌──────────────────────────────────────────────────┐  │
│  │           WebView Pool (对象池)                   │  │
│  │  ╔═══════╗ ╔═══════╗ ╔═══════╗ ╔═══════╗       │  │
│  │  ║ View1 ║ ║ View2 ║ ║ View3 ║ ║ View4 ║       │  │
│  │  ╚═══════╝ ╚═══════╝ ╚═══════╝ ╚═══════╝       │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 工作原理

1. **任务提交**: 外部调用者提交 Mermaid 渲染任务到工厂
2. **智能调度**: 调度器将任务分配给空闲的 Worker
3. **对象获取**: Worker 从对象池获取可用的 WebView
4. **并发渲染**: 多个 Worker 并行渲染不同的任务
5. **结果返回**: 渲染完成后按顺序返回结果（支持打字机模式）
6. **对象回收**: WebView 清理后返回对象池供下次使用

## 🚀 快速开始

### 1. 添加依赖

在 `app/build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation(project(":lib-webview_factory"))
}
```

### 2. 添加权限

在 `AndroidManifest.xml` 中添加：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 3. 基本使用

```kotlin
// 获取工厂实例
val factory = WebViewFactory.getInstance(context)

// 创建渲染任务
val task = MermaidRenderTask(
    mermaidCode = """
        graph TD
            A[开始] --> B{条件}
            B -->|是| C[处理]
            B -->|否| D[结束]
    """.trimIndent(),
    width = 1920,
    height = 1080,
    quality = 90,
    format = ImageFormat.PNG
)

// 提交任务
factory.submitTask(task, object : RenderCallback {
    override fun onRenderComplete(result: RenderResult) {
        when (result) {
            is RenderResult.Success -> {
                // 保存图片
                val file = File(context.filesDir, "output.png")
                file.writeBytes(result.imageData)
            }
            is RenderResult.Error -> {
                Log.e("Render", "失败: ${result.errorMessage}")
            }
        }
    }
})
```

## 📖 使用场景

### 场景 1: 单个图表渲染

适用于实时生成单个图表的场景。

```kotlin
val factory = WebViewFactory.getInstance(context)
factory.submitTask(task, callback)
```

### 场景 2: 批量图表生成

适用于一次性生成多个图表，比如报告生成、文档导出等。

```kotlin
val tasks = listOf(
    MermaidRenderTask(mermaidCode = "graph TD\n    A-->B"),
    MermaidRenderTask(mermaidCode = "graph LR\n    C-->D"),
    MermaidRenderTask(mermaidCode = "sequenceDiagram\n    A->>B: Hello")
)

factory.submitTasksBatch(tasks, callback)  // 按顺序返回结果
```

### 场景 3: 流式生成（打字机模式）

适用于动态接收数据并实时渲染的场景，比如接收服务器推送的数据流。

```kotlin
val submitter = factory.createStreamSubmitter(callback)

// 模拟打字机效果，数据逐个到达
serverDataFlow.collect { mermaidCode ->
    submitter.submitNext(MermaidRenderTask(mermaidCode = mermaidCode))
}
```

## 🎛️ 高级配置

### 自定义 Worker 和池大小

```kotlin
val factory = WebViewFactory.getInstance(
    context = context,
    workerCount = 5,      // 5个并发 Worker
    maxPoolSize = 8       // 最多8个 WebView 实例
)
```

### 自定义图片参数

```kotlin
val task = MermaidRenderTask(
    mermaidCode = code,
    width = 3840,              // 4K 宽度
    height = 2160,             // 4K 高度
    quality = 95,              // 高质量
    format = ImageFormat.WEBP  // WebP 格式（更小）
)
```

### 监控工厂状态

```kotlin
val stats = factory.getFactoryStats()
println("""
    Worker: ${stats.busyWorkerCount}/${stats.workerCount} 使用中
    队列: ${stats.queuedTaskCount} 个任务等待
    WebView池: ${stats.poolStats.availableSize}/${stats.poolStats.totalSize} 可用
""")
```

## 📊 性能特性

- **内存优化**: WebView 对象池避免频繁创建销毁
- **并发处理**: 多个 Worker 并行渲染，充分利用 CPU
- **队列管理**: 自动排队，防止资源耗尽
- **智能调度**: 任务按序分配，保证公平性
- **资源控制**: 可配置并发数和池大小

## 🔍 API 文档

详细的 API 文档请查看：[lib-webview_factory/README.md](lib-webview_factory/src/main/java/com/tcm/lib/webview/factory/README.md)

### 核心类

| 类名                  | 说明                             |
| --------------------- | -------------------------------- |
| `WebViewFactory`      | 主工厂类，负责任务调度和资源管理 |
| `WebViewPool`         | WebView 对象池                   |
| `WebViewWorker`       | 工作线程，执行具体渲染任务       |
| `MermaidRenderTask`   | 渲染任务数据类                   |
| `RenderCallback`      | 渲染回调接口                     |
| `RenderResult`        | 渲染结果（Success/Error）        |
| `StreamTaskSubmitter` | 流式任务提交器                   |

## 📱 示例应用

项目包含一个完整的示例应用，展示了所有使用场景：

- `app/src/main/java/com/tcm/webviewfactory/DemoActivity.kt` - Compose UI 演示
- `lib-webview_factory/src/main/java/com/tcm/lib/webview/factory/example/ExampleUsage.kt` - 代码示例

运行示例：

```bash
./gradlew :app:installDebug
```

## 🌐 支持的 Mermaid 图表类型

- ✅ 流程图 (Flowchart)
- ✅ 序列图 (Sequence Diagram)
- ✅ 类图 (Class Diagram)
- ✅ 状态图 (State Diagram)
- ✅ 甘特图 (Gantt Chart)
- ✅ 饼图 (Pie Chart)
- ✅ Git 图 (Git Graph)
- ✅ 用户旅程图 (User Journey)
- ✅ ER 图 (Entity Relationship Diagram)

Mermaid 语法参考：https://mermaid.js.org/

## ⚙️ 技术栈

- **语言**: Kotlin 2.0.21
- **最低 SDK**: Android 24 (Android 7.0)
- **编译 SDK**: Android 36
- **核心技术**:
  - Android WebView
  - Mermaid.js
  - Kotlin Coroutines
  - Handler / HandlerThread
  - 对象池模式
  - Worker 模式

## 📝 注意事项

1. ⚠️ **需要网络权限**: 加载 Mermaid CDN 需要网络访问
2. ⚠️ **内存占用**: WebView 占用内存较大，建议监控内存使用
3. ⚠️ **生命周期管理**: 建议在 Application 中管理 Factory 单例
4. ⚠️ **线程安全**: 所有公开方法都是线程安全的
5. ⚠️ **及时释放**: 在不需要时调用 `factory.shutdown()` 释放资源

## 🛠️ 构建项目

```bash
# 克隆项目
git clone <repository-url>

# 构建库
./gradlew :lib-webview_factory:build

# 运行示例
./gradlew :app:installDebug

# 运行测试
./gradlew test
```

## 📄 项目结构

```
WebviewFactory/
├── app/                              # 示例应用
│   └── src/main/java/com/tcm/webviewfactory/
│       ├── MainActivity.kt           # 主界面
│       └── DemoActivity.kt          # 演示界面
├── lib-webview_factory/             # 核心库
│   └── src/main/java/com/tcm/lib/webview/factory/
│       ├── WebViewFactory.kt        # 主工厂类
│       ├── WebViewPool.kt           # 对象池
│       ├── WebViewWorker.kt         # Worker
│       ├── MermaidRenderTask.kt     # 数据类
│       ├── README.md                # 详细文档
│       └── example/
│           └── ExampleUsage.kt      # 使用示例
└── README.md                        # 项目总览（本文件）
```

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📜 许可证

MIT License

---

**作者**: kongpingan  
**项目**: WebviewFactory  
**创建日期**: 2025-10-27
