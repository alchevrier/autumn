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

import dev.autumn.annotations.InjectTopology

@InjectTopology
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
                        "schemaVersion": "1.0.0",
                        "schemaDate": "2026-06-20",
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
                val query = call.request.queryParameters["query"]?.takeIf { it.isNotBlank() } ?: ""
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = 5
                val allEmployees = listOf(
                    "Alice Chevrier - Architecture", "Bob Smith - Marketing", "Charlie Davis - Design",
                    "Diana Ross - Product", "Evan Wright - Engineering", "Fiona Gallagher - HR",
                    "George Best - Sales", "Hannah Abbott - Finance", "Ian Somerhalder - Legal",
                    "Jack Bauer - IT", "Kelly Kapoor - Support", "Liam Gallagher - Ops",
                    "Monica Geller - QA", "Ned Stark - Security", "Olivia Pope - PR"
                )
                
                val filtered = allEmployees.filter { it.contains(query, ignoreCase = true) }
                val totalPages = maxOf(1, (filtered.size + pageSize - 1) / pageSize)
                val paged = filtered.drop((page - 1) * pageSize).take(pageSize)
                
                val itemsJson = paged.mapIndexed { index, emp ->
                    """"emp-$index": { "type": "ITEM", "path": "$emp", "action": "none" }"""
                }.joinToString(",\n                            ")
                
                val prevBtn = if (page > 1) 
                    """"prev-btn": { "type": "BUTTON", "path": "← Prev Page", "action": "navigate:/api/state/list?page=${page - 1}&query=$query" },""" 
                else ""
                    
                val nextBtn = if (page < totalPages) 
                    """"next-btn": { "type": "BUTTON", "path": "Next Page →", "action": "navigate:/api/state/list?page=${page + 1}&query=$query" },""" 
                else ""

                call.respondText(
                    """
                    {
                        "schemaVersion": "1.0.0",
                        "schemaDate": "2026-06-20",
                        "resources": {
                            "list-title": { "type": "TITLE", "path": "Employee Directory (Page $page of $totalPages)", "action": "none" },
                            "back-btn": { "type": "BUTTON", "path": "← Back to Home", "action": "navigate:/api/state/home" },
                            "filter-input": { "type": "INPUT", "path": "Search $query...", "action": "filter" },
                            $prevBtn
                            $nextBtn
                            ${if (itemsJson.isNotEmpty()) itemsJson else """"empty": { "type": "TEXT", "path": "No employees found.", "action": "none" }"""}
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