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
    private val staticTicks = mutableListOf<() -> Boolean>()
    
    // The physical oscillator that pulses this scheduler bounds
    private val oscillator = HardwareOscillator(this)

    /**
     * Commences the physical Clock Tick loop via the platform-idiomatic oscillator.
     * @param coreId The physical OS core (isolcpus) to pin to (JVM/Native only). 
     */
    fun start(coreId: Int = -1) {
        oscillator.start(coreId)
    }

    /**
     * Halts the hardware oscillator execution.
     */
    fun stop() {
        oscillator.stop()
    }

    /**
     * Mounts a dynamic Arbiter (execution pipeline) onto this hardware clock.
     */
    fun bindArbiter(arbiter: Arbiter) {
        arbiter.synthesize()
        arbiters.add(arbiter)
    }

    /**
     * Mounts a statically compiled unrolled topology loop.
     */
    fun bindStaticTopology(tickFunc: () -> Boolean) {
        staticTicks.add(tickFunc)
    }

    /**
     * Advances the finite state machine by exactly one clock cycle boundary.
     * This pulses all mounted modules sequentially.
     */
    fun tick() {
        var anyDataProcessed = false

        // Pulse dynamic topology
        for (arbiter in arbiters) {
            if (arbiter.pollSweep()) {
                anyDataProcessed = true
            }
        }
        
        // Pulse static verified topologies
        for (func in staticTicks) {
            if (func()) {
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
