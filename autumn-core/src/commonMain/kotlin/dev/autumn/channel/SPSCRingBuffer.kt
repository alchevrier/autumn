package dev.autumn.channel

/**
 * A Single-Producer Single-Consumer (SPSC) Lock-Free Ring Buffer.
 * 
 * In the Autumn Architecture, this is the explicit boundary between the `@HotPath`
 * and a `@ColdChannel`. 
 * 
 * - **Deterministic:** Fixed capacity, pre-allocated at boot.
 * - **Lock-Free:** Relies purely on `AtomicLong` (cache-coherent visibility) and sequence numbers.
 * - **False-Sharing Resilient:** Head and Tail sequence numbers pad cache lines (assuming user enforces memory layout or `@ThreadCacheBudget`).
 * - **HLS Analogy:** A hardware FIFO connecting two independent clock domains.
 */
class SPSCRingBuffer(val capacity: Int) {

    init {
        require(capacity > 0 && (capacity and (capacity - 1)) == 0) {
            "SPSCRingBuffer capacity must be a power of 2 for fast modulo arithmetic."
        }
    }

    private val mask = capacity - 1

    // Pseudo-cache line padding for JVM (Requires standard layout spacing or native integration)
    // 64 bytes of padding to avoid false sharing on CPUs.
    private var p0: Long = 0; private var p1: Long = 0; private var p2: Long = 0; private var p3: Long = 0
    private var p4: Long = 0; private var p5: Long = 0; private var p6: Long = 0; private var p7: Long = 0

    // Conceptually Atomics, simplified for pure compilation model. 
    // Uses intrinsic memory ordering or `AtomicLong` depending on actual target.
    // In Kotlin Native K2, standard var + atomic operations are used.
    
    // Written by Producer, Read by Consumer
    @Volatile
    private var writeSequence: Long = 0L

    private var p10: Long = 0; private var p11: Long = 0; private var p12: Long = 0; private var p13: Long = 0
    private var p14: Long = 0; private var p15: Long = 0; private var p16: Long = 0; private var p17: Long = 0

    // Written by Consumer, Read by Producer
    @Volatile
    private var readSequence: Long = 0L

    /**
     * Poll-mode non-blocking offer.
     * Returns the allocated index in the buffer if successful, or -1 if full.
     */
    fun offer(): Int {
        val currentWrite = writeSequence
        val currentRead = readSequence // Volatile read
        
        // Is the buffer full?
        if (currentWrite - currentRead >= capacity) {
            return -1 // Backpressure handled by the app FSM
        }
        
        return (currentWrite.toInt() and mask)
    }

    /**
     * Commits the offered element up to this sequence.
     */
    fun commitOffer() {
        writeSequence += 1 // Volatile write publishes the change
    }

    /**
     * Poll-mode non-blocking consume.
     * Returns the target index to read from, or -1 if empty.
     */
    fun poll(): Int {
        val currentRead = readSequence
        val currentWrite = writeSequence // Volatile read
        
        // Is the buffer empty?
        if (currentRead >= currentWrite) {
            return -1
        }
        
        return (currentRead.toInt() and mask)
    }

    /**
     * Acknowledges the index was read, freeing it for the producer.
     */
    fun commitPoll() {
        readSequence += 1 // Volatile write
    }
}
