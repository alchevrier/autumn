package dev.autumn.scheduler

/**
 * Represents a single combinational logic block or polling boundary 
 * bound to an expected number of cycles.
 */
data class ClockAwareOperation(
    val expectedCycles: Long,
    val isNetworkIngress: Boolean,
    val executable: () -> Unit
)
