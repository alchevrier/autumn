package dev.autumn.config

import kotlin.test.Test
import kotlin.test.assertEquals
import dev.autumn.annotations.LongLived

@LongLived
class ConfigManagerTest {

    @Test
    fun `test config resource loading zero allocation`() {
        val config = ConfigManager(maxResources = 100)

        // Simulate JSON parsing feeding integer mappings into the pool
        config.defineResource(typeId = 1, pathRefId = 500, actionId = 99)
        config.defineResource(typeId = 1, pathRefId = 501, actionId = 100)

        assertEquals(2, config.resources.size)

        // Read them out exactly as the state machine or UI would 
        assertEquals(99, config.getResourceAction(0))
        assertEquals(100, config.getResourceAction(1))
    }
}
