package dev.autumn.ui.ioc

import dev.autumn.annotations.LongLived
import dev.autumn.resolver.handoff.RawNetworkClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@LongLived
class AutumnMotherboardTest {

    // Same hardware mock from the resolver tests
    class MockRawNetworkClient : RawNetworkClient {
        var callCount = 0
        var simulateFailure = false

        // Example valid payload matching the JsonConfigParser expectations
        // The parser logic strictly looks for '"resources": {' map objects, not lists
        val cannedPayload = """
            { "resources": { "hero": { "type": "banner", "path": "/assets/hero.jpg", "action": "click_hero" } } }
        """.trimIndent().encodeToByteArray()

        override suspend fun executeRaw(
            endpoint: String,
            method: String,
            requestBody: ByteArray?
        ): Result<ByteArray> {
            callCount++
            return if (simulateFailure) Result.failure(Exception("Offline")) else Result.success(cannedPayload)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `motherboard connects network to state epoch without allocations`() = runTest(StandardTestDispatcher()) {
        val client = MockRawNetworkClient()
        val motherboard = AutumnMotherboard(
            networkClient = client,
            stringRegistryBudget = 10,
            concurrencyBudget = 2,
            epochMatrixBudget = 5
        )

        // 1. Initial State assertions
        assertEquals(0, motherboard.stateEngine.readEpoch(0))

        // Monitor the hardware wire
        var uiWakeups = 0
        val uiJob = launch {
            motherboard.stateEngine.acquireInterruptWire().collect {
                uiWakeups++
            }
        }
        
        // Let the collector start
        yield()

        // 2. The physical execution pipeline (app claims a slot and executes)
        val ioSlot = motherboard.networkEngine.claimSlot()
        assertTrue(ioSlot >= 0, "Must be able to claim a network slot")
        
        // Execute the pipeline!
        val result = motherboard.networkEngine.executeInPlace(ioSlot, "/v1/config")
        
        assertTrue(result.isSuccess, "Pipeline should succeed")
        assertEquals(1, client.callCount)

        // Give the shared flow a chance to dispatch the unit signal
        yield()

        // 3. Confirm entire pipeline resolution properties
        // A. Network released the slot back
        assertEquals(0, motherboard.slotManager.activeInFlightCount(), "Network slot reclaimed")
        
        // B. Parser populated the ConfigManager/Registry with string boundaries from payload
        // The parser registers hero, banner, /assets/hero.jpg, and click_hero
        assertEquals(4, motherboard.stringRegistry.count, "Registry must contain precisely 4 resolved String boundaries")
        
        // C. The epoch engine caught the update callback from the engine and flipped the bit
        assertEquals(1, motherboard.stateEngine.readEpoch(0), "Epoch 0 mutated correctly")

        // D. The global UI wire fired exactly once 
        assertEquals(1, uiWakeups, "UI wire should pulse reflecting the epoch mutation")

        uiJob.cancel()
    }

    @Test
    fun `hardReset flushes configuration registers cleanly`() {
        val motherboard = AutumnMotherboard(MockRawNetworkClient())
        
        // Push something raw into registry
        motherboard.stringRegistry.register(0, 5)
        assertEquals(1, motherboard.stringRegistry.count)

        // Reset
        motherboard.hardReset()

        // Verify bounds cleared (arrays reused)
        assertEquals(0, motherboard.stringRegistry.count)
    }
}
