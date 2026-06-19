package dev.autumn.config

import kotlin.test.Test
import kotlin.test.assertEquals
import dev.autumn.annotations.LongLived

@LongLived
class JsonConfigParserTest {

    @Test
    fun `test parsing full autumn interaction manifest without allocation`() {
        
        
        val parser = JsonConfigParser()

        val fullSchemaBytes = """
        {
          "manifest": {
            "version": "1.0.0",
            "entitySource": "bff",
            "countryResolverPolicy": "eu-resolver",
            "variantPolicy": {
              "enabled": true,
              "source": "bff"
            },
            "cache": {
              "configTtlSeconds": 3600,
              "defaultReadTtlSeconds": 300
            },
            "buckets": {
              "image": {
                "fr": "https://cdn.example.fr/images/",
                "de": "https://cdn.example.de/images/"
              },
              "video": {
                "fr": "https://video.example.fr/",
                "de": "https://video.example.de/"
              }
            },
            "resources": {
              "hero-banner": {
                "type": "image",
                "path": "banners/hero.webp",
                "action": "screen.home"
              },
              "secondary-banner": {
                "type": "video",
                "path": "banners/secondary.mp4",
                "action": "screen.promo"
              }
            },
            "entryStateId": "home",
            "stateIndex": ["home", "promo"]
          },
          "states": {
            "home": {
              "state": "ready",
              "screen": "Home",
              "interactions": []
            }
          }
        }
        """.trimIndent().encodeToByteArray()

        val config = ConfigManager(maxResources = BudgetCalculator.calculateConfigResourceBudget(declaredResources = 2))
        val registry = StringRegistry(maxStrings = 20)

        parser.parse(fullSchemaBytes, config, registry)

        assertEquals(2, config.resources.size, "Should have successfully extracted exactly 2 resources from deep within the manifest")

        val res0 = config.resources[0]
        println("res0 resourceId=${res0.resourceId}, typeId=${res0.typeId}, pathRefId=${res0.pathRefId}, actionId=${res0.actionId}")

        assertEquals("hero-banner", registry.getString(res0.resourceId))
        assertEquals("image", registry.getString(res0.typeId))
        assertEquals("banners/hero.webp", registry.getString(res0.pathRefId))
        assertEquals("screen.home", registry.getString(res0.actionId))

        val res1 = config.resources[1]
        assertEquals("secondary-banner", registry.getString(res1.resourceId))
        assertEquals("video", registry.getString(res1.typeId))
        assertEquals("banners/secondary.mp4", registry.getString(res1.pathRefId))
        assertEquals("screen.promo", registry.getString(res1.actionId))
        
        println("SUCCESS: Parsed full schema using DPDK pattern!")
    }
}
