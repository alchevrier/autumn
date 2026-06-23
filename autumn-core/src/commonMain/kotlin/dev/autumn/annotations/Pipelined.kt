package dev.autumn.annotations

/**
 * Instructs the Autumn K2 compiler plugin to erase this class/interface at compile time
 * and translate it into a flat Structure of Arrays (SoA).
 * This layout is backed by primitive arrays (e.g., ByteArray) for cache-aligned SIMD
 * pipelining and zero-deserialization hardware mappings.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Pipelined(val capacity: Int)
