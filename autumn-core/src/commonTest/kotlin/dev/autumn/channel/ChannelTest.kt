package dev.autumn.channel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChannelTest {

    @Test
    fun `test basic offer and poll`() {
        val buffer = Channel(4)

        // Initial state should be empty
        assertEquals(-1, buffer.poll())

        // Offer one item
        val offerIdx = buffer.offer()
        assertEquals(1, offerIdx) // 1-based indexing
        buffer.commitOffer()

        // Poll one item
        val pollIdx = buffer.poll()
        assertEquals(1, pollIdx)
        buffer.commitPoll()
        
        // Should be empty again
        assertEquals(-1, buffer.poll())
    }

    @Test
    fun `test buffer full and continuous sequencing`() {
        val buffer = Channel(4)

        // Fill the buffer
        assertEquals(1, buffer.offer()); buffer.commitOffer()
        assertEquals(2, buffer.offer()); buffer.commitOffer()
        assertEquals(3, buffer.offer()); buffer.commitOffer()
        assertEquals(4, buffer.offer()); buffer.commitOffer()

        // Buffer should now be full
        assertEquals(-1, buffer.offer())

        // Drain two items
        assertEquals(1, buffer.poll()); buffer.commitPoll()
        assertEquals(2, buffer.poll()); buffer.commitPoll()

        // Should be able to offer two more
        assertEquals(5, buffer.offer()); buffer.commitOffer()
        assertEquals(6, buffer.offer()); buffer.commitOffer()

        // Buffer should be full again
        assertEquals(-1, buffer.offer())
        
        // Drain remaining
        assertEquals(3, buffer.poll()); buffer.commitPoll()
        assertEquals(4, buffer.poll()); buffer.commitPoll()
        assertEquals(5, buffer.poll()); buffer.commitPoll()
        assertEquals(6, buffer.poll()); buffer.commitPoll()
        
        // Empty again
        assertEquals(-1, buffer.poll())
    }
}
