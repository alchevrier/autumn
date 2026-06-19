package dev.autumn.bff.plugins

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.header
import io.ktor.server.response.respond
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class ApiKeyResponse(val activeKeys: Set<String>, val revokedKeys: Set<String>)

class ApiKeyValidationException(message: String) : Exception(message)

object KeyCache {
    var activeKeys = setOf<String>()
    var revokedKeys = setOf<String>()

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun sync() {
        try {
            val response: ApiKeyResponse = client.get("http://127.0.0.1:8081/keys").body()
            activeKeys = response.activeKeys
            revokedKeys = response.revokedKeys
        } catch (e: Exception) {
            // Log sync failure, retain old cache
        }
    }
}

val ApiKeyValidationPlugin = createApplicationPlugin(name = "ApiKeyValidation") {
    // Sync initially for PoC
    runBlocking { KeyCache.sync() }

    onCall { call ->
        val authHeader = call.request.header("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.respond(HttpStatusCode.Unauthorized, "Missing API Key")
            throw ApiKeyValidationException("Missing API Key")
        }
        
        val key = authHeader.removePrefix("Bearer ")
        if (KeyCache.revokedKeys.contains(key)) {
            call.respond(HttpStatusCode.Forbidden, "API Key Revoked")
            throw ApiKeyValidationException("API Key Revoked")
        }

        if (!KeyCache.activeKeys.contains(key)) {
            call.respond(HttpStatusCode.Unauthorized, "Invalid API Key")
            throw ApiKeyValidationException("Invalid API Key")
        }
        
        // At this point, the key is known and not revoked.
    }
}
