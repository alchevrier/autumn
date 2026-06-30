package dev.autumn.annotations

/**
 * Enforces a strict upper bound on the number of iterations allowed for a loop
 * executing inside a WCET-auditable @InjectTopology pipeline.
 *
 * If a loop does not have statically provable bounds (e.g. iterating over an SPSC ring burst),
 * and this annotation is missing, the autumn-compiler-plugin will throw a fatal error
 * during the WCET static analysis pass.
 */
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class MaxIterations(
    val limit: Int
)