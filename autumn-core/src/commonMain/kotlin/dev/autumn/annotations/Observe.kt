package dev.autumn.annotations

/**
 * Weaves native zero-allocation telemetry instructions (e.g. `rdtsc`)
 * around the target FSM boundary or handler.
 * 
 * The compiler will automatically intercept this logic to calculate execution latency
 * and write it seamlessly into a non-blocking `AutumnMemoryBank` ring-buffer
 * reserved exclusively for exact point-in-time percentile math (e.g., P99, P99.9).
 * 
 * @param eventWindow How many occurrences to retain in the ring buffer before rolling over. Default 1 Million.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Observe(
    val eventWindow: Int = 1_000_000
)
