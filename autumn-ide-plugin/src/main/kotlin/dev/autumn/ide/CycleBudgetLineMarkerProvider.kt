package dev.autumn.ide

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import com.intellij.util.PlatformIcons
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import com.intellij.openapi.project.Project
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem

class CycleBudgetLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is KtNamedFunction && element !is KtProperty) return null

        val functionName = (element as? KtNamedFunction)?.name ?: (element as? KtProperty)?.name ?: return null
        
        val isAutumnObserve = (element as? KtNamedFunction)?.annotationEntries?.any { 
            it.shortName?.asString() == "Observe" || it.shortName?.asString() == "CycleBudget" 
        } == true
        
        val isAutumnChannel = (element as? KtProperty)?.annotationEntries?.any {
            it.shortName?.asString() in listOf("BoundaryChannel", "ColdChannel", "RegisterChannel", "XdpGateway")
        } == true
        
        if (!isAutumnObserve && !isAutumnChannel) return null
        
        var cycleLabel = if (isAutumnChannel) "🍂 Autumn Channel Boundary" else "🍂 Autumn Cycle Budget Enforced"
        var navHandler: GutterIconNavigationHandler<PsiElement>? = null
        
        try {
            val project = element.project
            val rootDir = File(project.basePath ?: "")
            val allTopologyFiles = rootDir.walkTopDown()
                .filter { it.name == "topology.json" && it.absolutePath.contains("build/reports/autumn") }
                .toList()
                
            val allComps = mutableListOf<TopologyComponent>()
            for (file in allTopologyFiles) {
                val rawJson = file.readText()
                allComps.addAll(Json { ignoreUnknownKeys = true }.decodeFromString<List<TopologyComponent>>(rawJson))
            }
            
            val comp = allComps.find { it.name == functionName }
            if (comp != null) {
                if (comp.type == "Handler" && comp.cycles > 0) {
                    val pressureStr = if (comp.portPressure.isNotEmpty()) " | Warning: ${comp.portPressure}" else ""
                    cycleLabel = "🍂 ${comp.cycles} ALU Cycles Estimated$pressureStr"
                } else if (comp.type == "Channel" && comp.capacity > 0) {
                    cycleLabel = "🍂 Channel Queue (${comp.capacity} Events, ${comp.sharded}x Shards)"
                }
                
                // Add Navigation if it routes somewhere
                if (comp.target.isNotEmpty()) {
                    cycleLabel += " → Routes To: ${comp.target} (Click to follow dataflow)"
                    val targetComp = allComps.find { it.name == comp.target || com.intellij.openapi.util.text.StringUtil.containsIgnoreCase(it.target, comp.name) }
                    if (targetComp != null) {
                        navHandler = GutterIconNavigationHandler<PsiElement> { _, _ ->
                            val vFile = LocalFileSystem.getInstance().findFileByIoFile(File(targetComp.sourceFile))
                            if (vFile != null) {
                                OpenFileDescriptor(project, vFile, targetComp.sourceLine - 1, 0).navigate(true)
                            }
                        }
                    }
                } else if (comp.type == "Channel") {
                    // Try to find a handler that routes to this channel
                    val sourceComp = allComps.find { it.target == comp.name || it.target.split(",").contains(comp.name) }
                    if (sourceComp != null) {
                        cycleLabel += " ← Fed By: ${sourceComp.name} (Click to see Producer)"
                        navHandler = GutterIconNavigationHandler<PsiElement> { _, _ ->
                            val vFile = LocalFileSystem.getInstance().findFileByIoFile(File(sourceComp.sourceFile))
                            if (vFile != null) {
                                OpenFileDescriptor(project, vFile, sourceComp.sourceLine - 1, 0).navigate(true)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback
        }

        return LineMarkerInfo(
            (element as? KtNamedFunction)?.nameIdentifier ?: (element as? KtProperty)?.nameIdentifier ?: element,
            element.textRange,
            AutumnIcons.Leaf, 
            { cycleLabel }, 
            navHandler,
            GutterIconRenderer.Alignment.LEFT,
            { "Autumn Circuit Lens" }
        )
    }
}
