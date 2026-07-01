package dev.autumn.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import java.io.File
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import kotlin.concurrent.timer

@Serializable
data class TopologyComponent(
    val type: String,
    val name: String,
    val channelType: String = "",
    val capacity: Int = 0,
    val sharded: Int = 1,
    val shardKey: String = "",
    val cycles: Int = 0,
    val portPressure: String = "",
    val target: String = "",
    val sourceFile: String = "",
    val sourceLine: Int = 0,
    val jvmAssemblyHtml: String = "",
    val nativeAssemblyHtml: String = "",
    val wasmAssemblyHtml: String = ""
)

class AutumnPerformanceCenterToolWindow : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val composePanel = ComposePanel()
        
        composePanel.setContent {
            val autumnColors = darkColors(
                primary = Color(0xFFbb9457),
                primaryVariant = Color(0xFF99582a),
                secondary = Color(0xFFffe6a7),
                error = Color(0xFF6f1d1b),
                background = Color.Transparent,
                surface = Color(0xFF432818)
            )
            MaterialTheme(colors = autumnColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
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
    var selectedTarget by remember { mutableStateOf("JVM (Bytecode)") }
    
    DisposableEffect(project) {
        val timer = timer(period = 2000L) {
            val rootDir = File(project.basePath ?: "")
            // Find all topology.json files in submodules
            val allTopologyFiles = rootDir.walkTopDown()
                .filter { it.name == "topology.json" && it.absolutePath.contains("build/reports/autumn") }
                .toList()

            if (allTopologyFiles.isNotEmpty()) {
                try {
                    val allComponents = mutableListOf<TopologyComponent>()
                    for (file in allTopologyFiles) {
                        val rawJson = file.readText()
                        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<List<TopologyComponent>>(rawJson)
                        allComponents.addAll(decoded)
                    }
                    components = allComponents
                    lastUpdated = java.time.LocalTime.now().toString()
                } catch(e: Exception) {
                    lastUpdated = "Error parsing json: ${e.message}"
                }
            } else {
                lastUpdated = "Waiting for compilation (topology.json)..."
            }
        }
        onDispose { timer.cancel() }
    }
    
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Autumn Performance Center", style = MaterialTheme.typography.h6)
        Text("Last Synced: $lastUpdated", style = MaterialTheme.typography.caption)
        
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val availableTargets = listOf(
                "JVM (Bytecode)" to { c: TopologyComponent -> c.jvmAssemblyHtml.isNotBlank() },
                "Native (x86_64)" to { c: TopologyComponent -> c.nativeAssemblyHtml.isNotBlank() },
                "Web (Wasm)" to { c: TopologyComponent -> c.wasmAssemblyHtml.isNotBlank() }
            ).filter { (_, predicate) -> components.any(predicate) }.map { it.first }.ifEmpty { listOf("JVM (Bytecode)") }
            
            if (selectedTarget !in availableTargets && availableTargets.isNotEmpty()) {
                selectedTarget = availableTargets.first()
            }
            
            availableTargets.forEach { tgt ->
                Button(
                    onClick = { selectedTarget = tgt },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (selectedTarget == tgt) MaterialTheme.colors.primary else MaterialTheme.colors.surface
                    )
                ) {
                    Text(tgt)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        if (components.isEmpty()) {
            Text("No Topology Metrics Discovered.", color = Color.Gray)
        } else {
            val handlers = components.filter { it.type == "Handler" }
            val allChannels = components.filter { c -> 
                c.type == "Channel" && handlers.any { h -> h.target == c.name || h.name.lowercase().contains(c.name.lowercase()) }
            }
            val roots = components.filter { it.type == "TopologyRoot" }.filter { r ->
                val boundNames = r.target.split(",")
                allChannels.any { boundNames.contains(it.name) }
            }
            
            var selectedTabIndex by remember { mutableStateOf(0) }
            val tabs = if (roots.isNotEmpty()) roots else allChannels
            
            if (selectedTabIndex >= tabs.size && tabs.isNotEmpty()) selectedTabIndex = 0
            
            if (tabs.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = MaterialTheme.colors.primary,
                    edgePadding = 8.dp
                ) {
                    tabs.forEachIndexed { index, tabNode ->
                        val icon = if (tabNode.type == "TopologyRoot") "🍂" else "📦"
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text("$icon Execute: ${tabNode.name}", style = MaterialTheme.typography.subtitle2) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                val currentTab = tabs.getOrNull(selectedTabIndex)
                if (currentTab != null) {
                    val channels = if (currentTab.type == "TopologyRoot") {
                        val boundNames = currentTab.target.split(",")
                        allChannels.filter { boundNames.contains(it.name) }
                    } else {
                        listOf(currentTab)
                    }
                    
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (currentTab.type == "TopologyRoot") {
                            item {
                                ComponentCard(currentTab, selectedTarget, project)
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                        
                        if (channels.isNotEmpty()) {
                            item {
                                Text("Data Boundaries", style = MaterialTheme.typography.subtitle1, color = Color.LightGray)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            items(channels) { comp ->
                                ComponentCard(comp, selectedTarget, project)
                            }
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    Text("⬇ Dataflow ⬇", color = Color.Gray, style = MaterialTheme.typography.caption)
                                }
                            }
                        }
                        
                        val relatedHandlers = handlers.filter { h ->
                            channels.any { c -> h.target == c.name || h.name.lowercase().contains(c.name.lowercase()) } || channels.isEmpty()
                        }
                        
                        if (relatedHandlers.isNotEmpty()) {
                            item {
                                Text("Execution FSMs (Handlers)", style = MaterialTheme.typography.subtitle1, color = Color.LightGray)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            items(relatedHandlers) { comp ->
                                ComponentCard(comp, selectedTarget, project)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Text("Execution FSMs (Handlers)", style = MaterialTheme.typography.subtitle1, color = Color.LightGray)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(handlers) { comp ->
                        ComponentCard(comp, selectedTarget, project)
                    }
                }
            }
        }
    }
}

@Composable
fun ComponentCard(comp: TopologyComponent, selectedTarget: String, project: Project) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expanded = !expanded },
        backgroundColor = if (comp.portPressure == "HIGH") MaterialTheme.colors.error.copy(alpha=0.8f) else Color(0x22bb9457),
        elevation = 0.dp,
        border = BorderStroke(1.dp, Color(0xFF99582a))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val icon = if (comp.type == "Channel") "📦" else "🍂"
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$icon ${comp.type}: ${comp.name}", style = MaterialTheme.typography.subtitle1, color = Color(0xFFffe6a7))
                
                if (comp.sourceFile.isNotEmpty()) {
                    Text("🔗 Jump to Source", style = MaterialTheme.typography.caption, color = Color(0xFFbb9457), modifier = Modifier.clickable {
                        var targetFile = comp.sourceFile
                        if (selectedTarget.contains("Native")) {
                            targetFile = targetFile.replace("jvmMain", "linuxX64Main").replace("jsMain", "linuxX64Main")
                        } else if (selectedTarget.contains("Web")) {
                            targetFile = targetFile.replace("jvmMain", "jsMain").replace("linuxX64Main", "jsMain")
                        } else {
                            targetFile = targetFile.replace("linuxX64Main", "jvmMain").replace("jsMain", "jvmMain")
                        }
                        
                        var vFile = LocalFileSystem.getInstance().findFileByIoFile(File(targetFile))
                        if (vFile == null) vFile = LocalFileSystem.getInstance().findFileByIoFile(File(comp.sourceFile)) // fallback
                        
                        if (vFile != null) {
                            OpenFileDescriptor(project, vFile, comp.sourceLine, 0).navigate(true)
                        }
                    })
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (comp.cycles > 0) Text("Cycles: ${comp.cycles}", style = MaterialTheme.typography.body2)
                if (comp.portPressure.isNotEmpty()) Text("Pressure: ${comp.portPressure}", style = MaterialTheme.typography.body2)
                if (comp.capacity > 0) Text("Capacity: ${comp.capacity}", style = MaterialTheme.typography.body2)
                if (comp.sharded > 1) {
                    Text("Cores: ${comp.sharded}x", style = MaterialTheme.typography.body2, color = Color(0xFFE066FF))
                }
            }
            if (comp.target.isNotEmpty()) Text("Routes To: ${comp.target}", style = MaterialTheme.typography.body2, color = Color(0xFFbb9457))
            if (comp.shardKey.isNotEmpty()) Text("Partition Key: ${comp.shardKey}", style = MaterialTheme.typography.body2, color = Color(0xFFE066FF))
            
            if (expanded && comp.type == "Handler") {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Micro-Architectural Drill-Down", color = MaterialTheme.colors.primary, style = MaterialTheme.typography.subtitle2)
                Spacer(modifier = Modifier.height(4.dp))
                
                val opsString = when(selectedTarget) {
                    "JVM (Bytecode)" -> comp.jvmAssemblyHtml
                    "Native (x86_64)" -> comp.nativeAssemblyHtml
                    "Web (Wasm)" -> comp.wasmAssemblyHtml
                    else -> ""
                }
                
                if (opsString.isNotEmpty()) {
                    Column(modifier = Modifier.background(Color.Transparent).padding(8.dp).fillMaxWidth()) {
                        val lines = opsString.split(";").filter { it.isNotBlank() }
                        lines.forEach { line ->
                            val parts = line.split("|")
                            if (parts.size >= 3) {
                                val opName = parts[1]
                                val isBranch = opName.contains("IFEQ") || opName.contains("jmp") || opName.contains("b.eq") || opName.contains("br_if")
                                val rowColor = if (isBranch) Color(0xFF99582a) else Color(0xFFbb9457)
                                val branchPrefix = if (isBranch) "⑂ " else ""
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(parts[0], fontFamily = FontFamily.Monospace, color = Color.LightGray, modifier = Modifier.weight(1f))
                                    Text("$branchPrefix$opName", fontFamily = FontFamily.Monospace, color = rowColor, modifier = Modifier.weight(1f))
                                    Text("${parts[2]} cyc", fontFamily = FontFamily.Monospace, color = Color(0xFF6f1d1b))
                                }
                            }
                        }
                    }
                } else {
                    Text("No deep profiling data available.", color = Color.Gray)
                }
            }
        }
    }
}
