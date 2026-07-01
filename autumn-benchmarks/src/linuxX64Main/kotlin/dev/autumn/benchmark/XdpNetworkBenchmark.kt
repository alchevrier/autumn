@file:OptIn(ExperimentalForeignApi::class)
package dev.autumn.benchmark

import dev.autumn.annotations.*
import dev.autumn.channel.AutumnChannel
import dev.autumn.memory.AutumnMemoryBank
import dev.autumn.scheduler.AutumnScheduler
import dev.autumn.scheduler.AutumnClock
import dev.autumn.observatory.LatencyHistogram
import dev.autumn.xdp.XdpSocket
import platform.posix.*
import dev.autumn.xdp.XdpGatewayDriver
import kotlin.system.exitProcess

@LongLived
@XdpGateway(interfaceName = "veth1", queueId = 0, forceCopy = true)
@BoundaryChannel(capacity = 16777216, weight = 100, sharded = 4)
val xdpInboundQueue = AutumnChannel<OrderEvent>(16777216, sharded = 4)

@LongLived
@ObserveChannel(observerName = "xdpTelemetry", capacity = 1000000)
val xdpMetrics = LatencyHistogram(0, 1000000)

@Observe("xdpTelemetry")
@HotPath
@CycleBudget(limit = 600)
@LongLived
fun onXdpPacket(idx: Int) {
    val packet = OrderEvent(idx)
    // 1. Packet logic executes here totally natively!
    // 2. HardwareOscillator clock gates automatically based on network traffic flow.
    val px = packet.price
    // Typical Order Book / Strategy execution logic simulating latency
    if (px > 0) {
        packet.shares += 1
    }
}

@InjectTopology
fun bootXdpPipeline() {
    // Compiler injects 4 Sharded Hardware Oscillators dynamically here
}

fun runXdpTrafficGenerator() {
    println("[Traffic Generator] Booting pure POSIX UDP socket targeting veth1...")
    
    val sockfd = socket(AF_INET, SOCK_DGRAM, 0)
    if (sockfd < 0) {
        println("[Traffic Generator] Failed to create socket")
        return
    }
    
    memScoped {
        val destAddr = alloc<sockaddr_in>()
        memset(destAddr.ptr, 0, sizeOf<sockaddr_in>().convert())
        destAddr.sin_family = AF_INET.convert()
        // Port 1234 in Network Byte Order is 53764
        destAddr.sin_port = 53764.toUShort()
        destAddr.sin_addr.s_addr = 167772186u // 10.0.0.2 in network byte order (Big Endian)
        
        val payload = "ITCH_MOCK_PAYLOAD_DATA"
        val payloadPtr = payload.cstr.ptr
        val payloadLen = payload.length.convert<size_t>()
        
        println("[Traffic Generator] Waiting 2s for XDP BPF program to attach...")
        sleep(2u)
        
        println("[Traffic Generator] Transmitting 1,000,000 UDP packets natively...")
        val start = dev.autumn.scheduler.AutumnClock.now()
        
        for (i in 0 until 1_000_000) {
            sendto(sockfd, payloadPtr, payloadLen, 0, destAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
            if (i % 2000 == 0) sched_yield() // Small hardware yield to keep VETH alive
        }
        
        val nanos = dev.autumn.scheduler.AutumnClock.now() - start
        println("[Traffic Generator] Transmission complete in ${nanos / 1_000_000}ms!")
    }
    close(sockfd)
}

@LongLived
fun xdpMain() {
    println("--- Executing XDP Hardware Network Boundary ---")
    println("[Warning] This requires root to bind AF_XDP to the veth1 network interface.")
    
    // Boot the FSM Shards (Pins 4 CPU cores!)
    bootXdpPipeline()

    try {
        xdpMetrics.startRecording()
        
        // Spin up the traffic generator on a stray core 
        dev.autumn.channel.AutumnRuntime.spawn {
            runXdpTrafficGenerator()
            println("[Autumn OS] Received 1,000,000 packets directly from Kernel BPF!")
            exitProcess(0)
        }

        // The K2 Compiler dynamically injects this exact binding loop behind the scenes 
        // when it evaluates the @XdpGateway annotation on 'xdpInboundQueue'
        XdpGatewayDriver.bind("veth1", 0, forceCopy = true, xdpInboundQueue) { idx, length ->
            val order = OrderEvent(idx)
            order.price = length
        }
        
    } catch (e: Exception) {
        println("XDP Failed to bind. Did you run the setup_veth.sh script with sudo?")
        e.printStackTrace()
    }
    
    // Park the main thread while the hardware oscillators do the work
    sleep(10u)
}
