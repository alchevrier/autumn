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
            // REST API endpoint: Home Dashboard
            get("/api/state/home") {
                // Return a static JSON representation of a zero-allocation schema
                call.respondText(
                    """
                    {
                        "resources": {
                            "hero-title": {
                                "type": "TITLE",
                                "path": "Home Dashboard",
                                "action": "none"
                            },
                            "description-text": {
                                "type": "TEXT",
                                "path": "Welcome to the zero-allocation browser. Click below to load a dynamic list of employees.",
                                "action": "none"
                            },
                            "nav-btn": {
                                "type": "BUTTON",
                                "path": "View Employee List",
                                "action": "navigate:/api/state/list"
                            }
                        }
                    }
                    """.trimIndent(),
                    contentType = ContentType.Application.Json
                )
            }

            // REST API endpoint: Filterable List
            get("/api/state/list") {
                call.respondText(
                    """
                    {
                        "resources": {
                            "list-title": { "type": "TITLE", "path": "Employee Directory", "action": "none" },
                            "back-btn": { "type": "BUTTON", "path": "← Back to Home", "action": "navigate:/api/state/home" },
                            "filter-input": { "type": "INPUT", "path": "Search employees...", "action": "filter" },
                            "emp-1": { "type": "ITEM", "path": "Alice Chevrier - Architecture", "action": "none" },
                            "emp-2": { "type": "ITEM", "path": "Bob Smith - Marketing", "action": "none" },
                            "emp-3": { "type": "ITEM", "path": "Charlie Davis - Design", "action": "none" },
                            "emp-4": { "type": "ITEM", "path": "Diana Ross - Product", "action": "none" },
                            "emp-5": { "type": "ITEM", "path": "Evan Wright - Engineering", "action": "none" }
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