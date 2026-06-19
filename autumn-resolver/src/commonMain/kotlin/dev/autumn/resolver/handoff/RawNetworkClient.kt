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
     * 
     * @param endpoint The route or URL to access.
     * @param method The HTTP method (GET, POST, etc).
     * @param requestBody An optional, pre-allocated raw byte buffer for sending data.
     * @return The exact Byte payload of the response, or failure if network/OS errors occurred.
     */
    suspend fun executeRaw(endpoint: String, method: String = "GET", requestBody: ByteArray? = null): Result<ByteArray>
}
