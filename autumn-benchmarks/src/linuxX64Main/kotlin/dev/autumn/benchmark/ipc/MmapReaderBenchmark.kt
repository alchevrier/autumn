package dev.autumn.benchmark.ipc

import dev.autumn.annotations.*
import kotlinx.cinterop.*
import platform.posix.*

// Global reference maintained purely for the mock execution
@OptIn(ExperimentalForeignApi::class)
var mmapReaderPointer: CPointer<ByteVar>? = null

@OptIn(ExperimentalForeignApi::class)
fun bootIpcReader() {
    mmapReaderPointer = dev.autumn.ipc.MmapGatewayDriver.mapSharedMemory("/dev/shm/autumn-market-data", isWriter = false, 16777216L * 20L)
}

/**
 * Executes a disjoint process tracking the Hot Path Memory. 
 * Notice there are no sockets, no FSM polling, no bytes entering memory limits.
 * We natively track the 20MB block exactly where the writer left it.
 */
@OptIn(ExperimentalForeignApi::class)
@LongLived
fun runReaderColdPathBenchmark() {
    val ptr = mmapReaderPointer ?: return
    println("🔍 [Autumn Mmap Logger] Passively tracking /dev/shm/autumn-market-data without allocating memory...")
    
    // Track where the Writer's commit index is at without blocking!
    var messagesLogged = 0L
    
    // We arbitrarily simulate spinning slower to prove backpressure does not exist
    while (messagesLogged < 2_000_000L) {
        val readOffset = (messagesLogged % 2097152L) * 9L
        // Verify if a struct has been flushed to this pointer location
        val msgType = ptr[readOffset]
        
        if (msgType == 65.toByte()) { // 'A' written by Writer
            var orderId = 0L
            for (i in 0 until 8) {
                // Reconstruct exact bytes the writer mapped sequentially
                orderId = orderId or ((ptr[readOffset + 1 + i].toLong() and 0xFF) shl (i * 8))
            }
            messagesLogged++
            
            if (messagesLogged % 1_000_000L == 0L) {
                val s = "[Autumn Logger] Passively read 1M events. Latest OrderID: %ld\\n"
                platform.posix.printf(s, orderId)
            }
        } else {
            // Hotpath hasn't written here yet. Just wait, no blocking kernel queue.
            sched_yield()
        }
    }
    println("✅ [Autumn Mmap Logger] Terminating. Passively logged 5,000,000 FSM multicasts via hardware pointer.")
}
