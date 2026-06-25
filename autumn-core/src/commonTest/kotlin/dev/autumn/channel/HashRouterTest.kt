package dev.autumn.channel

import kotlin.test.Test
import kotlin.test.assertEquals
import dev.autumn.core.channel.HashRouter
import kotlin.test.assertTrue

class HashRouterTest {

    @Test
    fun `test network frame ingress routing to proper partitioned channels`() {
        // Construct 4 isolated partitions (e.g. mapping to 4 distinct CPU cores)
        val channels = arrayOf(Channel(16), Channel(16), Channel(16), Channel(16))
        
        // Router hashes based on the symbol (AAPL, MSFT, etc)
        val router = HashRouter<String>(channels) { symbol ->
            // A simple hash function for deterministic testing
            when(symbol) {
                "AAPL" -> 0
                "MSFT" -> 1
                "TSLA" -> 2
                "AMZN" -> 3
                else -> 0
            }
        }

        // Simulating the NIC/Ingress Socket picking up frames
        router.route("AAPL")
        router.route("AAPL")
        router.route("TSLA")

        // Assert that the Arbiter bound to Core 0 (Partition 0) got both AAPL messages
        assertEquals(2, channels[0].offer()) // Next offer would be idx 2
        
        // Assert that Core 1 is untouched
        assertEquals(0, channels[1].offer()) 
        
        // Assert that Core 2 got the TSLA message
        assertEquals(1, channels[2].offer())
    }

    @Test
    fun `test hash router backpressure handling`() {
        val channels = arrayOf(Channel(2))
        val router = HashRouter<String>(channels) { 0 } // Everything hashes to channel 0

        assertTrue(router.route("MSG1"))
        assertTrue(router.route("MSG2"))
        
        // Channel capacity is 2, third message should be dropped (backpressure)
        val success = router.route("MSG3")
        assertEquals(false, success, "Router should return false when encountering backpressure in target partition")
    }

    @Test
    fun `test hash router negative hash handling`() {
        val channels = arrayOf(Channel(4), Channel(4))
        
        // A hash function that strictly returns negatives
        val router = HashRouter<String>(channels) { -5 }
        
        // The HashRouter internally does `(hash and 0x7FFFFFFF) % size` to avoid ArrayIndexOutOfBounds
        assertTrue(router.route("NEGATIVE_HASH_MSG"))
    }
}
