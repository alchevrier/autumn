package dev.autumn.channel

import kotlin.concurrent.AtomicLong

/**
 * Native implementation of HardwareSequence using AtomicLong.
 * In Kotlin/Native, AtomicLong guarantees exact CPU memory fence semantics 
 * structurally identical to acquiring/releasing hardware sequences.
 */
actual class HardwareSequence actual constructor(initial: Long) {
    @PublishedApi
    internal val sequence = AtomicLong(initial)

    actual inline fun setRelease(value: Long) {
        sequence.value = value
    }

    actual inline fun getAcquire(): Long {
        return sequence.value
    }
}