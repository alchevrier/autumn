package dev.autumn.bff.provider

import dev.autumn.bff.provider.ConfigurationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ConfigurationProviderTest {

    @Test
    fun `test default provider config without overrides`() {
        val payload = ConfigurationProvider.provideForDevice(deviceId = "user-123", countryCode = "US")
        
        // Assert defaults
        assertNotNull(payload)
        // From master config
        // GB override is 'system', default is 'dark'
        assertEquals("dark", payload["ui.theme"])
    }

    @Test
    fun `test country override`() {
        val payload = ConfigurationProvider.provideForDevice(deviceId = "user-123", countryCode = "GB")
        
        assertEquals("system", payload["ui.theme"])
    }

    @Test
    fun `test deterministic AB testing distribution`() {
        // We know probability is 0.5 for feature.x.enabled
        // We test a large number of device IDs and expect roughly half to be "false" and half to be the default ("true")
        
        var falseCount = 0
        var trueCount = 0

        for (i in 0..999) {
            val deviceId = "device-$i"
            val payload = ConfigurationProvider.provideForDevice(deviceId, "US")
            
            if (payload["feature.x.enabled"] == "false") {
                falseCount++
            } else {
                trueCount++
            }
        }

        // It's a hash distribution, so it shouldn't be exactly 500/500, but close
        // Ensures variability is working.
        assert(falseCount in 400..600) { "Distribution is skewed: falseCount=$falseCount" }
        assert(trueCount in 400..600) { "Distribution is skewed: trueCount=$trueCount" }
        
        // Ensure same device always gets the exact same deterministic result over multiple calls
        val firstCall = ConfigurationProvider.provideForDevice("fixed-user-1", "US")["feature.x.enabled"]
        val secondCall = ConfigurationProvider.provideForDevice("fixed-user-1", "US")["feature.x.enabled"]
        assertEquals(firstCall, secondCall, "Deterministic hashing broke")
    }
}
