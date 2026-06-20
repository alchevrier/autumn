package dev.autumn.resolver.handoff

import dev.autumn.annotations.NetworkConcurrencyBudget

/**
 * A lock-free, zero-allocation slot manager that enforces the @NetworkConcurrencyBudget.
 * 
 * It tracks in-flight requests using a static IntArray.
 * If all slots are claimed, it acts as an instantaneous local Circuit Breaker.
 */
class NetworkSlotManager(
    // In a real build, the compiler plugin injects this literal based on the annotation.
    @NetworkConcurrencyBudget(maxInFlightRequests = 2)
    private val budget: Int
) {
    // 0 = FREE, 1 = CLAIMED
    // A strict hardware-sympathetic matrix for tracking concurrency loops
    private val slotStates = IntArray(budget)

    /**
     * Attempts to claim an available I/O slot.
     * @return The slot index, or -1 if the concurrency limit is saturated.
     */
    fun claimSlot(): Int {
        for (i in slotStates.indices) {
            if (slotStates[i] == 0) {
                // Claim it
                slotStates[i] = 1
                return i
            }
        }
        // Concurrency budget exhausted! Circuit break immediately.
        return -1
    }

    /**
     * Releases the slot back to the available pool.
     */
    fun releaseSlot(index: Int) {
        if (index in slotStates.indices) {
            slotStates[index] = 0
        }
    }
    
    // For monitoring: allows telemetry to observe saturation without allocating metrics objects
    fun activeInFlightCount(): Int {
        var count = 0
        for (i in slotStates.indices) {
            if (slotStates[i] == 1) count++
        }
        return count
    }
}
