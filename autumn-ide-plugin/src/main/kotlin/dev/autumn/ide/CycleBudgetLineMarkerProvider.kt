package dev.autumn.ide

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import com.intellij.util.PlatformIcons

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

        // Return a mock line marker 🍂
        return LineMarkerInfo(
            element.nameIdentifier ?: element,
            element.textRange,
            PlatformIcons.PROPERTY_ICON, // Placeholder for a custom leaf icon
            { "🍂 37 / 60 Cycles | Port 1 Pressure" }, // Tooltip text on hover
            null,
            GutterIconRenderer.Alignment.LEFT,
            { "Autumn Circuit Lens" }
        )
    }
}
