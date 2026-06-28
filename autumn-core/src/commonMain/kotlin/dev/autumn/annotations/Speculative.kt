package dev.autumn.annotations

/**
 * Indicates that the Compiler should generate a Speculative Clock Burst-Router
 * around this property. The `burstWindow` represents the maximum number of 
 * contiguous bounds the Core is allowed to process before it MUST yield back 
 * to the `HardwareOscillator` to check the actual physical constraints.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class Speculative(val burstWindow: Int = 200)
