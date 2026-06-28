package dev.autumn.scheduler

/**
 * A true zero-allocation multiplatform clock for exact latency telemetry.
 */
expect object AutumnClock {
    /**
     * Returns the current time in monotonically increasing nanoseconds.
     */
    fun now(): Long
}
