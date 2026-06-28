package dev.autumn.channel

actual class ZeroCopyNetworkChannel actual constructor(
    val interfaceName: String,
    val queueId: Int,
    val host: String,
    val port: Int,
    val startIndex: Int,
    val capacity: Int
) {
    actual inline fun poll(block: (offset: Int, length: Int) -> Unit) {
        // Javascript environments (Browser/Node) have no concept of raw hardware networking.
        // NOP Stub
    }

    actual inline fun release(offset: Int) {
        // NOP Stub
    }

    actual fun destroy() {
        // NOP Stub
    }
}