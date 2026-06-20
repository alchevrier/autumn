package dev.autumn.demo.client

import dev.autumn.annotations.LongLived
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

class DemoCircuitBinder(
    private val motherboard: AutumnMotherboard
) : AutumnCircuitBinder(
    stateEngine = motherboard.stateEngine,
    stringRegistry = motherboard.stringRegistry
) {
    fun renderTo(elementId: String) {
        val root = document.getElementById(elementId) ?: return
        
        // Build the HTML by looking straight at the config buckets without allocating DTOs!
        val config = motherboard.configManager
        val registry = motherboard.stringRegistry

        var html = ""
        html += "<div style=\"font-family: monospace; padding: 20px; border: 2px solid green;\">"
        html += "<h2 style=\"color: green;\">[Circuit Pulse Received]</h2>"

        val count = config.resources.size
        for (i in 0 until count) {
            val res = config.resources[i]
            val key = registry.getString(res.resourceId)
            val type = registry.getString(res.typeId)
            val path = registry.getString(res.pathRefId)
            val action = registry.getString(res.actionId)

            html += "<div><strong>ID:</strong> ${key}<br/><strong>Type:</strong> ${type}<br/><strong>Path:</strong> ${path}<br/><strong>Action:</strong> ${action}</div><hr/>"
        }
        html += "</div>"
        
        root.innerHTML = html
    }
}

@LongLived
fun main() {
    console.log("Autumn JS Client Started")
    val root = document.getElementById("root")
    if (root != null) {
        root.innerHTML = "<h3>Waking up hardware matrix...</h3>"
    }

    // 1. Initialize the Motherboard with the native JS Fetch client limit bounds
    val motherboard = AutumnMotherboard(
        networkClient = JsFetchNetworkClient(),
        stringRegistryBudget = 100,
        concurrencyBudget = 5,
        epochMatrixBudget = 10
    )

    val binder = DemoCircuitBinder(motherboard)

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
            motherboard.networkEngine.executeInPlace(slot, "/api/state", "GET", null)
        } else {
            console.log("Circuit breaker tripped: Budget Exhausted")
        }
    }
}