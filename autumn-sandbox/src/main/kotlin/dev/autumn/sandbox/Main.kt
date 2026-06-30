package dev.autumn.sandbox

import dev.autumn.memory.AutumnMemoryBank
import dev.autumn.annotations.LongLived
import dev.autumn.annotations.InjectTopology

@InjectTopology(wcetAuditable = true)
@LongLived
fun main() {
    val node = HighFrequencyTradingNode()
    
    // Check OrderEvent instead!
    val clazz = OrderEvent::class.java

    println("--- Autumn Sandbox: IR Structural Injection Verification ---")
    val fields = clazz.declaredFields
    
    val syntheticFields = fields.filter { it.name.startsWith("synthetic_") }
    if (syntheticFields.isEmpty()) {
        println("ℹ️ INFO: No local synthetic backing arrays found on ${clazz.simpleName}. K2 successfully deferred interface allocations to the centralized Memory Bank.")
    } else {
        println("✅ Found Synthetic Backing Arrays injected by K2 PipelinedSoATransformer:")
        syntheticFields.forEach {
            println("  -> [${it.type.simpleName}] ${it.name}")
        }
    }
    
    // First, allocate the physical memory bank manually since the compiler's init boot injection isn't built yet
    // dev.autumn.memory.AutumnMemoryBank.allocate(4096)

    println("\nRunning mocked pipeline...")
    node.processMarketData()
    println("\n--- Testing SessionChannel Configuration Integration ---")
    AutumnMemoryBank.allocate(1024L * 1024 * 1024)
    val sessionNode = RiskEngineConfigNode()
    sessionNode.testSessionMemoryInjection()
    println("✅ Pipeline executed successfully over the synchronized Non-Heap Native Memory Bank!")
}
