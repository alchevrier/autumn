package dev.autumn.scheduler

/**
 * Defines the thermal and CPU execution aggressiveness for the [HardwareOscillator].
 * In HFT scenarios, different phases of the trading day require different hardware states.
 */
enum class PowerMode {
    /**
     * **Peak Performance.**
     * Emits `PAUSE` hardware instructions.
     * Prevents the CPU from entering low-power C-states. Zero-latency wake.
     * Used during active trading hours / expected burst periods.
     */
    ACTIVE,
    
    /**
     * **Slight Power Savings.**
     * Yields the thread's current time slice to the OS scheduler.
     * Drops latency to microseconds, reducing thermal load and CPU frequency decay 
     * before major bursts are anticipated.
     */
    YIELDING,
    
    /**
     * **Deep Sleep.**
     * Fully suspends the physical thread (parks).
     * Millisecond latency wakes. Extremely low power.
     * Used during after-hours, maintenance, or when the exchange is closed.
     */
    SLEEPING
}
