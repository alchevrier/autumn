package dev.autumn.admin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.UUID

enum class Role { USER, ADMIN }

data class UserAccount(val id: String, val token: String, val role: Role)

object UserRepository {
    val accounts = mapOf(
        "system-admin-token" to UserAccount("admin-1", "system-admin-token", Role.ADMIN),
        "dev-user-token" to UserAccount("user-1", "dev-user-token", Role.USER)
    )
}

class AuthorizationException(val code: HttpStatusCode, message: String) : Exception(message)

fun ApplicationCall.requireRole(vararg roles: Role) {
    val token = request.header("Authorization")?.removePrefix("Bearer ") 
        ?: throw AuthorizationException(HttpStatusCode.Unauthorized, "{\"error\":\"Missing Authorization header\"}")
    
    val user = UserRepository.accounts[token] 
        ?: throw AuthorizationException(HttpStatusCode.Unauthorized, "{\"error\":\"Invalid token\"}")
        
    if (user.role !in roles && !(roles.contains(Role.USER) && user.role == Role.ADMIN)) {
        throw AuthorizationException(HttpStatusCode.Forbidden, "{\"error\":\"Insufficient permissions\"}")
    }
}

data class ApiKeyRecord(val id: String, val name: String, val secret: String)

interface KeyRepositoryStrategy {
    val activeRecords: List<ApiKeyRecord>
    val revokedRecords: List<ApiKeyRecord>
    fun provide(id: String, name: String, secret: String): ApiKeyRecord
    fun revokeById(id: String)
    fun generate(name: String): ApiKeyRecord
    fun resetForTest()
}

class InMemoryKeyRepositoryStrategy : KeyRepositoryStrategy {
    private val records = mutableMapOf<String, ApiKeyRecord>()
    private val revokedIds = mutableSetOf<String>()

    init {
        provide("id-1", "Default Live Key", "ak_live_x892njkasd891")
        provide("id-2", "Default Test Key", "ak_test_j123n12o3n123")
        val revoked = provide("id-revoked", "Old Revoked Key", "ak_live_revoked99999")
        revokeById(revoked.id)
    }

    override val activeRecords: List<ApiKeyRecord>
        get() = records.values.filter { it.id !in revokedIds }

    override val revokedRecords: List<ApiKeyRecord>
        get() = records.values.filter { it.id in revokedIds }

    override fun provide(id: String, name: String, secret: String): ApiKeyRecord {
        val record = ApiKeyRecord(id, name, secret)
        records[id] = record
        revokedIds.remove(id)
        return record
    }

    override fun revokeById(id: String) {
        if (records.containsKey(id)) {
            revokedIds.add(id)
        }
    }

    override fun generate(name: String): ApiKeyRecord {
        val id = UUID.randomUUID().toString()
        val secret = "ak_live_" + id.replace("-", "")
        return provide(id, name, secret)
    }

    override fun resetForTest() {
        records.clear()
        revokedIds.clear()
    }
}

object KeyRepository {
    var strategy: KeyRepositoryStrategy = InMemoryKeyRepositoryStrategy()

    val activeRecords get() = strategy.activeRecords
    val revokedRecords get() = strategy.revokedRecords

    fun provide(id: String, name: String, secret: String) = strategy.provide(id, name, secret)
    fun revokeById(id: String) = strategy.revokeById(id)
    fun generate(name: String) = strategy.generate(name)
    fun resetForTest() = strategy.resetForTest()
}

fun main() {
    embeddedServer(Netty, port = 8081, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun parseId(json: String): String = json.substringAfter("\"id\":\"").substringBefore("\"")
fun parseName(json: String): String = json.substringAfter("\"name\":\"").substringBefore("\"")
fun parseSecret(json: String): String = json.substringAfter("\"secret\":\"").substringBefore("\"")

fun Application.module() {
    install(StatusPages) {
        exception<AuthorizationException> { call, cause ->
            call.respondText(cause.message ?: "", io.ktor.http.ContentType.Application.Json, cause.code)
        }
    }

    routing {
        // UI Facing Endpoint: Requires USER or ADMIN
        get("/keys") {
            call.requireRole(Role.USER, Role.ADMIN)
            val activeList = KeyRepository.activeRecords.joinToString(",") { "{\"id\":\"${it.id}\", \"name\":\"${it.name}\"}" }
            val revokedList = KeyRepository.revokedRecords.joinToString(",") { "{\"id\":\"${it.id}\", \"name\":\"${it.name}\"}" }
            val json = "{\"activeKeys\":[$activeList], \"revokedKeys\":[$revokedList]}"
            call.respondText(json, io.ktor.http.ContentType.Application.Json)
        }

        // Internal BFF Facing Endpoint: Requires ADMIN
        get("/internal/keys") {
            call.requireRole(Role.ADMIN)
            val activeList = KeyRepository.activeRecords.joinToString("\",\"", "[\"", "\"]") { it.secret }
            val revokedList = KeyRepository.revokedRecords.joinToString("\",\"", "[\"", "\"]") { it.secret }
            val aFormat = if (KeyRepository.activeRecords.isEmpty()) "[]" else activeList
            val rFormat = if (KeyRepository.revokedRecords.isEmpty()) "[]" else revokedList
            val json = "{\"activeKeys\":$aFormat, \"revokedKeys\":$rFormat}"
            call.respondText(json, io.ktor.http.ContentType.Application.Json)
        }

        post("/keys/generate") {
            call.requireRole(Role.USER, Role.ADMIN)
            val reqText = call.receiveText()
            val name = parseName(reqText)
            val record = KeyRepository.generate(name)
            val json = "{\"id\":\"${record.id}\", \"name\":\"${record.name}\", \"secret\":\"${record.secret}\"}"
            call.respondText(json, io.ktor.http.ContentType.Application.Json, HttpStatusCode.Created)
        }

        post("/keys/provide") {
            call.requireRole(Role.USER, Role.ADMIN)
            val reqText = call.receiveText()
            val id = parseId(reqText)
            val name = parseName(reqText)
            val secret = parseSecret(reqText)
            KeyRepository.provide(id, name, secret)
            call.respondText("{\"id\":\"$id\"}", io.ktor.http.ContentType.Application.Json, HttpStatusCode.Created)
        }

        delete("/keys/revoke") {
            call.requireRole(Role.USER, Role.ADMIN)
            val reqText = call.receiveText()
            val id = parseId(reqText)
            KeyRepository.revokeById(id)
            call.respondText("{\"id\":\"$id\"}", io.ktor.http.ContentType.Application.Json, HttpStatusCode.OK)
        }
    }
}
