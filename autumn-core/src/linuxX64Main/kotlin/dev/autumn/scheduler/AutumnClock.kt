package dev.autumn.scheduler

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Linux X64 implementation using POSIX monotonic clock with zero allocations
 * by caching a single timespec structure dynamically natively.
 */
@OptIn(ExperimentalForeignApi::class)
actual object AutumnClock {
    private val ts = nativeHeap.alloc<timespec>()

    actual fun now(): Long {
        clock_gettime(CLOCK_MONOTONIC, ts.ptr)
        return (ts.tv_sec * 1_000_000_000L) + ts.tv_nsec
    }
}