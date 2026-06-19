package dev.autumn.resolver.handoff

/**
 * A hardware-sympathetic wrapper over the platform's OS network stack.
 * 
 * Traditional libraries like Ktor return rich DTOs and strings. This boundary 
 * forces the network stack to return ONLY the raw byte buffers emitted by the NIC 
 * and OS socket layer, preventing accidental garbage generation before our 
 * circuit-based parser has an opportunity to align it.
 */
interface RawNetworkClient {
    /**
     * Executes the HTTP request.
     * @return The exact Byte payload of the response, or failure if network/OS errors occurred.
     */
    suspend fun getBytes(endpoint: String): Result<ByteArray>
}
