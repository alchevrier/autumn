package dev.autumn.scheduler

/**
 * Classifies the hardware execution profile of an AutumnScheduler (Arbiter).
 * 
 * Maps directly to ADR-0020: Channel-Driven Dataflow Execution.
 */
public enum class DispatcherProfile {
    /**
     * Hot Arbiters: 
     * - Pinned to isolated physical cores.
     * - Spin-wait at 100% CPU utilization.
     * - Never yield to the OS.
     * - Execute the latency-critical pipelines (e.g., Network, Matching Engine).
     */
    HOT_ISOLATED,

    /**
     * Cold Arbiters:
     * - Running on shared/unisolated OS cores.
     * - Allowed to park, sleep, or yield to the OS using locks/condvars.
     * - Handle non-critical paths like UI dispatching, disk I/O, or background telemetry.
     */
    COLD_SHARED
}