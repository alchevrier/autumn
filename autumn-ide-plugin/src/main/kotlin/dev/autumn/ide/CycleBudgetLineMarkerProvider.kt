package dev.autumn.ide

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import com.intellij.util.PlatformIcons
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import com.intellij.openapi.project.Project

class CycleBudgetLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Only target Kotlin functions
        if (element !is KtNamedFunction) return null

        // In a real implementation we would parse FIR, resolve the `@CycleBudget` annotation, 
        // walk the IR/FIR and perform the ILP and cycle accumulation Math here.
        // For now, we stub out the detection when a function has a name implying observability.
        
        val functionName = element.name ?: return null
        val docString = element.docComment?.text ?: ""
        
        // Very basic stub to light up `@CycleBudget` handlers visually
        val isAutumnObserve = element.annotationEntries.any { it.shortName?.asString() == "Observe" || it.shortName?.asString() == "CycleBudget" }
        
        if (!isAutumnObserve && !docString.contains("@CycleBudget")) {
            return null
        }
        
        // Attempt to parse actual compiler output for this element
        var cycleLabel = "🍂 Autumn Cycle Budget Enforced"
        try {
            val project = element.project
            val rootDir = File(project.basePath ?: "")
            val allTopologyFiles = rootDir.walkTopDown()
                .filter { it.name == "topology.json" && it.absolutePath.contains("build/reports/autumn") }
                .toList()
                
            for (file in allTopologyFiles) {
                val rawJson = file.readText()
                val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<List<TopologyComponent>>(rawJson)
                val comp = decoded.find { it.name == functionName && it.type == "Handler" }
                if (comp != null && comp.cycles > 0) {
                    val pressureStr = if (comp.portPressure.isNotEmpty()) " | Port Pressure: ${comp.portPressure}" else ""
                    cycleLabel = "🍂 ${comp.cycles} ALU Cycles Estimated$pressureStr"
                    break
                }
            }
        } catch (e: Exception) {
            // Fallback to static text if plugin JSON is unparseable or outdated
        }

        // Return a dynamic line marker 🍂
        return LineMarkerInfo(
            element.nameIdentifier ?: element,
            element.textRange,
            PlatformIcons.PROPERTY_ICON, // Placeholder for a custom leaf icon (Requires SVG in resources)
            { cycleLabel }, // Tooltip text on hover
            null,
            GutterIconRenderer.Alignment.LEFT,
            { "Autumn Circuit Lens" }
        )
    }
}
