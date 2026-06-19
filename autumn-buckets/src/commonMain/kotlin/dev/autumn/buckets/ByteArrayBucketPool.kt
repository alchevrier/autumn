package dev.autumn.buckets

import dev.autumn.annotations.LongLived

/**
 * Base implementation of an Array-of-Structs (AoS) backed by a flat [ByteArray].
 * This avoids pointer chasing and object overhead on hot paths.
 */
@LongLived
abstract class ByteArrayBucketPool<T>(
    override val capacity: Int,
    protected val recordSizeInBytes: Int
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

    /**
     * Helper to read an Int directly from the byte array.
     */
    protected fun readInt(offset: Int): Int {
        return (buffer[offset].toInt() and 0xFF shl 24) or
               (buffer[offset + 1].toInt() and 0xFF shl 16) or
               (buffer[offset + 2].toInt() and 0xFF shl 8) or
               (buffer[offset + 3].toInt() and 0xFF)
    }

    /**
     * Helper to write an Int directly into the byte array.
     */
    protected fun writeInt(offset: Int, value: Int) {
        buffer[offset] = (value ushr 24).toByte()
        buffer[offset + 1] = (value ushr 16).toByte()
        buffer[offset + 2] = (value ushr 8).toByte()
        buffer[offset + 3] = value.toByte()
    }
}
