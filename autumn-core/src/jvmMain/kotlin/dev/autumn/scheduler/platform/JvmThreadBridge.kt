package dev.autumn.scheduler.platform

/**
 * JVM Implementation of the OS Thread Bridge.
 * Binds directly to `Thread.onSpinWait()` and `Thread.yield()`.
 */
class JvmThreadBridge : OSThreadBridge {
    override fun yieldThread() {
        Thread.yield()
    }

    override fun pauseHardware() {
        Thread.onSpinWait() // Emits the PAUSE struct x86 hardware fence.
    }

    override fun pinToCore(coreId: Int): Boolean {
        return try {
            // Attempt to use the bare-metal Linux JNI
            dev.autumn.scheduler.NativeClock.pinToCore(coreId) == 0
        } catch (e: UnsatisfiedLinkError) {
            // Fails gracefully on Android or standard JVMs where the `.so` is not present.
            // On Android, thread assignment is generally managed via `android.os.Process.setThreadPriority`
            // rather than raw CPU pin, so we yield control dynamically.
            false
        }
    }
}
