package dev.autumn.admin

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class ApiKeyRequest(val key: String)

@Serializable
data class ApiKeyResponse(val activeKeys: Set<String>, val revokedKeys: Set<String>)

object KeyRepository {
    val activeKeys = mutableSetOf("ak_live_x892njkasd891", "ak_test_j123n12o3n123")
    val revokedKeys = mutableSetOf("ak_live_revoked99999")

    fun provide(key: String) {
        revokedKeys.remove(key)
        activeKeys.add(key)
    }

    fun revoke(key: String) {
        activeKeys.remove(key)
        revokedKeys.add(key)
    }
}

fun main() {
    embeddedServer(Netty, port = 8081, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/keys") {
            call.respond(ApiKeyResponse(KeyRepository.activeKeys, KeyRepository.revokedKeys))
        }

        post("/keys/provide") {
            val req = call.receive<ApiKeyRequest>()
            KeyRepository.provide(req.key)
            call.respond(HttpStatusCode.Created, mapOf("status" to "provided", "key" to req.key))
        }

        delete("/keys/revoke") {
            val req = call.receive<ApiKeyRequest>()
            KeyRepository.revoke(req.key)
            call.respond(HttpStatusCode.OK, mapOf("status" to "revoked", "key" to req.key))
        }
    }
}
