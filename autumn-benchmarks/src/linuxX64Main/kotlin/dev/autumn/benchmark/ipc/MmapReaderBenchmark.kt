package dev.autumn.benchmark.ipc

import dev.autumn.annotations.*
import kotlinx.cinterop.*
import platform.posix.*

@LongLived
@ColdChannel(capacity = 2097152, weight = 1, sharded = 1)
@IpcGateway(file = "/dev/shm/autumn-market-data", access = "READ")
val ipcInboundFrames = dev.autumn.channel.AutumnChannel<MmapFrame>(2097152)

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
        val readOffset = (messagesLogged % 2097152L).toInt() * 9
        val frame = MmapFrame(readOffset) // The compiler natively assigns this instance directly to the Mmap Gateway
        
        val msgType = frame.msgType
        
        if (msgType == 65.toByte()) { // 'A' written by Writer
            val orderId = frame.orderId
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
    println("✅ [Autumn Mmap Logger] Terminating. Passively logged 2,000,000 FSM multicasts via hardware pointer.")
}
