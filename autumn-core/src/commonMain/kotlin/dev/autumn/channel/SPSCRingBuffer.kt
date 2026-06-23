package dev.autumn.channel

/**
 * A Single-Producer Single-Consumer (SPSC) Lock-Free Ring Buffer.
 * 
 * In the Autumn Architecture, this is the explicit boundary between the `@HotPath`
 * and a `@ColdChannel`. 
 * 
 * - **Deterministic:** Fixed capacity, pre-allocated at boot.
 * - **Lock-Free:** Relies on Acquire/Release semantics via `HardwareSequence`.
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

    // Written by Producer, Read by Consumer (Using Release/Acquire semantics to avoid Cache Flushes)
    private val writeSequence = HardwareSequence(0L)
    
    // Internal fast path cached sequence to avoid hitting the actual Atomic layer 
    // unless strictly required (Wait-Free visibility optimization).
    private var cachedReadSequence: Long = 0L

    private var p10: Long = 0; private var p11: Long = 0; private var p12: Long = 0; private var p13: Long = 0
    private var p14: Long = 0; private var p15: Long = 0; private var p16: Long = 0; private var p17: Long = 0

    // Written by Consumer, Read by Producer
    private val readSequence = HardwareSequence(0L)
    private var cachedWriteSequence: Long = 0L

    // To allow `poll()` and `offer()` access to the currently tracked raw value
    // internally without performing volatile flushes during the active execution phase.
    private var internalUnreleasedWrite: Long = 0L
    private var internalUnreleasedRead: Long = 0L

    /**
     * Poll-mode non-blocking offer.
     * Returns the allocated index in the buffer if successful, or -1 if full.
     */
    fun offer(): Int {
        val currentWrite = internalUnreleasedWrite
        
        // Fast path: Check local cached read sequence first
        if (currentWrite - cachedReadSequence >= capacity) {
            // Memory barrier: Load the actual read sequence with Acquire semantics
            cachedReadSequence = readSequence.getAcquire()
            
            if (currentWrite - cachedReadSequence >= capacity) {
                return -1 // Still full, backpressure!
            }
        }
        
        return (currentWrite.toInt() and mask)
    }

    /**
     * Commits the offered element up to this sequence.
     * Employs Release semantics to ensure data written before this point is visible to the Consumer.
     */
    fun commitOffer() {
        val nextWrite = internalUnreleasedWrite + 1
        internalUnreleasedWrite = nextWrite
        writeSequence.setRelease(nextWrite) 
    }

    /**
     * Poll-mode non-blocking consume.
     * Returns the target index to read from, or -1 if empty.
     */
    fun poll(): Int {
        val currentRead = internalUnreleasedRead
        
        if (currentRead >= cachedWriteSequence) {
            // Memory barrier: Load the actual write sequence with Acquire semantics
            cachedWriteSequence = writeSequence.getAcquire()
            
            if (currentRead >= cachedWriteSequence) {
                return -1 // Still empty!
            }
        }
        
        return (currentRead.toInt() and mask)
    }

    /**
     * Acknowledges the index was read, freeing it for the producer.
     * Employs Release semantics.
     */
    fun commitPoll() {
        val nextRead = internalUnreleasedRead + 1
        internalUnreleasedRead = nextRead
        readSequence.setRelease(nextRead)
    }
}
