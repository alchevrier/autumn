package dev.autumn.benchmark.itch

import dev.autumn.annotations.*
import dev.autumn.channel.AutumnChannel
import dev.autumn.memory.AutumnMemoryBank
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

@Pipelined
@JvmInline
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

@LongLived
@BoundaryChannel(capacity = 2097152, weight = 100, sharded = 1)
@UdpGateway(port = 8000, host = "127.0.0.1")
val jvmCloudNetworkFrames = AutumnChannel<NetworkFrameFlyweight>(2097152)

@LongLived
@ObserveChannel(observerName = "jvmCloudUdpTelemetry", capacity = 1000000)
val jvmCloudUdpMetrics = dev.autumn.observatory.LatencyHistogram(0, 1000000)

var jvmUdpChannel: DatagramChannel? = null
var start = 0L
var messagesProcessed = 0L
val maxMessages = 2_000_000L

val udpRecvBuf = ByteBuffer.allocateDirect(2048)

@Observe("jvmCloudUdpTelemetry")
@HotPath
@CycleBudget(limit = 1200)
@LongLived
fun pollJvmUdpGateway() {
    val channel = jvmUdpChannel ?: return
    
    udpRecvBuf.clear()
    val senderAddr = channel.receive(udpRecvBuf)
    
    if (senderAddr != null) {
        val bytesRead = udpRecvBuf.position()
        if (bytesRead > 0) {
            if (start == 0L) start = dev.autumn.scheduler.AutumnClock.now()
            
            val frameIdx = jvmCloudNetworkFrames.nextIndexPartition(0)
            if (frameIdx != -1) {
                // JVM zero-allocation: direct buffer into AutumnMemoryBank
                for (i in 0 until bytesRead) {
                    AutumnMemoryBank.setByte(frameIdx + i, udpRecvBuf.get(i))
                }
                jvmCloudNetworkFrames.commitNextPartition(0)
                messagesProcessed++
            } else {
                // Channel full, backpressure handled locally. No blocking.
            }
        }
    }

    if (messagesProcessed >= maxMessages) {
        val nanos = dev.autumn.scheduler.AutumnClock.now() - start
        val ms = nanos / 1_000_000
        println("[Autumn JVM Cloud-Tier] UDP Stream Complete! Parsed $messagesProcessed exact messages in java.nio in ${ms}ms.")
        channel.close()
        jvmUdpChannel = null
        messagesProcessed = 0 // Reset to avoid re-trigger
    }
}

fun bootJvmUdpServer(port: Int, host: String = "127.0.0.1") {
    jvmUdpChannel = dev.autumn.udp.UdpGatewayDriver.bindNonBlockingSocket(port, host)
}
