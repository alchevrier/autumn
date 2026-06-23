package dev.autumn.annotations

/**
 * Enforces a strict L1/L2 cache capacity budget (in bytes).
 * If the `@Pipelined` SoA data structures allocated to this core exceed this size,
 * the Autumn K2 compiler plugin will fail the build during Static Timing Analysis.
 * 
 * Example: @ThreadCacheBudget(32768) // 32KB typical L1d cache limit
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ThreadCacheBudget(val bytes: Int)
