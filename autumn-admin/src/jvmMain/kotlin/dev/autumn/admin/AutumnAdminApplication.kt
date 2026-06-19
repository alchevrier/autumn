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
import java.util.UUID

interface KeyRepositoryStrategy {
    val activeKeys: Set<String>
    val revokedKeys: Set<String>
    fun provide(key: String)
    fun revoke(key: String)
    fun generate(): String
    fun resetForTest()
}

class InMemoryKeyRepositoryStrategy : KeyRepositoryStrategy {
    private val _activeKeys = mutableSetOf("ak_live_x892njkasd891", "ak_test_j123n12o3n123")
    private val _revokedKeys = mutableSetOf("ak_live_revoked99999")

    override val activeKeys: Set<String> get() = _activeKeys
    override val revokedKeys: Set<String> get() = _revokedKeys

    override fun provide(key: String) {
        _revokedKeys.remove(key)
        _activeKeys.add(key)
    }

    override fun revoke(key: String) {
        _activeKeys.remove(key)
        _revokedKeys.add(key)
    }

    override fun generate(): String {
        val newKey = "ak_live_" + UUID.randomUUID().toString().replace("-", "")
        _activeKeys.add(newKey)
        return newKey
    }

    override fun resetForTest() {
        _activeKeys.clear()
        _revokedKeys.clear()
    }
}

object KeyRepository {
    var strategy: KeyRepositoryStrategy = InMemoryKeyRepositoryStrategy()

    val activeKeys: Set<String> get() = strategy.activeKeys
    val revokedKeys: Set<String> get() = strategy.revokedKeys

    fun provide(key: String) = strategy.provide(key)
    fun revoke(key: String) = strategy.revoke(key)
    fun generate() = strategy.generate()
    fun resetForTest() = strategy.resetForTest()
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

        post("/keys/generate") {
            val newKey = KeyRepository.generate()
            call.respondText("{\"key\":\"$newKey\"}", io.ktor.http.ContentType.Application.Json, HttpStatusCode.Created)
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
