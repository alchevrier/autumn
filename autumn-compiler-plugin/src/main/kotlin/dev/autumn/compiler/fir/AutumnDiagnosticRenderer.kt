package dev.autumn.compiler.fir

import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.rendering.Renderers

object AutumnDiagnosticRenderer : BaseDiagnosticRendererFactory() {
    override val MAP = KtDiagnosticFactoryToRendererMap("Autumn").apply {
        put(
            AutumnErrors.HEAP_ALLOCATION_IN_STRICT_SCOPE,
            "Heap allocation detected in strict zero-allocation scope. Annotate with @LongLived if this is a safe long-lived allocation."
        )
        put(
            AutumnErrors.L1_CACHE_BUDGET_EXCEEDED,
            "L1 Cache Budget Exceeded! The @Pipelined data layout requires {0} bytes, which exceeds the @ThreadCacheBudget of {1} bytes.",
            Renderers.TO_STRING,
            Renderers.TO_STRING
        )
        put(
            AutumnErrors.COLD_CHANNEL_IN_HOT_PATH,
            "[Autumn] Illegal @ColdChannel usage inside a @HotPath. Cold operations cause L1 cache eviction and pipeline stalling. Offload this operation asynchronously."
        )
        put(
            AutumnErrors.CONFLICTING_CHANNEL_TYPES,
            "[Autumn] Architectural contradiction: A property cannot be both a @ColdChannel and a @RegisterChannel/@NetworkChannel."
        )
        put(
            AutumnErrors.INVALID_REGISTER_CHANNEL_CAPACITY,
            "[Autumn] @RegisterChannel capacity must be exactly a Power of Two (Found expression: {0})",
            Renderers.TO_STRING
        )
    }
}
