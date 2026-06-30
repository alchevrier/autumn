package dev.autumn.sandbox

import dev.autumn.annotations.CycleBudget
import dev.autumn.annotations.LongLived
import dev.autumn.annotations.Observe

/**
 * Demonstrates how the K2 Compiler Plugin uses @CycleBudget to enforce 
 * High-Level Synthesis (HLS) constraints and prevent Instruction Cache (L1i) blowouts.
 */

class CycleBudgetCases {

    val metrics = LongArray(100)

    /**
     * ✅ PASSES CYCLE BUDGET
     * 
     * This function contains a simple branch and two local variable operations.
     * The CycleBudgetVisitor estimates it at ~15-20 CPU cycles limit.
     * With a budget of 40, this perfectly synthesizes for extreme determinism.
     */
    @Observe("fastPath")
    @CycleBudget(limit = 60)
    @LongLived
    fun ultraFastPath(latencyNano: Long) {
        if (latencyNano < 1000) {
            metrics[0] = latencyNano
        }
    }

    /**
     * ⚠️ HOW TO TRIP THE CYCLE BUDGET
     *
     * This function contains string interpolation, heap allocation traps, 
     * and I/O branches. The CycleBudgetVisitor estimates this around ~450+ CPU cycles.
     * 
     * If you uncomment `@CycleBudget(limit = 200)`, compiling this file will immediately 
     * crash with a K2 Compiler Error:
     * e: [Autumn HLS] Cycle Budget VIOLATION: 'slowPath' estimated at 450 cycles (Limit: 200).
     */
    @Observe("slowPath")
    // @CycleBudget(limit = 200) // <-- UNCOMMENT TO SEE K2 COMPILER ERROR!
    @LongLived
    fun slowPath(latencyNano: Long) {
        if (latencyNano > 1000) {
            // String interpolation triggers StringBuilder allocs & calls
            val msg = "Outlier detected! Latency was $latencyNano ns"
            
            // I/O operations are extremely expensive in clock cycles
            println(msg)
            
            // Expensive math logic and external jumps
            metrics[0] = Math.max(metrics[0], latencyNano)
        }
    }

    /**
     * ✅ @MaxIterations WCET AUDIT COMPLIANCE
     *
     * Under ADR-0028 Formal Certificates, `@InjectTopology(wcetAuditable=true)` 
     * requires strict loop bounds to calculate the Control Flow Graph.
     * Without `@MaxIterations`, this loop would cause a compiler error during audit.
     */
    @Observe("auditPath")
    @CycleBudget(limit = 500)
    @LongLived
    fun auditablePath() {
        @dev.autumn.annotations.MaxIterations(10)
        for (i in 0 until 10) {
            metrics[i] = i * 2L
        }
    }

    /**
     * ⛔ UNBOUNDED LOOP TRAP (WCET COMPILER ERROR)
     *
     * If this handler were attached to an `@InjectTopology(wcetAuditable=true)` pipeline,
     * the compiler would instantly flag this as a fatal WCET error. Static analysis 
     * cannot compute the Worst-Case Execution Time (WCET) of an unbounded `while (true)` 
     * loop without empirical markers.
     */
    @Observe("infiniteTrap")
    @LongLived
    fun infiniteTrapHandler(state: Int) {
        var active = true
        // Missing @MaxIterations entirely!
        while (active) {
            metrics[0] = state.toLong()
            if (state == 0) active = false
        }
    }
}
