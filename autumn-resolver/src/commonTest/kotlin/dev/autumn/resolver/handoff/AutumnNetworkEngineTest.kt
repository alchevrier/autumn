package dev.autumn.resolver.handoff

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import dev.autumn.annotations.LongLived

@LongLived
class AutumnNetworkEngineTest {

    // Concrete mock for testing the boundary
    class MockRawNetworkClient : RawNetworkClient {
        var simulateFailure = false
        var lastEndpoint = ""
        var lastMethod = ""
        var lastBody: ByteArray? = null
        
        // This is what the hardware NIC/socket returns to us 
        val cannedResponse = byteArrayOf(1, 2, 3)

        override suspend fun executeRaw(
            endpoint: String,
            method: String,
            requestBody: ByteArray?
        ): Result<ByteArray> {
            lastEndpoint = endpoint
            lastMethod = method
            lastBody = requestBody
            
            return if (simulateFailure) {
                Result.failure(Exception("Socket timeout"))
            } else {
                Result.success(cannedResponse)
            }
        }
    }

    @Test
    fun `engine handles successful handoff without allocations`() = runTest {
        val slotManager = NetworkSlotManager(2)
        val networkClient = MockRawNetworkClient()
        
        var parsedBytes: ByteArray? = null
        val engine = AutumnNetworkEngine(
            slotManager = slotManager,
            networkClient = networkClient,
            payloadMatrixWriter = { bytes ->
                parsedBytes = bytes // intercepts the payload
            }
        )

        // 1. App claims the slot before doing anything
        val slotIndex = engine.claimSlot()
        assertEquals(0, slotIndex, "First slot should be claimed")
        assertEquals(1, slotManager.activeInFlightCount())

        // 2. Execute the async network call
        val requestBody = "json_payload".encodeToByteArray()
        val result = engine.executeInPlace(
            slotIndex = slotIndex,
            endpoint = "/v1/submit",
            method = "POST",
            requestBody = requestBody
        )

        // 3. Verify success and boundary invariants
        assertTrue(result.isSuccess, "Request should succeed")
        
        assertEquals("/v1/submit", networkClient.lastEndpoint)
        assertEquals("POST", networkClient.lastMethod)
        assertEquals(requestBody, networkClient.lastBody, "Request body bytes should be sent to the socket layer")
        
        assertEquals(networkClient.cannedResponse, parsedBytes, "Received bytes should flow strictly to the parser callback")
        
        // 4. Slot should be automatically freed by the engine when done!
        assertEquals(0, slotManager.activeInFlightCount(), "Slot should be released by the finally block")
    }

    @Test
    fun `engine handles OS network failures and reclaims slots correctly`() = runTest {
        val slotManager = NetworkSlotManager(2)
        val networkClient = MockRawNetworkClient().apply {
            simulateFailure = true
        }
        
        val engine = AutumnNetworkEngine(
            slotManager = slotManager,
            networkClient = networkClient,
            payloadMatrixWriter = { 
                throw IllegalStateException("Should not parse failure")
            }
        )

        val slotIndex = engine.claimSlot()
        assertEquals(1, slotManager.activeInFlightCount())

        val result = engine.executeInPlace(slotIndex, "/v1/data")
        
        assertTrue(result.isFailure, "Result should reflect the socket timeout")
        assertEquals(0, slotManager.activeInFlightCount(), "Slot must be freed even on network failure")
    }

    @Test
    fun `engine instantly circuit breaks when queuing too many requests`() = runTest {
        // Strict budget of exactly 2 concurrent network requests
        val slotManager = NetworkSlotManager(2)
        val networkClient = MockRawNetworkClient()
        
        val engine = AutumnNetworkEngine(
            slotManager = slotManager,
            networkClient = networkClient,
            payloadMatrixWriter = {}
        )

        // 1. Thread 1 claims the first slot
        val slot1 = engine.claimSlot()
        assertEquals(0, slot1)

        // 2. Thread 2 claims the second slot
        val slot2 = engine.claimSlot()
        assertEquals(1, slot2)

        // 3. Thread 3 attempts to claim a slot but the budget is exhausted!
        // No waiting, no queueing array, no blocking — just instant failure.
        val slot3 = engine.claimSlot()
        assertEquals(-1, slot3, "Must instantly circuit break when budget is exhausted")

        // 4. Verify telemetry still strictly reports 2
        assertEquals(2, slotManager.activeInFlightCount())

        // 5. When Thread 1 finishes, its slot becomes available again
        engine.releaseSlot(slot1)
        
        // 6. Thread 4 tries again and succeeds, reusing slot 0
        val slot4 = engine.claimSlot()
        assertEquals(0, slot4, "Freed slot must be immediately reusable")
    }
}
