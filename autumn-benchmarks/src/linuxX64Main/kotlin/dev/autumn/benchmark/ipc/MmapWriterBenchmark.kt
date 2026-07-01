package dev.autumn.benchmark.ipc

import dev.autumn.annotations.*
import dev.autumn.channel.AutumnChannel
import dev.autumn.memory.AutumnMemoryBank
import kotlinx.cinterop.*
import platform.posix.*

// A declarative FSM topology boundary mapped completely into cross-process Shared Memory
@LongLived
@BoundaryChannel(capacity = 2097152, weight = 100, sharded = 1)
@IpcGateway(file = "/dev/shm/autumn-market-data", access = "WRITE")
val ipcOutboundFrames = AutumnChannel<MmapFrame>(2097152)

@Pipelined
value class MmapFrame(val index: Int) {
    inline fun writeByte(offset: Int, value: Byte) {
        AutumnMemoryBank.setByte(index + offset, value)
    }
    inline fun writeLong(offset: Int, value: Long) {
        AutumnMemoryBank.setLong(index + offset, value)
    }
}

// Global reference maintained purely for the mock execution
@OptIn(ExperimentalForeignApi::class)
var mmapWriterPointer: CPointer<ByteVar>? = null

@OptIn(ExperimentalForeignApi::class)
fun bootIpcWriter() {
    // In actual production, the K2 compiler extracts the AutumnMemoryBank allocation size geometrically 
    // Here we explicitly map a 20MB block to mirror the bank's capacity for the test
    mmapWriterPointer = dev.autumn.ipc.MmapGatewayDriver.mapSharedMemory("/dev/shm/autumn-market-data", isWriter = true, 16777216L * 20L)
}

/**
 * Executes the "Hot Path" Engine natively. It writes bytes sequentially into the physical Mmap block, 
 * oblivious to the fact that another process is reading it simultaneously.
 */
@OptIn(ExperimentalForeignApi::class)
@LongLived
fun runWriterHotPathBenchmark() {
    val ptr = mmapWriterPointer ?: return
    println("🚀 [Autumn Mmap Writer] Synthesizing 5,000,000 Frames into /dev/shm natively without sockets...")

    val start = dev.autumn.scheduler.AutumnClock.now()
    var messagesSent = 0L

    while (messagesSent < 2_000_000L) {
        val partitionIdx = ipcOutboundFrames.nextMappedIndexPartition(0)
        if (partitionIdx != -1) {
            val frameIdx = partitionIdx * 9
            val frame = MmapFrame(frameIdx)
            // Payload Offset Map: (0) MsgType, (1-8) OrderId
            frame.writeByte(0, 65.toByte()) // 'A'
            frame.writeLong(1, messagesSent)
            
            // In a K2 compiled build, `AutumnMemoryBank.setByte` naturally overrides to `ptr[frameIdx] = value` natively via inline resolution.
            // For the sake of this explicit isolated benchmark, we flush the memory bank offsets directly to the CPointer.
            for (i in 0 until 9) {
                ptr[frameIdx + i] = AutumnMemoryBank.getByte(frameIdx + i)
            }
            
            ipcOutboundFrames.commitNextPartition(0)
            messagesSent++
        } else {
            // Hotpath just yields, absolutely zero kernel tracking.
            sched_yield()
        }
    }

    val nanos = dev.autumn.scheduler.AutumnClock.now() - start
    val ms = nanos / 1_000_000
    // Fix: Avoiding Kotlin String interpolation GC in strict hotpath simulation scope
    platform.posix.printf("[Autumn Mmap Writer] IPC Multicast Complete! Flushed %ld physical structs to memory map in %ldms.\\n", messagesSent, ms)
}
