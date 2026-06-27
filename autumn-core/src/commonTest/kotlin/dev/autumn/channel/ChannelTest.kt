package dev.autumn.channel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChannelTest {

    @Test
    fun `test power of two capacity requirement`() {
        assertFailsWith<IllegalArgumentException> {
            Channel<Any>(10)
        }
        // Should not fail
        Channel<Any>(16)
        Channel<Any>(1024)
    }

    @Test
    fun `test basic offer and poll`() {
        val buffer = Channel<Any>(4)

        // Initial state should be empty
        assertEquals(-1, buffer.poll())

        // Offer one item
        val offerIdx = buffer.offer()
        assertEquals(0, offerIdx)
        buffer.commitOffer()

        // Poll one item
        val pollIdx = buffer.poll()
        assertEquals(0, pollIdx)
        buffer.commitPoll()
        
        // Should be empty again
        assertEquals(-1, buffer.poll())
    }

    @Test
    fun `test buffer full and wrap around`() {
        val buffer = Channel<Any>(4)

        // Fill the buffer
        assertEquals(0, buffer.offer()); buffer.commitOffer()
        assertEquals(1, buffer.offer()); buffer.commitOffer()
        assertEquals(2, buffer.offer()); buffer.commitOffer()
        assertEquals(3, buffer.offer()); buffer.commitOffer()

        // Buffer should now be full
        assertEquals(-1, buffer.offer())

        // Drain two items
        assertEquals(0, buffer.poll()); buffer.commitPoll()
        assertEquals(1, buffer.poll()); buffer.commitPoll()

        // Should be able to offer two more, wrapping around the mask (capacity=4, mask=3)
        // Next exact sequence numbers: 4, 5 -> bitwise AND with 3 gives indices 0, 1
        assertEquals(0, buffer.offer()); buffer.commitOffer()
        assertEquals(1, buffer.offer()); buffer.commitOffer()

        // Buffer should be full again
        assertEquals(-1, buffer.offer())
        
        // Drain remaining
        assertEquals(2, buffer.poll()); buffer.commitPoll()
        assertEquals(3, buffer.poll()); buffer.commitPoll()
        assertEquals(0, buffer.poll()); buffer.commitPoll()
        assertEquals(1, buffer.poll()); buffer.commitPoll()
        
        // Empty again
        assertEquals(-1, buffer.poll())
    }
}
