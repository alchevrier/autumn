package dev.autumn.admin

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class AutumnAdminApplicationTest {

    @BeforeTest
    fun setup() {
        KeyRepository.resetForTest()
        KeyRepository.provide("test-id-1", "Test Key 1", "ak_live_x892njkasd891")
        KeyRepository.provide("test-id-revoked", "Revoked Key", "ak_live_revoked99999")
        KeyRepository.revokeById("test-id-revoked")
    }

    @Test
    fun `test get keys returns safe metadata without secrets`() = testApplication {
        application { module() }

        val response = client.get("/keys")
        assertEquals(HttpStatusCode.OK, response.status)

        val bodyText = response.bodyAsText()
        assertTrue(bodyText.contains("test-id-1"), "Should contain active key ID")
        assertTrue(bodyText.contains("Test Key 1"), "Should contain active key name")
        assertFalse(bodyText.contains("ak_live_x892njkasd891"), "Should NOT expose the actual secret in public endpoint")
    }

    @Test
    fun `test internal keys returns secrets for bff`() = testApplication {
        application { module() }

        val response = client.get("/internal/keys")
        assertEquals(HttpStatusCode.OK, response.status)

        val bodyText = response.bodyAsText()
        assertTrue(bodyText.contains("ak_live_x892njkasd891"), "Should expose secrets for internal sync")
        assertTrue(bodyText.contains("ak_live_revoked99999"), "Should expose revoked secrets for internal sync")
    }

    @Test
    fun `test generate key creates and exposes secret ONCE`() = testApplication {
        application { module() }

        val response = client.post("/keys/generate") {
           setBody("{\"name\":\"New UI Key\"}")
        }
        assertEquals(HttpStatusCode.Created, response.status)

        val bodyText = response.bodyAsText()
        // We get the secret precisely once in this payload
        assertTrue(bodyText.contains("ak_live_"), "Payload should contain the new secret key")
        assertTrue(bodyText.contains("New UI Key"), "Payload should bind the correct name")
        val generatedId = parseId(bodyText)
        
        assertTrue(KeyRepository.activeRecords.any { it.id == generatedId }, "Generated key should be active in repository")
    }

    @Test
    fun `test revoke key uses ID and marks it as revoked`() = testApplication {
        application { module() }

        val response = client.delete("/keys/revoke") {
            setBody("{\"id\":\"test-id-1\"}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(KeyRepository.activeRecords.none { it.id == "test-id-1" }, "Key should be removed from active")
        assertTrue(KeyRepository.revokedRecords.any { it.id == "test-id-1" }, "Key should now be revoked")
    }
}
