package com.tcm.webviewfactory

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tcm.lib.webview.factory.*
import com.tcm.webviewfactory.ui.theme.WebviewFactoryTheme
import java.io.File

/**
 * WebView Factory 演示 Activity
 * 展示如何使用 WebViewFactory 渲染 Mermaid 图表
 */
@ExperimentalMaterial3Api
class DemoActivity : ComponentActivity() {
    
    private val TAG = "DemoActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            WebviewFactoryTheme {
                DemoScreen()
            }
        }
    }
    
    @Composable
    fun DemoScreen() {
        var renderResults by remember { mutableStateOf<List<RenderResultItem>>(emptyList()) }
        var factoryStats by remember { mutableStateOf("") }
        var isProcessing by remember { mutableStateOf(false) }
        
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("WebView Factory Demo") }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            isProcessing = true
                            renderSingleTask { result ->
                                renderResults = renderResults + result
                                isProcessing = false
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        Text("单个任务")
                    }
                    
                    Button(
                        onClick = {
                            isProcessing = true
                            renderBatchTasks { results ->
                                renderResults = renderResults + results
                                isProcessing = false
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        Text("批量任务")
                    }
                    
                    Button(
                        onClick = {
                            isProcessing = true
                            renderStreamTasks { results ->
                                renderResults = results
                                if (results.size >= 5) {
                                    isProcessing = false
                                }
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        Text("流式任务")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 工厂状态按钮
                Button(
                    onClick = {
                        val factory = WebViewFactory.getInstance(this@DemoActivity)
                        val stats = factory.getFactoryStats()
                        factoryStats = stats.toString()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("查看工厂状态")
                }
                
                // 显示工厂状态
                if (factoryStats.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = factoryStats,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 结果列表
                Text(
                    text = "渲染结果 (${renderResults.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(renderResults) { item ->
                        ResultCard(item)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
    
    @Composable
    fun ResultCard(item: RenderResultItem) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (item.isSuccess) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = if (item.isSuccess) "✅ 成功" else "❌ 失败",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "任务ID: ${item.taskId}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    
    /**
     * 渲染单个任务
     */
    private fun renderSingleTask(onComplete: (RenderResultItem) -> Unit) {
        val factory = WebViewFactory.getInstance(this)
        
        val task = MermaidRenderTask(
            mermaidCode = """
                graph TD
                    A[开始] --> B{判断}
                    B -->|是| C[处理]
                    B -->|否| D[结束]
                    C --> D
            """.trimIndent(),
            width = 1920,
            height = 1080
        )
        
        factory.submitTask(task, object : RenderCallback {
            override fun onRenderComplete(result: RenderResult) {
                when (result) {
                    is RenderResult.Success -> {
                        val file = File(filesDir, "single_${System.currentTimeMillis()}.png")
                        file.writeBytes(result.imageData)
                        
                        onComplete(RenderResultItem(
                            taskId = result.taskId,
                            isSuccess = true,
                            message = "已保存: ${file.name} (${result.imageData.size / 1024}KB)"
                        ))
                        
                        Log.d(TAG, "✅ 单个任务完成: ${file.absolutePath}")
                    }
                    is RenderResult.Error -> {
                        onComplete(RenderResultItem(
                            taskId = result.taskId,
                            isSuccess = false,
                            message = "错误: ${result.errorMessage}"
                        ))
                        
                        Log.e(TAG, "❌ 单个任务失败", result.exception)
                    }
                }
            }
            
            override fun onProgress(taskId: String, progress: Int) {
                Log.d(TAG, "进度: $progress%")
            }
        })
    }
    
    /**
     * 渲染批量任务
     */
    private fun renderBatchTasks(onComplete: (List<RenderResultItem>) -> Unit) {
        val factory = WebViewFactory.getInstance(this)
        val results = mutableListOf<RenderResultItem>()
        
        val tasks = listOf(
            MermaidRenderTask(mermaidCode = "graph TD\n    A-->B"),
            MermaidRenderTask(mermaidCode = "graph LR\n    C-->D"),
            MermaidRenderTask(mermaidCode = "graph TD\n    E-->F")
        )
        
        factory.submitTasksBatch(tasks, object : RenderCallback {
            override fun onRenderComplete(result: RenderResult) {
                when (result) {
                    is RenderResult.Success -> {
                        val file = File(filesDir, "batch_${results.size + 1}.png")
                        file.writeBytes(result.imageData)
                        
                        results.add(RenderResultItem(
                            taskId = result.taskId,
                            isSuccess = true,
                            message = "批量 ${results.size + 1}/${tasks.size}: ${file.name}"
                        ))
                        
                        Log.d(TAG, "✅ 批量任务 ${results.size}/${tasks.size} 完成")
                    }
                    is RenderResult.Error -> {
                        results.add(RenderResultItem(
                            taskId = result.taskId,
                            isSuccess = false,
                            message = "批量任务失败: ${result.errorMessage}"
                        ))
                    }
                }
                
                if (results.size == tasks.size) {
                    onComplete(results)
                }
            }
        })
    }
    
    /**
     * 流式任务渲染（打字机模式）
     */
    private fun renderStreamTasks(onUpdate: (List<RenderResultItem>) -> Unit) {
        val factory = WebViewFactory.getInstance(this)
        val results = mutableListOf<RenderResultItem>()
        
        val codes = listOf(
            "graph TD\n    S1-->S2",
            "graph TD\n    S3-->S4",
            "graph TD\n    S5-->S6",
            "graph TD\n    S7-->S8",
            "graph TD\n    S9-->S10"
        )
        
        val submitter = factory.createStreamSubmitter(object : RenderCallback {
            override fun onRenderComplete(result: RenderResult) {
                when (result) {
                    is RenderResult.Success -> {
                        val file = File(filesDir, "stream_${results.size + 1}.png")
                        file.writeBytes(result.imageData)
                        
                        results.add(RenderResultItem(
                            taskId = result.taskId,
                            isSuccess = true,
                            message = "流式 ${results.size + 1}: ${file.name}"
                        ))
                        
                        Log.d(TAG, "⌨️ 流式任务 ${results.size} 完成")
                    }
                    is RenderResult.Error -> {
                        results.add(RenderResultItem(
                            taskId = result.taskId,
                            isSuccess = false,
                            message = "流式失败: ${result.errorMessage}"
                        ))
                    }
                }
                
                onUpdate(results.toList())
            }
        })
        
        // 模拟打字机效果
        Thread {
            codes.forEach { code ->
                submitter.submitNext(MermaidRenderTask(mermaidCode = code))
                Thread.sleep(200)
            }
        }.start()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 注意：在真实应用中，应该在 Application 中管理 Factory 的生命周期
        // 这里仅作演示
    }
    
    /**
     * 渲染结果项
     */
    data class RenderResultItem(
        val taskId: String,
        val isSuccess: Boolean,
        val message: String
    )
}

