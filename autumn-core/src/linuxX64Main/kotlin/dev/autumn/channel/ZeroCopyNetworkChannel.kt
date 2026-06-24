package dev.autumn.channel

import dev.autumn.xdp.XdpSocket

actual class ZeroCopyNetworkChannel actual constructor(
    val interfaceName: String,
    val queueId: Int,
    val host: String,   // Later passed into the eBPF hardware map
    val port: Int,      // Later passed into the eBPF hardware map
    val startIndex: Int,
    val capacity: Int
) {
    @PublishedApi
    internal val socket = XdpSocket(interfaceName, queueId, capacity)

    actual inline fun poll(block: (offset: Int, length: Int) -> Unit) {
        // Pushes the exact `startIndex` base offset mathematically aligned by 
        // the K2 compiler so the Flyweights point precisely into the massive Shared UMEM bound.
        socket.pollRx(startIndex * XdpSocket.FRAME_SIZE.toInt(), block)
    }

    actual inline fun release(offset: Int) {
        socket.pushFill(offset.toULong())
    }

    actual fun destroy() {
        socket.destroy()
    }
}