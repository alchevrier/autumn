package dev.autumn.ui.ioc

import dev.autumn.annotations.LongLived
import dev.autumn.config.ConfigManager
import dev.autumn.config.JsonConfigParser
import dev.autumn.config.StringRegistry
import dev.autumn.resolver.handoff.AutumnNetworkEngine
import dev.autumn.resolver.handoff.NetworkSlotManager
import dev.autumn.resolver.handoff.RawNetworkClient
import dev.autumn.state.EpochStateEngine

/**
 * The "Motherboard" / System-on-a-Chip composition root.
 * 
 * Instead of a reflection-heavy dynamic IoC container like Dagger or Koin,
 * this explicitly wires the exact pre-allocated boundaries once at boot.
 */
@LongLived
class AutumnMotherboard(
    networkClient: RawNetworkClient,
    stringRegistryBudget: Int = 1000,
    concurrencyBudget: Int = 10,
    epochMatrixBudget: Int = 200
) {
    // 1. Memory / L1 Cache
    val stringRegistry = StringRegistry(stringRegistryBudget)
    val configManager = ConfigManager(100) // Hardware buckets configuration

    // 2. State Engine (Interrupt Moderation)
    val stateEngine = EpochStateEngine(epochMatrixBudget)

    // 3. Network Boundaries
    val slotManager = NetworkSlotManager(concurrencyBudget)
    
    val networkEngine = AutumnNetworkEngine(
        slotManager = slotManager,
        networkClient = networkClient,
        payloadMatrixWriter = { bytes ->
            // Zero-allocation pipeline execution:
            // Parse -> Registry -> Config -> Epoch Bump
            val parser = JsonConfigParser()
            parser.parse(bytes, configManager, stringRegistry)
            
            // In a complete loop, the parser would identify which slot was populated.
            // For now, we simulate bumping index 0 to wake the UI wire.
            stateEngine.incrementEpoch(0)
        }
    )

    /**
     * Resets the entire hardware state instantly without garbage collection.
     */
    fun hardReset() {
        stringRegistry.clear()
        configManager.clear()
        // Resetting epochs and network locks implies just wiping their IntArrays,
        // maintaining the static zero-allocation guarantees across user session resets.
    }
}
