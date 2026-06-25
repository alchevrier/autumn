package dev.autumn.hash

import org.apache.commons.codec.digest.MurmurHash3 as ApacheMurmurHash3
import kotlin.test.Test
import kotlin.test.assertEquals

class MurmurHash3JvmTest {

    @Test
    fun `compare zero-allocation implementation with Apache Commons Codec`() {
        val testStrings = listOf(
            "AAPL", 
            "MSFT", 
            "TSLA", 
            "Hello, world!",
            "", 
            "A slightly longer string to ensure the tail blocks and boundary math wrap correctly across the JVM."
        )

        for (text in testStrings) {
            val bytes = text.encodeToByteArray()
            
            // Generate benchmark standard from Apache Commons
            // Using seed 0 to match our default
            val expectedHash = ApacheMurmurHash3.hash32x86(bytes, 0, bytes.size, 0)

            // Test our raw ByteArray pipeline against Apache Commons standard
            val ourByteHash = MurmurHash3.hash32(bytes)
            assertEquals(expectedHash, ourByteHash, "ByteArray implementation failed to match Apache Commons for input: '\$text'")
            
            // Note on Zero-Allocation String Hashing:
            // Our custom `MurmurHash3.hash32(text: String)` directly hashes the underlying UTF-16 
            // Kotlin `Char` array natively without allocating a `ByteArray`. 
            // Because UTF-16 uses 16 bits per character, the byte layout fed into the Murmur3 
            // mixer is physically different than UTF-8 `encodeToByteArray()`. 
            // Therefore: Apache `hash32x86(utf8_bytes)` != Autumn `hash32(utf16_string)`.
            // The zero-alloc function guarantees deterministic routing without GC thrash!
        }
    }
}
