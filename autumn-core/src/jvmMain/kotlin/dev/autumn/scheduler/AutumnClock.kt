package dev.autumn.scheduler

/**
 * JVM Implementation defaults to the NativeClock RDTSC hardware binding
 * or gracefully falls back to System.nanoTime() without allocating.
 */
actual object AutumnClock {
    actual fun now(): Long {
        return try {
            NativeClock.rdtsc()
        } catch (e: Throwable) {
            System.nanoTime()
        }
    }
}
