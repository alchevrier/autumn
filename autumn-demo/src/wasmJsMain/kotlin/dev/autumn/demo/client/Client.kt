package dev.autumn.demo.client

import dev.autumn.annotations.LongLived
import dev.autumn.ui.ioc.AutumnMotherboard
import dev.autumn.resolver.handoff.RawNetworkClient
import dev.autumn.ui.AutumnCircuitBinder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@LongLived
class WasmFetchNetworkClient : RawNetworkClient {
    override suspend fun executeRaw(endpoint: String, method: String, requestBody: ByteArray?): Result<ByteArray> {
        return suspendCoroutine { continuation ->
            val promise = window.fetch(endpoint)
            promise.then { response ->
                if (response.ok) {
                    response.text().then { text ->
                        continuation.resume(Result.success(text.toString().encodeToByteArray()))
                        text
                    }
                } else {
                    continuation.resumeWithException(Exception("HTTP error: ${response.status}"))
                    response
                }
            }
        }
    }
}

var currentFilter: String = ""
var filterTimeout: Int = 0

class DemoCircuitBinder(
    private val motherboard: AutumnMotherboard
) : AutumnCircuitBinder(
    stateEngine = motherboard.stateEngine,
    stringRegistry = motherboard.stringRegistry
) {
    var currentFocusId: String? = null

    fun renderTo(elementId: String) {
        val root = document.getElementById(elementId) ?: return
        
        var html = "<div style=\"max-width: 600px; margin: 0 auto; padding: 20px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\">"
        
        val config = motherboard.configManager
        val registry = motherboard.stringRegistry
        
        val count = config.resources.size
        for (i in 0 until count) {
            val res = config.resources[i]
            val key = registry.getString(res.resourceId)
            val type = registry.getString(res.typeId)
            val path = registry.getString(res.pathRefId)
            val action = registry.getString(res.actionId)

            when (type.uppercase()) {
                "TITLE" -> html += "<h1 style=\"color: #2c3e50; margin-bottom: 10px;\">$path</h1>"
                "TEXT" -> html += "<p style=\"color: #34495e; line-height: 1.6; margin-bottom: 20px;\">$path</p>"
                "INPUT" -> html += "<input id=\"autumn-search\" type=\"text\" placeholder=\"$path\" value=\"$currentFilter\" oninput=\"autumnFilter(this.value)\" style=\"width: 100%; padding: 10px; margin-bottom: 20px; border: 1px solid #ccc; border-radius: 4px; font-size: 16px;\"/>"
                "ITEM" -> html += "<div style=\"padding: 15px; border-bottom: 1px solid #eee; color: #2c3e50;\">👉 $path</div>"
                "BUTTON" -> {
                    if (action.startsWith("navigate:")) {
                        val target = action.substringAfter("navigate:")
                        html += "<button onclick=\"autumnNavigate('$target')\" style=\"background-color: #3498db; color: white; border: none; padding: 10px 20px; font-size: 16px; border-radius: 4px; cursor: pointer; margin-right: 10px; margin-bottom: 20px;\">$path</button>"
                    } else {
                        html += "<button onclick=\"window.alert('Circuit Action Dispatched: $action')\" style=\"background-color: #2ecc71; color: white; border: none; padding: 10px 20px; font-size: 16px; border-radius: 4px; cursor: pointer; margin-right: 10px; margin-bottom: 20px;\">$path</button>"
                    }
                }
            }
        }
        
        html += "</div>"
        root.innerHTML = html

        if (currentFocusId != null) {
            val el = document.getElementById(currentFocusId!!) as? HTMLInputElement
            if (el != null) {
                el.focus()
                val len = el.value.length
                el.setSelectionRange(len, len)
            }
        }
    }
}

@LongLived
val motherboard = AutumnMotherboard(
    networkClient = WasmFetchNetworkClient(),
    stringRegistryBudget = 0,
    concurrencyBudget = 4,
    epochMatrixBudget = 2,
    configBucketsBudget = 100
)

@LongLived
val binder = DemoCircuitBinder(motherboard)

fun main() {
    binder.attachToInterruptWire(GlobalScope) {
        binder.renderTo("root")
    }

    GlobalScope.launch {
        console.log("Executing WebAssembly network request...")
        val slot = motherboard.networkEngine.claimSlot()
        if (slot >= 0) {
            motherboard.networkEngine.executeInPlace(slot, "/api/state/home", "GET", null)
        }
    }
}

@JsExport
fun autumnNavigate(target: String) {
    if (!target.contains("query=")) {
        currentFilter = ""
    }
    GlobalScope.launch {
        motherboard.hardReset()
        val slot = motherboard.networkEngine.claimSlot()
        if (slot >= 0) {
            motherboard.networkEngine.executeInPlace(slot, target, "GET", null)
        }
    }
}

@JsExport
fun autumnFilter(query: String) {
    currentFilter = query
    binder.currentFocusId = "autumn-search"
    window.clearTimeout(filterTimeout)
    filterTimeout = window.setTimeout({
        GlobalScope.launch {
            motherboard.hardReset()
            val slot = motherboard.networkEngine.claimSlot()
            if (slot >= 0) {
                val encoded = encodeURIComponent(query)
                motherboard.networkEngine.executeInPlace(slot, "/api/state/list?query=${encoded}", "GET", null)
            }
        }
    }, 300)
}
