package dev.autumn.resolver.handoff

/**
 * The concrete wiring of the OS Network Boundary to the Autumn Circuit-Based runtime.
 * 
 * This fulfills [RequestHandoff] by enforcing the memory budget statically and 
 * resolving the raw payload directly into pre-allocated memory matrices without DTOs.
 */
class AutumnNetworkEngine(
    private val slotManager: NetworkSlotManager,
    private val networkClient: RawNetworkClient,
    // Dependency-inverted callback to the Config module where the zero-allocation parser lives
    private val payloadMatrixWriter: (ByteArray) -> Unit
) : RequestHandoff {

    override fun claimSlot(): Int {
        return slotManager.claimSlot()
    }

    override fun releaseSlot(slotIndex: Int) {
        slotManager.releaseSlot(slotIndex)
    }

    override suspend fun executeInPlace(
        slotIndex: Int, 
        endpoint: String, 
        method: String, 
        requestBody: ByteArray?
    ): Result<Unit> {
        // 1. Send/Fetch raw bytes to/from the OS Socket layer
        val requestResult = networkClient.executeRaw(endpoint, method, requestBody)

        return requestResult.fold(
            onSuccess = { bytes ->
                try {
                    // 2. The critical handoff: execute zero-allocation callback to write 
                    // dimensions exactly into the memory boundary. No new DTOs.
                    payloadMatrixWriter(bytes)
                    
                    // 3. Mark the slot as free immediately after parsing completion
                    releaseSlot(slotIndex)
                    Result.success(Unit)
                } catch (e: Exception) {
                    releaseSlot(slotIndex)
                    Result.failure(e)
                }
            },
            onFailure = { error ->
                // Circuit Breaker / Network Error
                releaseSlot(slotIndex)
                Result.failure(error)
            }
        )
    }
}
