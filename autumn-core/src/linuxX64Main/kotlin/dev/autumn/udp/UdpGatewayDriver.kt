package dev.autumn.udp

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Low-level hardware boundary driver for Cloud-Tier UDP Socket polling. 
 * The K2 compiler statically wires `@UdpGateway` properties to invoke this driver.
 */
object UdpGatewayDriver {

    @OptIn(ExperimentalForeignApi::class)
    fun bindNonBlockingSocket(port: Int): Int {
        val sockfd = socket(AF_INET, SOCK_DGRAM, 0)
        if (sockfd < 0) {
            throw RuntimeException("FAILED to create socket: ${strerror(errno)}")
        }

        memScoped {
            val serverAddr = alloc<sockaddr_in>()
            memset(serverAddr.ptr, 0, sizeOf<sockaddr_in>().toULong())
            serverAddr.sin_family = AF_INET.convert<sa_family_t>()
            serverAddr.sin_addr.s_addr = INADDR_ANY
            
            val b0 = (port shr 8) and 0xFF
            val b1 = port and 0xFF
            serverAddr.sin_port = ((b1 shl 8) or b0).toUShort()

            if (bind(sockfd, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
                close(sockfd)
                throw RuntimeException("FAILED to bind socket: ${strerror(errno)}")
            }
        }

        val flags = fcntl(sockfd, F_GETFL, 0)
        fcntl(sockfd, F_SETFL, flags or O_NONBLOCK)
        
        println("[Autumn OS] Dynamically bridging @UdpGateway to native UDP port $port...")
        return sockfd
    }
}
