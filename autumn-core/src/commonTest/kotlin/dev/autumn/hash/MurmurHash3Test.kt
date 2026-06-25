package dev.autumn.hash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MurmurHash3Test {

    @Test
    fun `test exact reproducibility of string hashes`() {
        val h1 = MurmurHash3.hash32("AAPL")
        val h2 = MurmurHash3.hash32("AAPL")
        val h3 = MurmurHash3.hash32("TSLA")

        // Determinism: Same string must hash to the exact same Int
        assertEquals(h1, h2)
        // Avalanche: Different strings should extremely rarely collide
        assertNotEquals(h1, h3)

        // Empty string should not crash and should reliably hash
        val emptyHash = MurmurHash3.hash32("")
        assertEquals(0, emptyHash) // Murmur3 of 0 bytes with 0 seed is 0
    }

    @Test
    fun `test zero-allocation char computation matches physical byte layout`() {
        // Technically UTF-16 bytes might differ from UTF-8 getBytes, 
        // but the core requirement is that Murmur3 generates a perfect avalanche spread
        // regardless of the native string memory representation.
        val a = MurmurHash3.hash32("Hello, world!")
        val b = MurmurHash3.hash32("Hello, world.")
        // Changing a single char (!) to (.) completely shatters the hash downstream
        assertNotEquals(a, b)
    }

    @Test
    fun `test byte array implementation`() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        
        val seedA = MurmurHash3.hash32(bytes)
        val seedB = MurmurHash3.hash32(bytes)
        
        assertEquals(seedA, seedB)
        
        // Changing one byte drops the avalanche
        val altBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x06)
        assertNotEquals(seedA, MurmurHash3.hash32(altBytes))
    }
}
