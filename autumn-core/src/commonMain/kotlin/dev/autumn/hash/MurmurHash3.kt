package dev.autumn.hash

/**
 * A zero-allocation Kotlin Multiplatform implementation of MurmurHash3 (x86_32).
 * 
 * MurmurHash3 is a non-cryptographic hash function renowned for its excellent 
 * avalanche properties and extreme speed. This makes it the ideal candidate for 
 * driving the `HashRouter` Ingress pattern without producing JVM garbage.
 */
object MurmurHash3 {
    private const val C1 = 0xcc9e2d51L.toInt()
    private const val C2 = 0x1b873593L.toInt()

    /**
     * Hashes a raw ByteArray. 
     * Ideal for Socket/NIC ingress boundaries reading raw parsed frames.
     */
    fun hash32(data: ByteArray, length: Int = data.size, seed: Int = 0): Int {
        var h1 = seed
        val roundedEnd = length and 0xFFFFFFFC.toInt() // length & ~3

        for (i in 0 until roundedEnd step 4) {
            var k1 = (data[i].toInt() and 0xFF) or
                    ((data[i + 1].toInt() and 0xFF) shl 8) or
                    ((data[i + 2].toInt() and 0xFF) shl 16) or
                    ((data[i + 3].toInt() and 0xFF) shl 24)

            k1 = k1 * C1
            k1 = k1.rotateLeft(15)
            k1 = k1 * C2

            h1 = h1 xor k1
            h1 = h1.rotateLeft(13)
            h1 = h1 * 5 + 0xe6546b64L.toInt()
        }

        var k1 = 0
        val tail = length and 3
        if (tail == 3) k1 = k1 xor ((data[roundedEnd + 2].toInt() and 0xFF) shl 16)
        if (tail >= 2) k1 = k1 xor ((data[roundedEnd + 1].toInt() and 0xFF) shl 8)
        if (tail >= 1) {
            k1 = k1 xor (data[roundedEnd].toInt() and 0xFF)
            k1 = k1 * C1
            k1 = k1.rotateLeft(15)
            k1 = k1 * C2
            h1 = h1 xor k1
        }

        h1 = h1 xor length
        return fmix32(h1)
    }

    /**
     * Hashes a String directly via its underlying Char representation. 
     * Avoids the JVM `String.toByteArray(UTF_8)` object allocation!
     */
    fun hash32(text: String, seed: Int = 0): Int {
        var h1 = seed
        val length = text.length
        // Chars are 2 bytes in Kotlin. A chunk of 4 bytes is 2 Chars.
        val roundedEnd = length and 0xFFFFFFFE.toInt() 

        for (i in 0 until roundedEnd step 2) {
            var k1 = (text[i].code and 0xFFFF) or
                    ((text[i + 1].code and 0xFFFF) shl 16)

            k1 = k1 * C1
            k1 = k1.rotateLeft(15)
            k1 = k1 * C2

            h1 = h1 xor k1
            h1 = h1.rotateLeft(13)
            h1 = h1 * 5 + 0xe6546b64L.toInt()
        }

        var k1 = 0
        if ((length and 1) != 0) {
            k1 = k1 xor (text[length - 1].code and 0xFFFF)
            k1 = k1 * C1
            k1 = k1.rotateLeft(15)
            k1 = k1 * C2
            h1 = h1 xor k1
        }

        // The hash finalizer uses the raw byte length (length * 2 for Chars)
        h1 = h1 xor (length * 2)
        return fmix32(h1)
    }

    private fun fmix32(hash: Int): Int {
        var h = hash
        h = h xor (h ushr 16)
        h = h * 0x85ebca6bL.toInt()
        h = h xor (h ushr 13)
        h = h * 0xc2b2ae35L.toInt()
        h = h xor (h ushr 16)
        return h
    }
}
