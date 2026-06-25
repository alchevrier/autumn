package dev.autumn.channel

/**
 * A directional data wire in the Autumn Architecture.
 * 
 * Driven purely by temporality on the Arbiter's clock. This avoids standard 
 * synchronization mechanics (no Seqlocks, no volatile barriers, no cache padding)
 * because the dataflow schedule logically guarantees array visibility.
 * 
 * - **Deterministic:** Fixed capacity, pre-allocated at boot.
 * - **Unsynchronized:** Driven by purely sequential event loops avoiding cross-core chatter.
 * - **HLS Analogy:** A hardware FIFO connecting bounded clock domains.
 */
class Channel(val capacity: Int) {

    init {
        require(capacity > 0 && (capacity and (capacity - 1)) == 0) {
            "Channel capacity must be a power of 2 for fast modulo arithmetic."
        }
    }

    private val mask = capacity - 1

    // Pure sequential indices, stripped of hardware locks and padding
    // because execution is temporally synchronized by the Arbiter's schedule.
    private var writeSequence: Long = 0L
    private var readSequence: Long = 0L

    /**
     * Statically injected by the Autumn K2 Compiler to globally align this buffer's indices
     * into the unified System MemoryBank for its pooled Structure of Arrays (SoA).
     */
    var globalIndexOffset: Int = 0

    /**
     * Poll-mode non-blocking offer.
     * Returns the allocated index in the buffer if successful, or -1 if full.
     */
    fun offer(): Int {
        if (writeSequence - readSequence >= capacity) {
            return -1 // Full, backpressure!
        }
        
        // Return globally mapped index to support wait-free hardware bounded pointers
        return (writeSequence.toInt() and mask) + globalIndexOffset
    }

    /**
     * Commits the offered element up to this sequence.
     */
    fun commitOffer() {
        writeSequence++
    }

    /**
     * Poll-mode non-blocking consume.
     * Returns the target index to read from, or -1 if empty.
     */
    fun poll(): Int {
        if (readSequence >= writeSequence) {
            return -1 // Empty!
        }
        
        // Return globally mapped index to support wait-free hardware bounded pointers
        return (readSequence.toInt() and mask) + globalIndexOffset
    }

    /**
     * Acknowledges the index was read, freeing it for the producer.
     */
    fun commitPoll() {
        readSequence++
    }
}
