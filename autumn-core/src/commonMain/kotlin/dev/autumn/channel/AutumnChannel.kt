package dev.autumn.channel

/**
 * The user-facing abstraction representing an isolated hardware-sympathetic queue.
 * 
 * At runtime, the Autumn Compiler Plugin inspects the type parameter [T] (which must be 
 * a `@Pipelined` interface) and dynamically injects the SoA (Structure of Arrays) 
 * backing fields directly alongside this channel's instantiation.
 * 
 * Usage:
 * ```kotlin
 * @ColdChannel
 * val auditLog = AutumnChannel<OrderEvent>(1024)
 * ```
 */
class AutumnChannel<T>(val capacity: Int) {
    
    /**
     * The purely temporal data wire that orchestrates index allocations 
     * between execution domains.
     */
    val buffer = Channel(capacity)

    // Note: The actual data payload (e.g. IntArray, LongArray) is explicitly NOT held 
    // here in standard Kotlin generics to avoid boxing. 
    // The Autumn K2 Compiler structurally generates the arrays at compile-time 
    // precisely where the AutumnChannel is hoisted.
}
