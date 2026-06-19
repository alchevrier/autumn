package dev.autumn.annotations

/**
 * Instructs the compiler plugin to enforce a strict upper bound on 
 * concurrent network requests for a given system partition.
 * 
 * By defining this at compile-time, Autumn statically allocates the exact 
 * number of network tracking slots. Any request exceeding this limit is 
 * instantly rejected (Circuit Breaker), explicitly preventing network avalanches, 
 * OOMs, and runaway concurrency loops.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class NetworkConcurrencyBudget(
    val maxInFlightRequests: Int
)
