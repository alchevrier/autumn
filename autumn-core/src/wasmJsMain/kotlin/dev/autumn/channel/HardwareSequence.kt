package dev.autumn.channel

actual class HardwareSequence actual constructor(initial: Long) {
    
    // JS is single-threaded; no barriers required.
    private var underlying = initial

    actual fun getAcquire(): Long {
        return underlying
    }

    actual fun setRelease(value: Long) {
        underlying = value
    }
}
