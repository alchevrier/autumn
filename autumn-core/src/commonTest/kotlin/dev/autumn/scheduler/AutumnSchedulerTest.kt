package dev.autumn.scheduler

import dev.autumn.channel.AutumnChannel
import dev.autumn.channel.Channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutumnSchedulerTest {

    @Test
    fun `test clock-aware deterministic FSM ticks via Hot Arbiter`() {
        val scheduler = AutumnScheduler()
        val arbiter = Arbiter(profile = DispatcherProfile.HOT_ISOLATED)
        val ingressQueue = AutumnChannel<Any>(16)

        var handledIdx = -1

        // In the fully assembled backend, this binds channels with their topological weights.
        arbiter.addChannel(ingressQueue.partitions[0], weight = 1) { idx ->
            handledIdx = idx
        }
        scheduler.bindArbiter(arbiter)

        // Network Ingress Simulation (Inject 5 items)
        for (i in 0 until 5) {
            val idx = ingressQueue.partitions[0].offer()
            assertTrue(idx != -1)
            ingressQueue.partitions[0].commitOffer()
        }

        // Rather than a blind while(true), the environment explicitly clocks the pipeline 
        for (cycle in 0 until 10) {
            scheduler.tick() // Deterministic clock step
        }

        assertEquals(10L, scheduler.getClock())
        assertEquals(5, handledIdx) // Last inserted item was 5 (1-based index)
        
        // Assert the queue was drained natively by the synthesized Arbiter
        val remainingIdx = ingressQueue.partitions[0].poll()
        assertEquals(-1, remainingIdx, "Scheduler should have swept the channel entirely")
    }

    @Test
    fun `test unwinding synthesis based on topological weights`() {
        val scheduler = AutumnScheduler()
        val arbiter = Arbiter(profile = DispatcherProfile.HOT_ISOLATED)
        val netChannel = Channel(16)
        val sessionChannel = Channel(16)
        val coldChannel = Channel(16)

        var fastProcessed = 0

        // Network weighs 3x more than cold, and session 2x more
        arbiter.addChannel(netChannel, weight = 3) { fastProcessed++ }
        arbiter.addChannel(sessionChannel, weight = 2) { }
        arbiter.addChannel(coldChannel, weight = 1) { }
        
        scheduler.bindArbiter(arbiter)

        // We injected 3 + 2 + 1 = 6 schedule operations. Let's clock exactly 1 time.
        // It should poll the network channel 3 times on the first tick!
        
        // Inject 5 items into network channel
        for (i in 0 until 5) {
            netChannel.offer()
            netChannel.commitOffer()
        }

        scheduler.tick()

        // The arbiter should have consumed exactly 3 items from network channel because weight = 3
        assertEquals(3, fastProcessed) // The handler should have been invoked 3 times
    }

    @Test
    fun `test cold shared dispatcher profile execution`() {
        val scheduler = AutumnScheduler()
        val arbiter = Arbiter(profile = DispatcherProfile.COLD_SHARED)
        val uiChannel = Channel(16)

        arbiter.addChannel(uiChannel, weight = 1) { }
        scheduler.bindArbiter(arbiter)

        var yieldTriggered = false
        scheduler.onIdle = { profile ->
            if (profile == DispatcherProfile.COLD_SHARED) {
                yieldTriggered = true
            }
        }

        scheduler.tick() // Executes without data, triggering OS yield callback

        assertEquals(1L, scheduler.getClock())
        assertTrue(yieldTriggered, "Scheduler should trigger onIdle hook for COLD_SHARED profiles when entirely dry")
    }
}
