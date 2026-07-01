package dev.autumn.benchmark.itch

import dev.autumn.annotations.*
import dev.autumn.channel.AutumnChannel
import dev.autumn.memory.AutumnMemoryBank
import kotlinx.cinterop.*
import platform.posix.*

@LongLived
@BoundaryChannel(capacity = 2097152, weight = 100, sharded = 1)
@UdpGateway(port = 8000)
val cloudNetworkFrames = AutumnChannel<NetworkFrameFlyweight>(2097152)

@LongLived
@ObserveChannel(observerName = "cloudUdpTelemetry", capacity = 1000000)
val cloudUdpMetrics = dev.autumn.observatory.LatencyHistogram(0, 1000000)

var udpSockFd = -1
var start = 0L
var messagesProcessed = 0L
val maxMessages = 2_000_000L

@OptIn(ExperimentalForeignApi::class)
val udpRecvBuf = nativeHeap.allocArray<ByteVar>(2048)

@Observe("cloudUdpTelemetry")
@HotPath
@CycleBudget(limit = 1200)
@LongLived
@OptIn(ExperimentalForeignApi::class)
fun pollUdpGateway() {
    if (udpSockFd == -1) return
    
    val bytesRead = recvfrom(udpSockFd, udpRecvBuf, 2048U, 0, null, null)
    
    if (bytesRead > 0) {
        if (start == 0L) start = dev.autumn.scheduler.AutumnClock.now()
        
        val frameIdx = cloudNetworkFrames.nextIndexPartition(0)
        if (frameIdx != -1) {
            for (i in 0 until bytesRead.toInt()) {
                AutumnMemoryBank.setByte(frameIdx + i, udpRecvBuf[i])
            }
            cloudNetworkFrames.commitNextPartition(0)
            messagesProcessed++
        }
    } else if (bytesRead < 0 && (errno != EAGAIN && errno != EWOULDBLOCK)) {
        println("Socket error: ${strerror(errno)}")
        udpSockFd = -1
    }

    if (messagesProcessed >= maxMessages) {
        val nanos = dev.autumn.scheduler.AutumnClock.now() - start
        val ms = nanos / 1_000_000
        println("[Autumn Cloud-Tier] UDP Stream Complete! Parsed $messagesProcessed exact messages natively in ${ms}ms.")
        close(udpSockFd)
        udpSockFd = -1
        messagesProcessed = 0 // Reset to avoid re-trigger
    }
}

/**
 * Phase 5.5: Pluggable Layered Boundary Transports (ADR-0030)
 * 
 * We reuse the EXACT same topology logic through standard Cloud-Native UDP ingress.
 * The business logic doesn't care. It just reads from `cloudNetworkFrames`.
 */
@OptIn(ExperimentalForeignApi::class)
fun bootPosixUdpServer(port: Int) {
    udpSockFd = dev.autumn.udp.UdpGatewayDriver.bindNonBlockingSocket(port)
    println("[Autumn Cloud-Tier] Listening natively on UDP port $port. Ready for payload ingress.")
    // Note: The AutumnScheduler will automatically invoke pollUdpGateway() via @Observe
}
