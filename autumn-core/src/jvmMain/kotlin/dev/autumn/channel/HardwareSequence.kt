package dev.autumn.channel

import java.util.concurrent.atomic.AtomicLong

actual class HardwareSequence actual constructor(initial: Long) {
    
    // Boot-time allocation is zero-cost at runtime
    private val underlying = AtomicLong(initial)

    actual fun getAcquire(): Long {
        // Standard AtomicLong get() translates to a volatile read establishing Acquire semantics
        return underlying.get()
    }

    actual fun setRelease(value: Long) {
        // lazySet translates to a StoreStore barrier (Release semantics) without a StoreLoad cache flush.
        underlying.lazySet(value)
    }
}
