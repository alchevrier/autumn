package dev.autumn.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.concurrent.timer

@Serializable
data class TopologyComponent(
    val type: String,
    val name: String,
    val capacity: Int,
    val cycles: Int,
    val portPressure: String,
    val target: String
)

class AutumnPerformanceCenterToolWindow : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val composePanel = ComposePanel()
        
        composePanel.setContent {
            MaterialTheme(colors = darkColors()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TopologyDashboard(project)
                }
            }
        }
        
        val content = toolWindow.contentManager.factory.createContent(composePanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

@Composable
fun TopologyDashboard(project: Project) {
    var components by remember { mutableStateOf(emptyList<TopologyComponent>()) }
    var lastUpdated by remember { mutableStateOf("") }
    
    // Simple polling watcher pointing into the active project directory
    DisposableEffect(project) {
        val topologyFile = File(project.basePath, "build/reports/autumn/topology.json")
        val timer = timer(period = 2000L) {
            if (topologyFile.exists()) {
                try {
                    val rawJson = topologyFile.readText()
                    val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<List<TopologyComponent>>(rawJson)
                    components = decoded
                    lastUpdated = java.time.LocalTime.now().toString()
                } catch(e: Exception) {
                    lastUpdated = "Error parsing json: ${e.message}"
                }
            } else {
                lastUpdated = "File not found: ${topologyFile.absolutePath}"
            }
        }
        onDispose { timer.cancel() }
    }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Autumn Topology Metrics", style = MaterialTheme.typography.h6)
        Text("Last Synced: $lastUpdated", style = MaterialTheme.typography.caption)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (components.isEmpty()) {
            Text("No Topology Metrics Discovered. Waiting for K2 Compiler Output...")
        } else {
            components.forEach { comp ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    backgroundColor = if (comp.portPressure == "HIGH") MaterialTheme.colors.error else MaterialTheme.colors.surface,
                    elevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("🧩 ${comp.type}: ${comp.name}", style = MaterialTheme.typography.subtitle1)
                        Text("⚡ Cycles: ${comp.cycles}", style = MaterialTheme.typography.body2)
                        if (comp.portPressure.isNotEmpty()) Text("⚠️ Pressure: ${comp.portPressure}", style = MaterialTheme.typography.body2)
                        if (comp.target.isNotEmpty()) Text("🎯 Routes To: ${comp.target}", style = MaterialTheme.typography.body2)
                        if (comp.capacity > 0) Text("📦 Ring Size: ${comp.capacity}", style = MaterialTheme.typography.body2)
                    }
                }
            }
        }
    }
}
