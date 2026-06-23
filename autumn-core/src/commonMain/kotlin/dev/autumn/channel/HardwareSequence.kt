package dev.autumn.channel

/**
 * A multiplatform abstraction for atomic sequence tracking that avoids 
 * full cache-flushing memory barriers by utilizing Acquire/Release semantics.
 * 
 * Unlike `@Volatile` which forces Sequentially Consistent (StoreLoad) barriers,
 * this allows the CPU to pipeline writes and only flushes what is necessary 
 * to guarantee visibility to the reading core.
 */
expect class HardwareSequence(initial: Long = 0L) {
    fun getAcquire(): Long
    fun setRelease(value: Long)
}
