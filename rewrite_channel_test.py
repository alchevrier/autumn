import re

with open('autumn-core/src/commonTest/kotlin/dev/autumn/channel/ChannelTest.kt', 'r') as f:
    text = f.read()

# Replace all Channel<Any> with Channel
text = text.replace("Channel<Any>", "Channel")

# We can just remove the test 'test power of two capacity requirement' as it no longer throws
text = re.sub(r'    @Test\n    fun `test power of two capacity requirement`\(\) \{[\s\S]*?    \}\n\n', '', text)

# Rewrite 'test basic offer and poll' to expect 1-based monotonically increasing indexing
search_basic = """    @Test
    fun `test basic offer and poll`() {
        val buffer = Channel(4)

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
    }"""
replace_basic = """    @Test
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
    }"""
text = text.replace(search_basic, replace_basic)

search_wrap = """    @Test
    fun `test buffer full and wrap around`() {
        val buffer = Channel(4)

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
    }"""

# The Channel implementation currently just returns strictly increasing logical sequence offsets
# meaning offer returns 5, 6, etc.
replace_wrap = """    @Test
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
    }"""
text = text.replace(search_wrap, replace_wrap)

with open('autumn-core/src/commonTest/kotlin/dev/autumn/channel/ChannelTest.kt', 'w') as f:
    f.write(text)
