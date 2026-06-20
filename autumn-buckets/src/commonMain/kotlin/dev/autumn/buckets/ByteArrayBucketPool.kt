package dev.autumn.buckets

import dev.autumn.annotations.LongLived

/**
 * Base implementation of an Array-of-Structs (AoS) backed by a flat [ByteArray],
 * mapped to a strictly evaluated Simple Binary Encoding (SBE) flyweight.
 */
abstract class ByteArrayBucketPool<T : SbeDecoder>(
    override val capacity: Int,
    protected val recordSizeInBytes: Int,
    private val flyweight: T
) : BucketPool<T> {

    // The backend array pre-allocated at startup for the lifetime of this pool.
    @LongLived
    protected val buffer = ByteArray(capacity * recordSizeInBytes)
    
    override var size: Int = 0
        protected set

    override fun clear() {
        size = 0
        // We do not zero out the array to save CPU cycles on the hot path.
        // Old data is logically inaccessible because size = 0.
    }

    override fun get(index: Int): T {
        if (index < 0 || index >= size) throw IndexOutOfBoundsException("Index $index out of bounds")
        flyweight.wrap(buffer, index * recordSizeInBytes)
        return flyweight
    }

    override fun append(): T {
        if (size >= capacity) throw IllegalStateException("Bucket capacity exceeded")
        flyweight.wrap(buffer, size * recordSizeInBytes)
        size++
        return flyweight
    }
}
