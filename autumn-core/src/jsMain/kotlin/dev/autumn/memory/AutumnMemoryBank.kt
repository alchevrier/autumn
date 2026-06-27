package dev.autumn.memory

import org.khronos.webgl.DataView
import org.khronos.webgl.ArrayBuffer

actual object AutumnMemoryBank {
    
    private var view: DataView? = null

    actual fun allocate(sizeBytes: Long) {
        view = DataView(ArrayBuffer(sizeBytes.toInt()))
    }

    actual fun getInt(offset: Int): Int = view!!.getInt32(offset, true)
    actual fun setInt(offset: Int, value: Int) { view!!.setInt32(offset, value, true) }

    actual fun getByte(offset: Int): Byte = view!!.getInt8(offset)
    actual fun setByte(offset: Int, value: Byte) { view!!.setInt8(offset, value) }

    actual fun getLong(offset: Int): Long = 0L // Stub for standard JS 53-bit int limits
    actual fun setLong(offset: Int, value: Long) {}

    actual fun free() {
        view = null
    }
}
