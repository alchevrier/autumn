package dev.autumn.memory

import sun.misc.Unsafe
import java.lang.reflect.Field

actual object AutumnMemoryBank {
    
    @PublishedApi
    internal var baseAddress: Long = 0L

    private val unsafe: Unsafe by lazy {
        val field: Field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        field.get(null) as Unsafe
    }

    actual fun allocate(sizeBytes: Long) {
        baseAddress = unsafe.allocateMemory(sizeBytes.toLong())
        unsafe.setMemory(baseAddress, sizeBytes.toLong(), 0.toByte())
    }

    actual fun getInt(offset: Int): Int = unsafe.getInt(baseAddress + offset.toLong())
    actual fun setInt(offset: Int, value: Int) { unsafe.putInt(baseAddress + offset.toLong(), value) }

    actual fun getByte(offset: Int): Byte = unsafe.getByte(baseAddress + offset.toLong())
    actual fun setByte(offset: Int, value: Byte) { unsafe.putByte(baseAddress + offset.toLong(), value) }

    actual fun getLong(offset: Int): Long = unsafe.getLong(baseAddress + offset.toLong())
    actual fun setLong(offset: Int, value: Long) { unsafe.putLong(baseAddress + offset.toLong(), value) }

    actual fun free() {
        if (baseAddress != 0L) {
            unsafe.freeMemory(baseAddress)
            baseAddress = 0L
        }
    }
}