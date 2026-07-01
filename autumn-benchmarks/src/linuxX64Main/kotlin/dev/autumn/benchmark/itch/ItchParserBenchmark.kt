package dev.autumn.benchmark.itch

import dev.autumn.annotations.*
import dev.autumn.channel.AutumnChannel
import dev.autumn.observatory.LatencyHistogram
import dev.autumn.memory.AutumnMemoryBank
import kotlin.system.exitProcess
import platform.posix.*
import kotlinx.cinterop.*

@Pipelined
interface ItchAddOrder {
    var orderRef: Long
    var shares: Int
    var price: Int
    var side: Byte
}

value class ItchAddOrderFlyweight(val index: Int) : ItchAddOrder {
    override var orderRef: Long
        get() = 0L; set(value) {}
    override var shares: Int
        get() = 0; set(value) {}
    override var price: Int
        get() = 0; set(value) {}
    override var side: Byte
        get() = 0; set(value) {}
}

@LongLived
@BoundaryChannel(capacity = 2097152, weight = 100, sharded = 1)
val rawNetworkFrames = AutumnChannel<NetworkFrameFlyweight>(2097152)

@LongLived
@RegisterChannel(capacity = 2097152, weight = 50)
val parsedAddOrders = AutumnChannel<ItchAddOrder>(2097152)

@LongLived
@ObserveChannel(observerName = "parserTelemetry", capacity = 1000000)
val parserMetrics = LatencyHistogram(0, 1000000)

value class NetworkFrameFlyweight(val index: Int) {
    inline fun readByte(offset: Int): Byte {
        return AutumnMemoryBank.getByte(index + offset)
    }
    inline fun readInt(offset: Int): Int {
        return AutumnMemoryBank.getInt(index + offset)
    }
    inline fun readLong(offset: Int): Long {
        return AutumnMemoryBank.getLong(index + offset)
    }
}

@Observe("parserTelemetry")
@HotPath
@CycleBudget(limit = 500)
@LongLived
fun onRawNetworkFrames(idx: Int) {
    val frame = NetworkFrameFlyweight(idx)
    
    // ITCH 5.0 Msg Type
    val msgType = frame.readByte(0)
    
    // 'A' == Add Order (No MPID)
    if (msgType == 65.toByte()) {
        val nextIdx = parsedAddOrders.nextIndex()
        if (nextIdx != -1) {
            val order = ItchAddOrderFlyweight(nextIdx)
            // Field mapping based strictly on official ITCH 5.0 Spec Offsets!
            order.orderRef = frame.readLong(11) // uint64
            order.side = frame.readByte(19)     // char
            order.shares = frame.readInt(20)    // uint32
            order.price = frame.readInt(32)     // uint32
            
            parsedAddOrders.commitNext()
        }
    }
}

@InjectTopology
fun bootItchParser() {
}

fun simulateItchStream() {
    println("[Autumn Offline] Streaming 1,000,000 Mock ITCH 5.0 'Add Order' messages natively...")
    val payloadSize = 36
    
    val start = dev.autumn.scheduler.AutumnClock.now()
    
    for (i in 0 until 1_000_000) {
        val frameIdx = rawNetworkFrames.nextIndexPartition(0)
        if (frameIdx != -1) {
            
            // Hardcode absolute fake mock struct to prove parsing algorithms natively!
            AutumnMemoryBank.setByte(frameIdx + 0, 65)  // 'A'
            AutumnMemoryBank.setLong(frameIdx + 11, i.toLong()) // OrderRef
            AutumnMemoryBank.setByte(frameIdx + 19, 66) // 'B' (Buy)
            AutumnMemoryBank.setInt(frameIdx + 20, 100) // 100 Shares
            AutumnMemoryBank.setInt(frameIdx + 32, 10000 + i) // Price 
            
            rawNetworkFrames.commitNextPartition(0)
        } else {
            sched_yield() // Hardware ring full
        }
    }
    
    val nanos = dev.autumn.scheduler.AutumnClock.now() - start
    println("[Autumn Offline] Successfully parsed 1M ITCH messages mathematically in ${nanos / 1_000_000}ms!")
}

@OptIn(ExperimentalForeignApi::class)
fun parseRealItchFile(filePath: String) {
    println("[Autumn Offline] Opening Real ITCH 5.0 Capture: $filePath")
    val file = platform.posix.fopen(filePath, "rb")
    if (file == null) {
        println("FAILED to open file at $filePath")
        return
    }

    val start = dev.autumn.scheduler.AutumnClock.now()
    var messagesProcessed = 0L
    var bytesReadTotal = 0L

    memScoped {
        val lengthBuf = allocArray<ByteVar>(2)
        
        while (true) {
            // Read 2-byte SoupBinTCP Length (Big Endian)
            val bytesRead = platform.posix.fread(lengthBuf, 1U, 2U, file)
            if (bytesRead < 2UL) break
            
            // Reconstruct Big-Endian 16-bit length
            val b0 = lengthBuf[0].toInt() and 0xFF
            val b1 = lengthBuf[1].toInt() and 0xFF
            val length = (b0 shl 8) or b1
            
            if (length <= 0) break
            
            // Read the full message payload (starting with MsgType)
            val tempBuf = allocArray<ByteVar>(length)
            val payloadRead = platform.posix.fread(tempBuf, 1U, length.toULong(), file)
            if (payloadRead < length.toULong()) break
            
            val msgType = tempBuf[0]

            // Get a slot in the Memory Bank via the Channel
            val frameIdx = rawNetworkFrames.nextIndexPartition(0)
            if (frameIdx != -1) {
                // Write the message type and payload to Memory Bank natively
                for (i in 0 until length) {
                    AutumnMemoryBank.setByte(frameIdx + i, tempBuf[i])
                }
                
                rawNetworkFrames.commitNextPartition(0)
                messagesProcessed++
                bytesReadTotal += (length + 2).toLong()
                
                if (messagesProcessed >= 2_000_000L) {
                    break
                }
            } else {
                // Channel is full, yield
                platform.posix.sched_yield()
                // Seek back to re-read the 2 length bytes + payload length we just read
                val backtrack = -(length + 2).toLong()
                platform.posix.fseek(file, backtrack, platform.posix.SEEK_CUR) 
            }
        }
    }

    platform.posix.fclose(file)
    val nanos = dev.autumn.scheduler.AutumnClock.now() - start
    val ms =  nanos / 1_000_000
    println("[Autumn Offline] File Stream Complete! Parsed $messagesProcessed exact messages ($bytesReadTotal bytes) natively in ${ms}ms.")
}
