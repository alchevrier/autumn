package dev.autumn.scheduler

/**
 * Bridges the physical hardware Time Stamp Counter (TSC).
 * By querying the hardware directly via JNI, we completely bypass 
 * virtualization traps and kernel-space boundary context switching.
 */
object NativeClock {
    init {
        try {
            // JVM will look for libautumn_clock.so on the java.library.path
            System.loadLibrary("autumn_clock")
        } catch (e: UnsatisfiedLinkError) {
            println("[Autumn OS] WARNING: autumn_clock native library not found. Falling back to System.nanoTime().")
        }
    }
    
    /**
     * Executes the inline Assembly `rdtsc` instruction.
     */
    @JvmStatic
    external fun rdtsc(): Long

    /**
     * Reaches into the OS via `pthread_setaffinity_np` to explicitly lock 
     * this Thread (the Oscillator loop) down to the specified core.
     */
    @JvmStatic
    external fun pinToCore(coreId: Int): Int
}
