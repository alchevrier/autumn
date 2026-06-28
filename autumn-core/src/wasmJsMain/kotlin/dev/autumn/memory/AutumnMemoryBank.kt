package dev.autumn.memory

actual object AutumnMemoryBank {
    private var memoryBuffer: ByteArray? = null

    actual fun allocate(sizeBytes: Long) {
        // Linear array mapping directly mapping onto the WebAssembly memory model limits.
        memoryBuffer = ByteArray(sizeBytes.toInt())
    }

    actual fun getInt(offset: Int): Int {
        val b = memoryBuffer ?: return 0
        return (b[offset].toInt() and 0xFF) or
               ((b[offset+1].toInt() and 0xFF) shl 8) or
               ((b[offset+2].toInt() and 0xFF) shl 16) or
               ((b[offset+3].toInt() and 0xFF) shl 24)
    }

    actual fun setInt(offset: Int, value: Int) {
        val b = memoryBuffer ?: return
        b[offset] = (value and 0xFF).toByte()
        b[offset+1] = ((value ushr 8) and 0xFF).toByte()
        b[offset+2] = ((value ushr 16) and 0xFF).toByte()
        b[offset+3] = ((value ushr 24) and 0xFF).toByte()
    }

    actual fun getByte(offset: Int): Byte {
        return memoryBuffer?.get(offset) ?: 0
    }

    actual fun setByte(offset: Int, value: Byte) {
        memoryBuffer?.set(offset, value)
    }

    actual fun getLong(offset: Int): Long {
        val low = getInt(offset).toLong() and 0xFFFFFFFFL
        val high = getInt(offset + 4).toLong() and 0xFFFFFFFFL
        return (high shl 32) or low
    }

    actual fun setLong(offset: Int, value: Long) {
        setInt(offset, (value and 0xFFFFFFFFL).toInt())
        setInt(offset + 4, (value ushr 32).toInt())
    }

    actual fun free() {
        memoryBuffer = null
    }
}
