package dev.autumn.bff.provider

object ConfigurationProvider {

    // Simulates a master configuration map in the backend
    private val masterConfig = mapOf(
        "ui.theme" to "dark",
        "feature.x.enabled" to "true",
        "feature.y.enabled" to "false"
    )

    private val experiments = listOf(
        Experiment("feature.x.enabled", "false", probability = 0.5) // half users see feature x disabled
    )

    /**
     * Hashes the device ID against cohorts and strips unneeded configurations 
     * out of the master config, returning a purely flat payload tailored for this specific client.
     */
    fun provideForDevice(deviceId: String, countryCode: String): Map<String, String> {
        val payload = masterConfig.toMutableMap()
        
        // Example localized override:
        if (countryCode == "GB") {
            payload["ui.theme"] = "system"
        }

        // Evaluate A/B Experiments deterministically by hash
        experiments.forEach { exp ->
            val hash = (deviceId + exp.key).hashCode()
            if (Math.abs(hash % 100) < (exp.probability * 100)) {
                payload[exp.key] = exp.overrideValue
            }
        }

        return payload
    }

    private data class Experiment(
        val key: String,
        val overrideValue: String,
        val probability: Double
    )
}
