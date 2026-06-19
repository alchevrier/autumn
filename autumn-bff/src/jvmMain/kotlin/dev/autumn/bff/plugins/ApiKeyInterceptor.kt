package dev.autumn.bff.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.request.header
import io.ktor.server.response.respond

class ApiKeyValidationException(message: String) : Exception(message)

val ApiKeyValidationPlugin = createApplicationPlugin(name = "ApiKeyValidation") {
    val activeKeys = setOf(
        "ak_live_x892njkasd891", // Example allowed key
        "ak_test_j123n12o3n123"
    )
    
    val revokedKeys = setOf(
        "ak_live_revoked99999"
    )

    onCall { call ->
        val authHeader = call.request.header("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.respond(HttpStatusCode.Unauthorized, "Missing API Key")
            throw ApiKeyValidationException("Missing API Key")
        }
        
        val key = authHeader.removePrefix("Bearer ")
        if (revokedKeys.contains(key)) {
            call.respond(HttpStatusCode.Forbidden, "API Key Revoked")
            throw ApiKeyValidationException("API Key Revoked")
        }

        if (!activeKeys.contains(key)) {
            call.respond(HttpStatusCode.Unauthorized, "Invalid API Key")
            throw ApiKeyValidationException("Invalid API Key")
        }
        
        // At this point, the key is known and not revoked.
        // In a real system, you would attach the resolved tenant/scope to the Call context here.
    }
}
