package dev.autumn.benchmark

import dev.autumn.annotations.*
import dev.autumn.channel.AutumnChannel
import dev.autumn.memory.AutumnMemoryBank
import dev.autumn.scheduler.AutumnScheduler
import dev.autumn.scheduler.AutumnClock
import dev.autumn.observatory.LatencyHistogram
import dev.autumn.xdp.XdpSocket
import platform.posix.*
import kotlinx.cinterop.*
import kotlin.system.exitProcess

@LongLived
@BoundaryChannel(capacity = 16777216, weight = 100, sharded = 4)
val xdpInboundQueue = AutumnChannel<OrderEvent>(16777216, sharded = 4)

@LongLived
@ObserveChannel(observerName = "xdpTelemetry", capacity = 1000000)
val xdpMetrics = LatencyHistogram(0, 1000000)

@Observe("xdpTelemetry")
@HotPath
@CycleBudget(limit = 600)
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
    println("[Traffic Generator] Booting pure POSIX UDP socket on veth0...")
    // This runs in a separate thread, blasting standard UDP packets to 10.0.0.2 (veth1).
    // Autumn will intercept these at the bare-metal kernel boundary before they even reach the Linux network stack!
}

@LongLived
fun xdpMain() {
    println("--- Executing XDP Hardware Network Boundary ---")
    println("[Warning] This requires root to bind AF_XDP to the veth1 network interface.")
    
    // Allocate the unified physical BRAM equivalent (UMEM + Queues)
    AutumnMemoryBank.allocate(16777216L * 100L)
    
    // Boot the FSM Shards (Pins 4 CPU cores!)
    bootXdpPipeline()

    try {
        println("[Autumn OS] Binding XDP Socket to veth1 (Queue 0)...")
        val socket = XdpSocket(interfaceName = "veth1", queueId = 0)

        // Spin up the traffic generator on a stray core 
        dev.autumn.channel.AutumnRuntime.spawn {
            runXdpTrafficGenerator()
        }

        println("[Autumn OS] Entering Lock-Free Hardware DMA Polling phase...")
        // Master Thread: Acts as the physical NIC bridging layer
        while (true) {
            socket.poll { baseOffset, length ->
                // Look into the raw byte payload to find the Session ID
                // For now, mock a round-robin hash
                val hash = baseOffset % 4
                
                val idx = xdpInboundQueue.nextMappedIndexPartition(hash)
                if (idx != -1) {
                    val order = OrderEvent(idx)
                    // Memory Copy raw packet payload from XDP UMEM -> Autumn Memory Bank
                    order.price = length // Mock payload extraction
                    inboundNetwork.commitNextPartition(hash)
                }
            }
        }
    } catch (e: Exception) {
        println("XDP Failed to bind. Did you run the setup_veth.sh script with sudo?")
        e.printStackTrace()
    }
}
