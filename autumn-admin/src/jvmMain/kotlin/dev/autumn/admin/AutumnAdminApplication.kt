package dev.autumn.admin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

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

// Minimal manual parser for the test
fun parseKey(json: String): String {
    return json.substringAfter("\"key\":\"").substringBefore("\"")
}

fun Application.module() {
    routing {
        get("/keys") {
            val activeArray = KeyRepository.activeKeys.joinToString("\",\"", "[\"", "\"]")
            val revokedArray = KeyRepository.revokedKeys.joinToString("\",\"", "[\"", "\"]")
            val json = "{\"activeKeys\":$activeArray, \"revokedKeys\":$revokedArray}"
            call.respondText(json, io.ktor.http.ContentType.Application.Json)
        }

        post("/keys/provide") {
            val reqText = call.receiveText()
            val key = parseKey(reqText)
            KeyRepository.provide(key)
            call.respondText("{\"key\":\"$key\"}", io.ktor.http.ContentType.Application.Json, HttpStatusCode.Created)
        }

        delete("/keys/revoke") {
            val reqText = call.receiveText()
            val key = parseKey(reqText)
            KeyRepository.revoke(key)
            call.respondText("{\"key\":\"$key\"}", io.ktor.http.ContentType.Application.Json, HttpStatusCode.OK)
        }
    }
}
