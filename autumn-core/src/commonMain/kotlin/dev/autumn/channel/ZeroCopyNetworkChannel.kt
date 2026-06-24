package dev.autumn.channel

/**
 * A Multiplatform Wait-Free network ingress channel.
 *
 * This abstraction guarantees zero-allocation packet processing.
 * - On Linux, this is strictly bound to AF_XDP natively.
 * - On JVM, this wraps NIO DirectByteBuffers (Project Panama in the future) for local simulation/testing.
 */
expect class ZeroCopyNetworkChannel(
    interfaceName: String,
    queueId: Int,
    host: String,
    port: Int,
    startIndex: Int,
    capacity: Int
) {
    
    /**
     * Polls the hardware queue for new packets.
     * Guaranteed to execute entirely wait-free (0 system calls, 0 locks).
     * 
     * @param block Inline lambda that processes the packet's UMEM payload offset and length.
     */
    inline fun poll(block: (offset: Int, length: Int) -> Unit)

    /**
     * Replenishes the hardware DMA queue, allowing the NIC to overwrite this UMEM frame.
     * Must be called after the [block] in `poll()` finishes processing the packet.
     */
    inline fun release(offset: Int)

    fun destroy()
}