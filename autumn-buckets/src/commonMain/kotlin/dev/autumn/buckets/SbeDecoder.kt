package dev.autumn.buckets

/**
 * Base class for a Simple Binary Encoding (SBE) style flyweight decoder/encoder.
 * The instance wraps a single contiguous [buffer] and points to a specific [offset].
 * Offsets for actual fields are evaluated relative to this base offset.
 */
abstract class SbeDecoder {
    protected var buffer: ByteArray = EMPTY_BUFFER
    protected var offset: Int = 0

    /**
     * Shifts the flyweight cursor to point to a new location in a buffer.
     */
    fun wrap(newBuffer: ByteArray, newOffset: Int) {
        this.buffer = newBuffer
        this.offset = newOffset
    }

    /**
     * Reads an Int directly from the byte array at the current [offset] + [fieldOffset].
     */
    protected fun readInt(fieldOffset: Int): Int {
        val pos = offset + fieldOffset
        return (buffer[pos].toInt() and 0xFF shl 24) or
               (buffer[pos + 1].toInt() and 0xFF shl 16) or
               (buffer[pos + 2].toInt() and 0xFF shl 8) or
               (buffer[pos + 3].toInt() and 0xFF)
    }

    /**
     * Writes an Int directly to the byte array at the current [offset] + [fieldOffset].
     */
    protected fun writeInt(fieldOffset: Int, value: Int) {
        val pos = offset + fieldOffset
        buffer[pos] = (value ushr 24).toByte()
        buffer[pos + 1] = (value ushr 16).toByte()
        buffer[pos + 2] = (value ushr 8).toByte()
        buffer[pos + 3] = value.toByte()
    }

    companion object {
        val EMPTY_BUFFER = ByteArray(0)
    }
}
