package dev.autumn.udp

import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel

/**
 * High-level Cloud boundary driver for Managed JVM UDP Socket polling.
 * The K2 compiler statically wires `@UdpGateway` properties to invoke this driver.
 */
object UdpGatewayDriver {

    fun bindNonBlockingSocket(port: Int, host: String = "0.0.0.0"): DatagramChannel {
        val channel = DatagramChannel.open()
        channel.configureBlocking(false)
        channel.bind(InetSocketAddress(host, port))
        
        println("[Autumn JVM Cloud-Tier] Dynamically bridging @UdpGateway to native java.nio $host:$port...")
        return channel
    }
}
