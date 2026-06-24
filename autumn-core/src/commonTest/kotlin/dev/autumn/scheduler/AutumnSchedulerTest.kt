package dev.autumn.scheduler

import dev.autumn.channel.AutumnChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutumnSchedulerTest {

    @Test
    fun `test clock-aware deterministic FSM ticks`() {
        val scheduler = AutumnScheduler()
        val ingressQueue = AutumnChannel<Any>(16)
        val egressQueue = AutumnChannel<Any>(16)

        var processedCount = 0

        // The compiler mechanically binds the precise dataflow pipeline 
        // into the clock cycle schedule
        scheduler.bindOperation(expectedCycles = 1L, isNetworkIngress = true) {
            val idx = ingressQueue.buffer.poll()
            if (idx != -1) {
                processedCount++
                
                val outIdx = egressQueue.buffer.offer()
                if (outIdx != -1) {
                    egressQueue.buffer.commitOffer()
                }
                ingressQueue.buffer.commitPoll()
            }
        }

        // Network Ingress Simulation (Inject 5 items)
        for (i in 0 until 5) {
            val idx = ingressQueue.buffer.offer()
            assertTrue(idx != -1)
            ingressQueue.buffer.commitOffer()
        }

        // Rather than a blind while(true), the environment explicitly clocks the pipeline 
        for (cycle in 0 until 10) {
            scheduler.tick() // Deterministic clock step
        }

        assertEquals(10L, scheduler.getClock(), "Clock should track exact ticks")
        assertEquals(5, processedCount, "Should exact process precisely bounded by cycle operations")
        
        // Assert the data was piped to the egress channel correctly
        var egressDrained = 0
        while(egressQueue.buffer.poll() != -1) {
            egressDrained++
            egressQueue.buffer.commitPoll()
        }
        assertEquals(5, egressDrained)
    }
}
