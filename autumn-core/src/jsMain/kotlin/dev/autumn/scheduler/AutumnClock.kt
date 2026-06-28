package dev.autumn.scheduler

private fun getPerformanceNow(): Double = js("window.performance.now()")

/**
 * JS implementation binding non-allocating JS DOM performance timer 
 * converted to nanosecond-equivalent offsets.
 */
actual object AutumnClock {
    actual fun now(): Long {
        val ms = getPerformanceNow()
        return (ms * 1_000_000.0).toLong()
    }
}