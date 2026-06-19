package dev.autumn.bff

import dev.autumn.bff.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class AutumnBffApplicationTest {

    @Test
    fun `test missing API Key returns 401 Unauthorized`() = testApplication {
        application { module() }

        val response = client.get("/config")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Missing API Key", response.bodyAsText())
    }

    @Test
    fun `test revoked API Key returns 403 Forbidden`() = testApplication {
        application { module() }

        val response = client.get("/config") {
            header("Authorization", "Bearer ak_live_revoked99999")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("API Key Revoked", response.bodyAsText())
    }

    @Test
    fun `test invalid API Key returns 401 Unauthorized`() = testApplication {
        application { module() }

        val response = client.get("/config") {
            header("Authorization", "Bearer some_random_garbage_key")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Invalid API Key", response.bodyAsText())
    }

    @Test
    fun `test valid API Key with Country override and cohort resolution`() = testApplication {
        application { module() }

        val response = client.get("/config") {
            header("Authorization", "Bearer ak_live_x892njkasd891")
            header("CF-IPCountry", "GB")
            header("X-Device-Id", "test-device-1234")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        
        val bodyText = response.bodyAsText()
        val jsonElement = Json.parseToJsonElement(bodyText).jsonObject
        
        // Assert GB override
        assertEquals("system", jsonElement["ui.theme"]?.run { toString().replace("\"", "") })
        
        // Ensure standard keys are intact
        assertEquals("false", jsonElement["feature.y.enabled"]?.run { toString().replace("\"", "") })
    }

    @Test
    fun `test valid API Key with Fallback Country`() = testApplication {
        application { module() }

        val response = client.get("/config") {
            header("Authorization", "Bearer ak_test_j123n12o3n123")
            // Intentionally omit country headers
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val bodyText = response.bodyAsText()
        val jsonElement = Json.parseToJsonElement(bodyText).jsonObject

        // Should use fallback country US, meaning theme is dark
        assertEquals("dark", jsonElement["ui.theme"]?.run { toString().replace("\"", "") })
    }
}
