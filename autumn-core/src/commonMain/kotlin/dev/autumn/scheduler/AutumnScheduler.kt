package dev.autumn.scheduler

import dev.autumn.scheduler.platform.OSThreadBridge

/**
 * The core Finite State Machine (FSM) driving the Autumn execution model.
 * 
 * Unlike traditional frameworks or basic DPDK busy-spin loops, Autumn uses 
 * **Clock-Aware Circuit Programming**. The scheduling is deterministic and 
 * statically enforced.
 * 
 * This Clock pulses the registered Arbiters (which hold the synthesized 
 * channel polling layouts) on a predictable `tick()` representing a hardware clock boundary.
 */
class AutumnScheduler(
    private val osThreadBridge: OSThreadBridge? = null
) {

    /**
     * Dictates the execution aggressiveness of the underlying hardware oscillator.
     */
    var powerMode: PowerMode = PowerMode.ACTIVE

    /**
     * Platform-specific hook representing the physical hardware's backoff logic.
     * On JVM, this might map to `Thread.yield()` or `LockSupport.parkNanos()`.
     * On Native, this maps to `sched_yield()` or atomic waits.
     */
    var onIdle: (DispatcherProfile) -> Unit = { profile ->
        if (osThreadBridge != null) {
            when (profile) {
                DispatcherProfile.HOT_ISOLATED -> osThreadBridge.pauseHardware()
                DispatcherProfile.COLD_SHARED -> osThreadBridge.yieldThread()
            }
        }
    }

    private var currentCycle: Long = 0L

    private val arbiters = mutableListOf<Arbiter>()

    /**
     * Mounts an Arbiter (execution pipeline) onto this hardware clock.
     */
    fun bindArbiter(arbiter: Arbiter) {
        arbiter.synthesize()
        arbiters.add(arbiter)
    }

    /**
     * Advances the finite state machine by exactly one clock cycle boundary.
     * This pulses all mounted arbiters sequentially.
     */
    fun tick() {
        var anyDataProcessed = false

        for (arbiter in arbiters) {
            if (arbiter.pollSweep()) {
                anyDataProcessed = true
            }
        }
        
        currentCycle++

        // Adaptive Scheduling Feedback
        if (powerMode == PowerMode.ACTIVE && !anyDataProcessed) {
            // Apply profile-specific backoff strategies for idle pipelines
            for (arbiter in arbiters) {
                // If it is HOT, it applies a micro-pause. If COLD, it yields to the OS.
                onIdle(arbiter.profile)
            }
        }
    }

    fun getClock(): Long = currentCycle
}
