package dev.autumn.xdp

import dev.autumn.channel.AutumnChannel
import dev.autumn.channel.AutumnRuntime

/**
 * A Multiplatform-friendly Driver object that the K2 Compiler dynamically invokes
 * when it detects an @XdpGateway annotation. This keeps the IR injection small
 * while isolating the Linux-specific networking mechanics.
 */
object XdpGatewayDriver {
    inline fun bind(
        interfaceName: String,
        queueId: Int,
        forceCopy: Boolean,
        channel: AutumnChannel<*>,
        crossinline onPacket: (index: Int, length: Int) -> Unit
    ) {
        println("[Autumn OS] Dynamically binding @XdpGateway to $interfaceName (Queue $queueId)...")
        try {
            val socket = XdpSocket(interfaceName, queueId, forceCopy)
            println("[Autumn OS] XDP Socket Bound. Transferring hardware bridging to AutumnScheduler clock tick...")

            // Route physical networking entirely into the Scheduler's clock tick loop
            AutumnRuntime.spawnOscillator(queueId) {
                var processedAny = false
                socket.pollRx(0) { baseOffset: Int, length: Int ->
                    val hash = baseOffset % 4
                    val idx = channel.nextMappedIndexPartition(hash)
                    if (idx != -1) {
                        onPacket(idx, length)
                        channel.commitNextPartition(hash)
                    }
                    processedAny = true
                }
                
                // Returns true if packets were pulled, yielding control if false
                processedAny
            }
        } catch (e: Exception) {
            println("[Autumn WARNING] @XdpGateway Failed to bind $interfaceName: ${e.message}")
        }
    }
}
