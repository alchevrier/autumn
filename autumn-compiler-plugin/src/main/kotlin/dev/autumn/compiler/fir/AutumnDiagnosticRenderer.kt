package dev.autumn.compiler.fir

import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap

object AutumnDiagnosticRenderer : BaseDiagnosticRendererFactory() {
    override val MAP = KtDiagnosticFactoryToRendererMap("Autumn").apply {
        put(
            AutumnErrors.HEAP_ALLOCATION_IN_STRICT_SCOPE,
            "Heap allocation detected in strict zero-allocation scope. Annotate with @LongLived if this is a safe long-lived allocation."
        )
    }
}
