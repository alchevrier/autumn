package dev.autumn.scheduler

/**
 * Wasm implementation binding non-allocating JS DOM performance timer 
 * converted to nanosecond-equivalent offsets.
 */
actual object AutumnClock {
    actual fun now(): Long {
        val ms = js("window.performance.now()") as Double
        return (ms * 1_000_000.0).toLong()
    }
}
