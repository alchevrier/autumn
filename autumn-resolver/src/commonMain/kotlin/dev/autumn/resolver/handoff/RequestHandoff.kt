package dev.autumn.resolver.handoff

/**
 * Defines the "Fire and Forget In-Place" boundary.
 * 
 * Instead of lifting network responses into heap-allocated Domain Transfer Objects (DTOs),
 * this coordinates the async OS network stack directly with pre-allocated application slots.
 */
interface RequestHandoff {

    /**
     * Reserves an available slot in the pre-allocated memory budget.
     * 
     * If all slots are currently consumed by active rendering or in-flight requests, 
     * this fails instantly. This acts as a natural, zero-allocation Circuit Breaker 
     * preventing network cascades.
     * 
     * @return The integer index of the slot, or -1 if the budget is exhausted.
     */
    fun claimSlot(): Int

    /**
     * Releases a slot back to the pool (e.g., on network timeout, error, or UI eviction).
     */
    fun releaseSlot(slotIndex: Int)

    /**
     * Hands off IO to the OS network stack and suspends.
     * 
     * When the OS resumes with the network payload (ByteArray), it is NOT mapped 
     * into objects. Instead, a zero-allocation parser is invoked over the byte buffer, 
     * writing dimension coordinates and states directly into the static matrices at [slotIndex].
     *
     * @param slotIndex The claimed slot where the hardware-sympathetic matrices should be updated.
     * @param endpoint The resource to fetch.
     * @return A Result indicating only success or failure. The data is already in place.
     */
    suspend fun executeInPlace(slotIndex: Int, endpoint: String): Result<Unit>
}
