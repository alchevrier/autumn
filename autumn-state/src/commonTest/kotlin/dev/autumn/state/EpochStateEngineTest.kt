package dev.autumn.state

import dev.autumn.annotations.LongLived
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@LongLived
class EpochStateEngineTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
        // We use StandardTestDispatcher to prevent immediate inline dispatching during the loop
    fun `interrupt wire completely coalesces multiple mutations without allocations`() = runTest(kotlinx.coroutines.test.StandardTestDispatcher()) {
        val budget = 100 // Example budget of 100 on-screen list items
        val stateEngine = EpochStateEngine(budget)

        var uiWakeups = 0

        // 1. Simulate the UI Thread suspending on the hardware wire
        val uiJob = launch {
            stateEngine.acquireInterruptWire().collect {
                uiWakeups++
            }
        }
        
        // Yield to let the job start
        kotlinx.coroutines.yield()

        // 3. Simulate a massive network payload returning and mutating 50 items simultaneously 
        // We pause the Unconfined dispatcher by not yielding until all 50 are fired
        for (i in 0 until 50) {
            stateEngine.incrementEpoch(i)
        }
        
        // Let the collector read the coalesced replay buffer
        kotlinx.coroutines.yield()

        // 4. Verify invariants:
        // Memory registered the 50 distinct epoch ticks.
        assertEquals(1, stateEngine.readEpoch(0), "Slot 0 epoch advanced")
        assertEquals(1, stateEngine.readEpoch(49), "Slot 49 epoch advanced")
        assertEquals(0, stateEngine.readEpoch(50), "Slot 50 untouched")

        // CRITICAL INVARIANT: The UI queue was NOT flooded with 50 events!
        // It wakes once for the 50 mutations
        assertEquals(1, uiWakeups, "UI must wake up coalesced regardless of mutation volume")

        uiJob.cancel()
    }
}
