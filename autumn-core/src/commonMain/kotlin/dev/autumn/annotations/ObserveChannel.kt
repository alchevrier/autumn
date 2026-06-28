package dev.autumn.annotations

/**
 * Denotes a `LatencyHistogram` structure inside the codebase that 
 * acts as a Cold Observatory memory bank tracking execution latency.
 * 
 * @param observerName The string identifier linking this memory bank to the `@Observe` function boundary.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class ObserveChannel(
    val observerName: String = "metricsHistogram",
    val capacity: Int = 1_000_000
)