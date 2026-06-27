package dev.autumn.memory

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
actual object AutumnMemoryBank {

    private var memoryPtr: CPointer<ByteVar>? = null

    actual fun allocate(sizeBytes: Long) {
        val alignment = 4096UL
        memScoped {
            val memPtr = alloc<CPointerVar<ByteVar>>()
            if (posix_memalign(memPtr.ptr.reinterpret(), alignment, sizeBytes.toULong()) != 0) {
                throw RuntimeException("posix_memalign failed to allocate off-heap memory")
            }
            memoryPtr = memPtr.value
        }
    }

    actual fun getInt(offset: Int): Int {
        return (memoryPtr!! + offset)!!.reinterpret<IntVar>()[0]
    }
    actual fun setInt(offset: Int, value: Int) {
        (memoryPtr!! + offset)!!.reinterpret<IntVar>()[0] = value
    }

    actual fun getByte(offset: Int): Byte {
        return (memoryPtr!! + offset)!!.reinterpret<ByteVar>()[0]
    }
    actual fun setByte(offset: Int, value: Byte) {
        (memoryPtr!! + offset)!!.reinterpret<ByteVar>()[0] = value
    }

    actual fun getLong(offset: Int): Long {
        return (memoryPtr!! + offset)!!.reinterpret<LongVar>()[0]
    }
    actual fun setLong(offset: Int, value: Long) {
        (memoryPtr!! + offset)!!.reinterpret<LongVar>()[0] = value
    }

    actual fun free() {
        if (memoryPtr != null) {
            free(memoryPtr)
            memoryPtr = null
        }
    }
}
