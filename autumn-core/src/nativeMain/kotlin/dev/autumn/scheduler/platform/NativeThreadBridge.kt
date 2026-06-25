package dev.autumn.scheduler.platform

import platform.posix.pthread_self
import platform.posix.sched_yield

// The exact C-Interop bindings mapped from autumn_os.def
import dev.autumn.os.autumn_pause
import dev.autumn.os.autumn_pin_to_core
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * Kotlin Native Implementation of the OS Thread Bridge.
 * Directly taps into bare-metal POSIX C library functions, and falls back to
 * Autumn's explicitly compiled C-Interop headers to bridge non-portable Linux APIs 
 * like `pthread_setaffinity_np` and `__builtin_ia32_pause()`.
 */
@OptIn(ExperimentalForeignApi::class)
class NativeThreadBridge : OSThreadBridge {
    
    override fun yieldThread() {
        // Explicit POSIX yield to the OS thread scheduler
        sched_yield() 
    }

    override fun pauseHardware() {
        autumn_pause()
    }

    override fun pinToCore(coreId: Int): Boolean {
        // Uses the explicit Linux-native C function mapped via cinterop
        // int autumn_pin_to_core(int core_id);
        return autumn_pin_to_core(coreId) == 0
    }
}
