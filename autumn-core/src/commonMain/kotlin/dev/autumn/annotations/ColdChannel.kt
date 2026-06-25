package dev.autumn.annotations

/**
 * Declares a low-priority, latency-tolerant pathway explicitly excluded from the Hot Path.
 * 
 * Used for logging, telemetry, historical persistence (e.g., NVMe io_uring), or any operation
 * that requires OS-level blocking or heavy heap allocations. The Cache-Affinity Scheduler 
 * will automatically push operations routed to a @ColdChannel onto unpinned, non-budgeted 
 * background CPU cores to protect the thermal and L1 cache boundaries of the @HotPath.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class ColdChannel(
    val capacity: Int = 1024,
    val weight: Int = 1,
    val sharded: Int = 1
)
