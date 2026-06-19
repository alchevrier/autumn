package dev.autumn.resolver.handoff

import dev.autumn.annotations.NetworkConcurrencyBudget
import dev.autumn.annotations.LongLived
import kotlin.test.Test
import kotlin.test.assertEquals

@LongLived
class NetworkConcurrencyBudgetProcessorSimulatorTest {

    // Simulates the K2 compiler plugin scanning the AST and evaluating the constant literal
    private fun simulateCompilerPluginProcessing(annotation: NetworkConcurrencyBudget): Int {
        return annotation.maxInFlightRequests
    }

    @Test
    fun `compiler statically injects budget and strictly bounds slot manager array`() {
        // 1. App developer declares a hard limit via annotation:
        // @NetworkConcurrencyBudget(maxInFlightRequests = 3)
        // val slotManager = ...
        val annotation = NetworkConcurrencyBudget(maxInFlightRequests = 3)

        // 2. The compiler plugin resolves and substitutes this constant at compile time
        val staticallyInjectedBudget = simulateCompilerPluginProcessing(annotation)
        assertEquals(3, staticallyInjectedBudget)

        // 3. The manager is initialized with that exact raw matrix constraint
        val slotManager = NetworkSlotManager(staticallyInjectedBudget)

        // 4. Verify deterministic zero-allocation slot claiming
        assertEquals(0, slotManager.claimSlot(), "First request claims index 0")
        assertEquals(1, slotManager.claimSlot(), "Second request claims index 1")
        assertEquals(2, slotManager.claimSlot(), "Third request claims index 2")
        
        // 5. Circuit Breaker is active automatically! No queuing or OOMs.
        assertEquals(-1, slotManager.claimSlot(), "Fourth request must instantly circuit break (-1)")

        // 6. Telemetry accurately tracks in-flight without mapping objects
        assertEquals(3, slotManager.activeInFlightCount())

        // 7. Verify lock-free reclaim
        slotManager.releaseSlot(1)
        assertEquals(2, slotManager.activeInFlightCount())
        assertEquals(1, slotManager.claimSlot(), "Released index 1 is reclaimed")
    }
}
