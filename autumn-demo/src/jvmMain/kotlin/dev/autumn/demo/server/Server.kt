package dev.autumn.demo.server

import dev.autumn.annotations.LongLived
import io.ktor.http.ContentType
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import java.io.File

@LongLived
fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(CORS) {
            anyHost()
        }
        
        routing {
            // REST API endpoint
            get("/api/state") {
                // Return a static JSON representation of a zero-allocation schema
                call.respondText(
                    """
                    {
                        "resources": {
                            "hero-title": {
                                "type": "TEXT",
                                "path": "Welcome to Autumn Circuit",
                                "action": "none"
                            },
                            "action-label": {
                                "type": "TEXT",
                                "path": "Network Circuit Demo",
                                "action": "click"
                            }
                        }
                    }
                    """.trimIndent(),
                    contentType = ContentType.Application.Json
                )
            }

            // Static web application
            val webDir = System.getenv("AUTUMN_DEMO_WEB_DIR")
            if (webDir != null) {
                staticFiles("/", File(webDir)) {
                    default("index.html")
                }
            } else {
                get("/") {
                    call.respondText("Web application build missing (AUTUMN_DEMO_WEB_DIR not set).")
                }
            }
        }
    }.start(wait = true)
}