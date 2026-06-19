package dev.autumn.config

import dev.autumn.annotations.LongLived

class StringRegistry(maxStrings: Int) {
    // Stores strings as offsets and lengths in a global buffer
    @LongLived
    private val offsets = IntArray(maxStrings)
    @LongLived
    private val lengths = IntArray(maxStrings)
    var count = 0
        private set

    // The single byte array containing all string data (could be the network buffer itself)
    private var sourceBuffer: ByteArray? = null

    fun setBuffer(buffer: ByteArray) {
        sourceBuffer = buffer
    }

    fun register(offset: Int, length: Int): Int {
        val id = count++
        offsets[id] = offset
        lengths[id] = length
        return id
    }

    fun getString(id: Int): String {
        val buf = sourceBuffer ?: return ""
        if (id < 0 || id >= count) return ""
        return buf.decodeToString(offsets[id], offsets[id] + lengths[id])
    }

    fun clear() {
        count = 0
        sourceBuffer = null
    }
}
