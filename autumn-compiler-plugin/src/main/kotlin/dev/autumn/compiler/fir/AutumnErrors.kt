package dev.autumn.compiler.fir

import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error2
import org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory
import org.jetbrains.kotlin.psi.KtElement

object AutumnErrors {
    val HEAP_ALLOCATION_IN_STRICT_SCOPE by error0<KtElement>()
    val L1_CACHE_BUDGET_EXCEEDED by error2<KtElement, Int, Int>() // Required bytes, Budget bytes
    val COLD_CHANNEL_IN_HOT_PATH by error0<KtElement>()
    val CONFLICTING_CHANNEL_TYPES by error0<KtElement>()
    
    init {
        RootDiagnosticRendererFactory.registerFactory(AutumnDiagnosticRenderer)
    }
}
