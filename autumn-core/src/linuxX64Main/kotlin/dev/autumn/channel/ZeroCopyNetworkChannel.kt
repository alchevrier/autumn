package dev.autumn.channel

import dev.autumn.xdp.XdpSocket

actual class ZeroCopyNetworkChannel actual constructor(
    val interfaceName: String,
    val queueId: Int,
    val host: String,   // Later passed into the eBPF hardware map
    val port: Int       // Later passed into the eBPF hardware map
) {
    @PublishedApi
    internal val socket = XdpSocket(interfaceName, queueId)

    actual inline fun poll(block: (offset: Int, length: Int) -> Unit) {
        socket.pollRx(block)
    }

    actual inline fun release(offset: Int) {
        socket.pushFill(offset.toULong())
    }

    actual fun destroy() {
        socket.destroy()
    }
}