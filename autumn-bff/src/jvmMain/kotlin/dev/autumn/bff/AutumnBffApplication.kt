package dev.autumn.bff

import dev.autumn.bff.plugins.ApiKeyValidationPlugin
import dev.autumn.bff.provider.ConfigurationProvider
import dev.autumn.bff.resolver.CountryResolver
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }

    install(ApiKeyValidationPlugin)

    routing {
        get("/config") {
            val deviceId = call.request.header("X-Device-Id") ?: "anonymous"
            
            // 1. Resolve Country
            val country = CountryResolver.resolve(call)
            
            // 2. Evaluate Config & Cohorts
            val tailoredConfig = ConfigurationProvider.provideForDevice(deviceId, country)
            
            // 3. Serialize and Respond (Normally we'd use Content Negotiation, but keeping it raw here to mirror the circuit API)
            val jsonPayload = Json.encodeToString(tailoredConfig)
            
            call.respondText(jsonPayload, io.ktor.http.ContentType.Application.Json, HttpStatusCode.OK)
        }
    }
}
