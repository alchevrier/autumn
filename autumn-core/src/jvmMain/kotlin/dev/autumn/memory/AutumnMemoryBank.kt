package dev.autumn.memory

import java.nio.ByteBuffer
import java.nio.ByteOrder

actual object AutumnMemoryBank {
    
    @PublishedApi
    internal var memory: ByteBuffer? = null

    actual fun allocate(sizeBytes: Int) {
        memory = ByteBuffer.allocateDirect(sizeBytes).order(ByteOrder.nativeOrder())
    }

    actual fun getInt(offset: Int): Int = memory!!.getInt(offset)
    actual fun setInt(offset: Int, value: Int) { memory!!.putInt(offset, value) }

    actual fun getByte(offset: Int): Byte = memory!!.get(offset)
    actual fun setByte(offset: Int, value: Byte) { memory!!.put(offset, value) }

    actual fun getLong(offset: Int): Long = memory!!.getLong(offset)
    actual fun setLong(offset: Int, value: Long) { memory!!.putLong(offset, value) }

    actual fun free() {
        memory = null
    }
}
