#!/bin/bash
cat << 'INNER' > autumn-core/src/commonMain/kotlin/dev/autumn/scheduler/SpeculativeClock.kt
package dev.autumn.scheduler

/**
 * A Clock representation for Burst-Routing.
 * 
 * Instead of re-evaluating the physical TSC (`rdtsc()`) on every single poll,
 * the speculative clock allows an execution core to execute a predetermined 
 * number of routing bounds (a "burst window") under the assumption that the 
 * physical clock boundary hasn't been breached, saving expensive system queries.
 */
class SpeculativeClock(val burstLimit: Int) {
    var burstsExecuted = 0
    var isPacing = false
    
    inline fun <T> speculate(action: () -> T?): T? {
        if (burstsExecuted >= burstLimit) {
            burstsExecuted = 0
            isPacing = true
            return null // Force the loop to break and yield to physical bounds
        }
        
        val result = action()
        if (result != null) {
            burstsExecuted++
        }
        return result
    }
    
    fun acknowledgeHardwareTick() {
        isPacing = false
        burstsExecuted = 0
    }
}
INNER
