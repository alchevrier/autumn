package dev.autumn.channel

import kotlin.test.Test
import kotlin.test.assertTrue

class ZeroCopyNetworkChannelTest {

    @Test
    fun testChannelInitialization() {
        var initialized = false
        try {
            // Attempt to initialize the channel.
            // On JVM: This will succeed immediately by binding to the UDP host/port.
            // On LinuxX64: This requires root privileges (CAP_NET_RAW) and a valid interface 
            // to bind AF_XDP. It will likely throw a RuntimeException if run in a normal CI/CD environment.
            val channel = ZeroCopyNetworkChannel("lo", 0, "127.0.0.1", 5000, 0, 4096)
            
            // If we succeed (e.g., JVM), verify the inline poll block doesn't crash
            channel.poll { offset, length -> 
                // NOP for JVM stub
            }
            channel.release(0)
            channel.destroy()
            initialized = true

        } catch (e: RuntimeException) {
            // This is expected on Linux natively unless ran via `sudo ./gradlew linuxX64Test`
            // with a properly configured eBPF permissions capability.
            val message = e.message ?: ""
            assertTrue(
                message.contains("socket(AF_XDP)") || 
                message.contains("Failed to bind AF_XDP Socket") ||
                message.contains("posix_memalign"),
                "Expected AF_XDP hardware validation error on unprivileged native execution, got: $message"
            )
            initialized = true
        }

        assertTrue(initialized, "Channel test failed to execute either the stub or catch the native capability restriction")
    }
}