package dev.autumn.scheduler

import dev.autumn.channel.Channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArbiterTest {

    @Test
    fun `test arbiter sweeping isolated pipeline`() {
        val arbiter = Arbiter(DispatcherProfile.HOT_ISOLATED)
        val channel = Channel(16)
        
        var receivedIdx = -1
        arbiter.addChannel(channel, weight = 1) { idx ->
            receivedIdx = idx
        }
        arbiter.synthesize()
        
        // Initial empty sweep
        assertFalse(arbiter.pollSweep(), "Sweep should return false when no data is processed")

        // Inject data
        val insertedIdx = channel.offer()
        channel.commitOffer()

        // Sweep again
        assertTrue(arbiter.pollSweep(), "Sweep should return true when data is processed natively")
        assertEquals(insertedIdx, receivedIdx, "Arbiter should have dispatched the event index to the downstream handler")

        // Next sweep is empty
        assertFalse(arbiter.pollSweep(), "Channel should be barren after processing")
    }

    @Test
    fun `test arbiter synthesis properly unrolls weights`() {
        val arbiter = Arbiter(profile = DispatcherProfile.HOT_ISOLATED)
        val fastChannel = Channel(16)
        val slowChannel = Channel(16)

        var fastProcessed = 0
        var slowProcessed = 0

        arbiter.addChannel(fastChannel, weight = 4) { fastProcessed++ }
        arbiter.addChannel(slowChannel, weight = 1) { slowProcessed++ }
        
        // Synthesis should flat-map into an array of size 5
        arbiter.synthesize()

        // Let's gorge both channels
        for (i in 0 until 5) {
            fastChannel.offer(); fastChannel.commitOffer()
            slowChannel.offer(); slowChannel.commitOffer()
        }

        // A single sweep executes the synthesized layout
        val processedAnything = arbiter.pollSweep()
        assertTrue(processedAnything)

        // After one sweep, it should have dispatched fastChannel 4 times, and slowChannel 1 time.
        assertEquals(4, fastProcessed) 
        assertEquals(1, slowProcessed)
    }
}
