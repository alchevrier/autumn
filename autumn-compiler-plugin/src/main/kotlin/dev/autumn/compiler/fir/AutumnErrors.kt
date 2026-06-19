package dev.autumn.compiler.fir

import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory
import org.jetbrains.kotlin.psi.KtElement

object AutumnErrors {
    val HEAP_ALLOCATION_IN_STRICT_SCOPE by error0<KtElement>()
    
    init {
        RootDiagnosticRendererFactory.registerFactory(AutumnDiagnosticRenderer)
    }
}
