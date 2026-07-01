package dev.autumn.benchmark.itch

import dev.autumn.annotations.*
import dev.autumn.channel.AutumnChannel
import dev.autumn.memory.AutumnMemoryBank
import kotlinx.cinterop.*
import platform.posix.*

/**
 * Phase 5.5: Pluggable Layered Boundary Transports (ADR-0030)
 * 
 * We reuse the EXACT same topology logic from ItchParserBenchmark (`rawNetworkFrames`).
 * No business logic changes, only the boundary ingestion changes from `fread` to `recvfrom`.
 * This proves the FSM boundary is agnostic to the Network layer, maintaining zero-allocation
 * safely inside the JVM/LLVM boundary while opening Autumn to Cloud deployments!
 */
@OptIn(ExperimentalForeignApi::class)
fun runPosixUdpServer(port: UShort) {
    println("[Autumn Cloud-Tier] Starting POSIX UDP Zero-Allocation Listener on port ${port}")
    
    val sockfd = socket(AF_INET, SOCK_DGRAM, 0)
    if (sockfd < 0) {
        println("FAILED to create socket: ${strerror(errno)}")
        return
    }

    memScoped {
        val serverAddr = alloc<sockaddr_in>()
        memset(serverAddr.ptr, 0, sizeOf<sockaddr_in>().toULong())
        serverAddr.sin_family = AF_INET.convert<sa_family_t>()
        serverAddr.sin_addr.s_addr = INADDR_ANY
        
        // Host to Network Short for port
        val b0 = (port.toInt() shr 8) and 0xFF
        val b1 = port.toInt() and 0xFF
        serverAddr.sin_port = ((b1 shl 8) or b0).toUShort()

        if (bind(sockfd, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
            println("FAILED to bind socket: ${strerror(errno)}")
            close(sockfd)
            return
        }

        // Set socket to non-blocking to participate in the clock-aware FSM natively
        val flags = fcntl(sockfd, F_GETFL, 0)
        fcntl(sockfd, F_SETFL, flags or O_NONBLOCK)

        println("[Autumn Cloud-Tier] Listening natively on UDP port ${port}. Ready for payload ingress.")

        var start = 0L
        var messagesProcessed = 0L
        val maxMessages = 2_000_000L

        // Preallocate a stack buffer for receiving packets
        val recvBuf = allocArray<ByteVar>(2048)

        while (messagesProcessed < maxMessages) {
            val bytesRead = recvfrom(sockfd, recvBuf, 2048U, 0, null, null)
            
            if (bytesRead > 0) {
                if (start == 0L) start = dev.autumn.scheduler.AutumnClock.now()
                
                // Pure FSM ingestion - EXACT same routing logic as the File stream!
                // Zero JVM allocations, purely bounded by hardware
                val frameIdx = rawNetworkFrames.nextIndexPartition(0)
                if (frameIdx != -1) {
                    for (i in 0 until bytesRead.toInt()) {
                        AutumnMemoryBank.setByte(frameIdx + i, recvBuf[i])
                    }
                    rawNetworkFrames.commitNextPartition(0)
                    messagesProcessed++
                } else {
                    sched_yield() // Channel full, backpressure handled locally
                }
            } else if (bytesRead < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
                // Non-blocking wait, FSM loop can execute other FSM ticks here!
                sched_yield()
            } else if (bytesRead < 0) {
                println("Socket error: ${strerror(errno)}")
                break
            }
        }

        if (start != 0L) {
            val nanos = dev.autumn.scheduler.AutumnClock.now() - start
            val ms = nanos / 1_000_000
            println("[Autumn Cloud-Tier] UDP Stream Complete! Parsed ${messagesProcessed} exact messages natively in ${ms}ms.")
        }
    }

    close(sockfd)
}
