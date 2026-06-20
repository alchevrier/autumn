package dev.autumn.demo.client

import dev.autumn.annotations.LongLived
import dev.autumn.annotations.InjectBudget
import dev.autumn.annotations.NetworkConcurrencyBudget
import dev.autumn.ui.ioc.AutumnMotherboard
import dev.autumn.resolver.handoff.RawNetworkClient
import dev.autumn.ui.AutumnCircuitBinder
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch

@LongLived
class JsFetchNetworkClient : RawNetworkClient {
    override suspend fun executeRaw(endpoint: String, method: String, requestBody: ByteArray?): Result<ByteArray> {
        return try {
            val response = window.fetch(endpoint).await()
            if (!response.ok) throw Exception("HTTP error: ${response.status}")
            val text = response.text().await()
            Result.success(text.encodeToByteArray())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Global filter state simulating native form boundaries
var currentFilter: String = ""

class DemoCircuitBinder(
    private val motherboard: AutumnMotherboard
) : AutumnCircuitBinder(
    stateEngine = motherboard.stateEngine,
    stringRegistry = motherboard.stringRegistry
) {
    fun renderTo(elementId: String) {
        val root = document.getElementById(elementId) ?: return
        
        val currentFocusId = document.activeElement?.id?.takeIf { it.isNotEmpty() }
        
        // Build the HTML by looking straight at the config buckets without allocating DTOs!
        val config = motherboard.configManager
        val registry = motherboard.stringRegistry

        var html = ""
        html += "<div style=\"font-family: sans-serif; padding: 40px; max-width: 600px; margin: 0 auto; border: 2px solid #ccc; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);\">"
        html += "<div style=\"color: #666; font-size: 0.8em; margin-bottom: 20px; text-transform: uppercase; letter-spacing: 1px;\">🔌 Hardware Wire Connected</div>"

        val count = config.resources.size
        for (i in 0 until count) {
            val res = config.resources[i]
            val key = registry.getString(res.resourceId)
            val type = registry.getString(res.typeId)
            val path = registry.getString(res.pathRefId)
            val action = registry.getString(res.actionId)

            when (type.uppercase()) {
                "TITLE" -> {
                    html += "<h1 style=\"color: #2c3e50; margin-bottom: 10px;\">$path</h1>"
                }
                "TEXT" -> {
                    html += "<p style=\"color: #34495e; line-height: 1.6; margin-bottom: 20px;\">$path</p>"
                }
                "INPUT" -> {
                    html += "<input id=\"autumn-search\" type=\"text\" placeholder=\"$path\" value=\"$currentFilter\" oninput=\"window.autumnFilter(this.value)\" style=\"width: 100%; padding: 10px; margin-bottom: 20px; border: 1px solid #ccc; border-radius: 4px; font-size: 16px;\"/>"
                }
                "ITEM" -> {
                    // Filter is applied securely on the backend now. Zero-alloc mapping displays directly.
                    html += "<div style=\"padding: 15px; border-bottom: 1px solid #eee; color: #2c3e50;\">👉 $path</div>"
                }
                "BUTTON" -> {
                    if (action.startsWith("navigate:")) {
                        val target = action.substringAfter("navigate:")
                        val onClick = "onclick=\"window.autumnNavigate('$target')\""
                        html += "<button $onClick style=\"background-color: #3498db; color: white; border: none; padding: 10px 20px; font-size: 16px; border-radius: 4px; cursor: pointer; margin-right: 10px; margin-bottom: 20px;\">$path</button>"
                    } else {
                        val onClick = "onclick=\"window.alert('Circuit Action Dispatched: $action')\""
                        html += "<button $onClick style=\"background-color: #2ecc71; color: white; border: none; padding: 10px 20px; font-size: 16px; border-radius: 4px; cursor: pointer; margin-right: 10px; margin-bottom: 20px;\">$path</button>"
                    }
                }
                else -> {
                    // Fallback for unknown types
                    html += "<div style=\"font-family: monospace; font-size: 12px; color: #7f8c8d; margin-top: 20px; padding: 10px; background: #f8f9fa;\">"
                    html += "<strong>Unknown Component ($key):</strong> $type | $path | $action"
                    html += "</div>"
                }
            }
        }
        
        // Debug pane showing the matrix layout
        html += "<hr style=\"margin-top: 40px; border: 0; border-top: 1px solid #eee;\"/>"
        html += "<h4 style=\"color: #95a5a6; font-family: monospace;\">Raw Memory Matrix (0-alloc)</h4>"
        html += "<pre style=\"font-size: 10px; color: #bdc3c7; background: #2c3e50; padding: 10px; border-radius: 4px; overflow-x: auto;\">"
        html += "Active Registry Strings: ${registry.count}\\n"
        html += "Active Config Buckets: ${config.resources.size}\\n"
        html += "</pre>"
        
        html += "</div>"
        
        root.innerHTML = html

        if (currentFocusId != null) {
            val el = document.getElementById(currentFocusId) as? org.w3c.dom.HTMLInputElement
            if (el != null) {
                el.focus()
                val len = el.value.length
                el.setSelectionRange(len, len)
            }
        }
    }
}

@LongLived
fun main() {
    console.log("Autumn JS Client Started")
    val root = document.getElementById("root")
    if (root != null) {
        root.innerHTML = "<h3>Waking up hardware matrix...</h3>"
    }

    // 1. Compile-Time Memory Matrix Definitions
    // The exact structural limits of the 'Employee List' view are used to cap memory allocation.

    @NetworkConcurrencyBudget(maxInFlightRequests = 2)
    val maxNetworkCalls = 2 // 1 active list fetch + 1 pending debounce

    @InjectBudget(singleEntityCount = 5, paginatedCollectionCount = 1, slotsPerPage = 5)
    val bucketConfigLimits = 10 // 5 static UI nodes (title, buttons, search) + (1 list * 5 items)

    @InjectBudget(entityScope = "StringPointers", singleEntityCount = 40)
    val stringRegistryLimits = 40 // 4 string pointers (key, type, action, path) per bucket * 10 buckets

    // 2. Initialize the Motherboard with the native JS Fetch client limit bounds
    val motherboard = AutumnMotherboard(
        networkClient = JsFetchNetworkClient(),
        stringRegistryBudget = stringRegistryLimits,
        concurrencyBudget = maxNetworkCalls,
        epochMatrixBudget = 2,
        configBucketsBudget = bucketConfigLimits
    )

    val binder = DemoCircuitBinder(motherboard)
    var filterTimeout: Int = 0

    // Bridge JS Native events to Autumn circuit triggers
    window.asDynamic().autumnNavigate = { target: String ->
        if (!target.contains("query=")) {
            currentFilter = "" // Reset filter state on new context
        }
        GlobalScope.launch {
            motherboard.hardReset() // Flush buckets cleanly
            val slot = motherboard.networkEngine.claimSlot()
            if (slot >= 0) {
                motherboard.networkEngine.executeInPlace(slot, target, "GET", null)
            }
        }
    }

    window.asDynamic().autumnFilter = { query: String ->
        currentFilter = query
        window.clearTimeout(filterTimeout)
        filterTimeout = window.setTimeout({
            GlobalScope.launch {
                motherboard.hardReset()
                val slot = motherboard.networkEngine.claimSlot()
                if (slot >= 0) {
                    val encoded = js("window.encodeURIComponent(query)") as String
                    motherboard.networkEngine.executeInPlace(slot, "/api/state/list?query=${encoded}", "GET", null)
                }
            }
        }, 300)
    }

    // 2. Attach to the Epoch Pulse (FSM / Event Loop)
    binder.attachToInterruptWire(GlobalScope) {
        // Redraw strictly on pulse
        binder.renderTo("root")
    }

    // 3. Fire the request through the Network Engine
    // This will fetch, parse into strings/buckets via zero-alloc parser, 
    // and trigger the FSM epoch bump, which will execute the callback above!
    GlobalScope.launch {
        console.log("Executing network request through Autumn Circuit...")
        val slot = motherboard.networkEngine.claimSlot()
        if (slot >= 0) {
            motherboard.networkEngine.executeInPlace(slot, "/api/state/home", "GET", null)
        } else {
            console.log("Circuit breaker tripped: Budget Exhausted")
        }
    }
}