package dev.autumn.annotations

/**
 * Marks a function as a critical execution pathway.
 * 
 * In the Autumn architecture, the distinction is binary: everything not explicitly 
 * in the hot path is considered "cold". The primary directive of this annotation is isolation: 
 * cold path logic (metrics, persistence, allocations, etc.) must never impede the hot path 
 * via L1/L2 cache line eviction or CPU execution port contention.
 * 
 * The Cache-Affinity Scheduler routes execution through this pathway, saturating CPU 
 * execution ports by grouping invocations touching the same cache lines.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class HotPath
