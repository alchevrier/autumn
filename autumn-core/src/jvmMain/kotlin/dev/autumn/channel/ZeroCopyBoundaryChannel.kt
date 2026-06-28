package dev.autumn.channel

import java.net.InetSocketAddress
import java.net.StandardProtocolFamily
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

/**
 * JVM implementation for the ZeroCopy channel.
 * Uses Java NIO DirectByteBuffers (off-heap memory) and non-blocking DatagramChannels
 * to faithfully simulate the AF_XDP UMEM environment.
 */
actual class ZeroCopyNetworkChannel actual constructor(
    val interfaceName: String,
    val queueId: Int,
    val host: String,
    val port: Int,
    val startIndex: Int,
    val capacity: Int
) {
    companion object {
        const val FRAME_SIZE = 2048
    }
    
    // DirectByteBuffer completely bypasses the JVM Garbage Collector.
    // For local simulation, we allocate the channel's capacity. 
    // (In Linux Native, this offsets into a singular massive global UMEM pointer).
    @PublishedApi
    internal val umem: ByteBuffer = ByteBuffer.allocateDirect(FRAME_SIZE * capacity)
    
    @PublishedApi
    internal var writeIndex = 0

    @PublishedApi
    internal val datagramChannel: DatagramChannel = DatagramChannel.open(StandardProtocolFamily.INET)

    init {
        datagramChannel.configureBlocking(false) // Strict Non-Blocking (Polling mode)
        // Correct implementation mapping to the provided host and port configuration
        datagramChannel.bind(InetSocketAddress(host, port))
    }

    actual inline fun poll(block: (offset: Int, length: Int) -> Unit) {
        val offset = writeIndex * FRAME_SIZE
        
        // Position the buffer to the current UMEM frame slot
        umem.position(offset)
        umem.limit(offset + FRAME_SIZE)

        // Non-blocking hardware read directly into off-heap memory
        val senderAddress = datagramChannel.receive(umem)
        
        if (senderAddress != null) {
            val length = umem.position() - offset
            
            // Invoke the application logic with the simulated global startIndex offset
            // so the Kotlin compiler plugin array math aligns!
            block((startIndex * FRAME_SIZE) + offset, length)
            
            // Advance the ring
            writeIndex = (writeIndex + 1) % capacity
        }
    }

    actual inline fun release(offset: Int) {
        // On JVM, manual memory release back to the NIC isn't required 
        // as we are manually managing the DirectByteBuffer ring via writeIndex.
    }

    actual fun destroy() {
        datagramChannel.close()
    }
}