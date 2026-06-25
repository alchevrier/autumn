package dev.autumn.scheduler.platform

/**
 * A platform-agnostic bridge to raw OS thread execution logic.
 * The implementation injected depends on the compilation target 
 * (Kotlin Native LLVM, Kotlin/JVM, or Kotlin/JS).
 */
interface OSThreadBridge {
    /**
     * Attempts to yield the current thread back to the OS scheduler.
     * Called by COLD_SHARED Dispatchers when the dataflow pipeline is completely empty.
     */
    fun yieldThread()

    /**
     * Applies a physical CPU hint to prevent pipeline branch predictor saturation.
     * Equivalent to `_mm_pause()` in x86 C++. 
     * Called continuously by HOT_ISOLATED Dispatchers in an empty spin loop.
     */
    fun pauseHardware()

    /**
     * Applies strict UNIX scheduler affinity to the active OS thread.
     * @return true if successful or natively implemented, false if on a managed VM (like Android/Web)
     */
    fun pinToCore(coreId: Int): Boolean
}
