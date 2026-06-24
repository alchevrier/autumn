package dev.autumn.scheduler

/**
 * The physical clock generator for the Autumn architecture.
 * Drives the deterministic `tick()` on the provided [AutumnScheduler].
 * 
 * In a true Time-Triggered Architecture (TTA), this component takes 
 * exclusive ownership of a physical CPU core, pinning itself, and running
 * an unyielding loop to generate clock cycles without OS interruption.
 */
expect class HardwareOscillator(scheduler: AutumnScheduler) {
    
    /**
     * Commences the physical Clock Tick loop.
     * @param coreId The physical OS core (isolcpus) to pin to. 
     */
    fun start(coreId: Int = -1)
    
    /**
     * Halts the hardware oscillator execution.
     */
    fun stop()
}
