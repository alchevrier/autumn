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

class AutumnAdminApplicationTest {

    @BeforeTest
    fun setup() {
        KeyRepository.activeKeys.clear()
        KeyRepository.activeKeys.add("ak_live_x892njkasd891")
        KeyRepository.revokedKeys.clear()
        KeyRepository.revokedKeys.add("ak_live_revoked99999")
    }

    @Test
    fun `test get keys returns current state`() = testApplication {
        application { module() }

        val response = client.get("/keys")
        assertEquals(HttpStatusCode.OK, response.status)

        val bodyText = response.bodyAsText()
        assertTrue(bodyText.contains("ak_live_x892njkasd891"), "Should contain active key")
        assertTrue(bodyText.contains("ak_live_revoked99999"), "Should contain revoked key")
    }

    @Test
    fun `test provide key adds to active and removes from revoked`() = testApplication {
        application { module() }

        val response = client.post("/keys/provide") {
            setBody("{\"key\":\"ak_live_revoked99999\"}")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(KeyRepository.activeKeys.contains("ak_live_revoked99999"), "Key should now be active")
        assertTrue(!KeyRepository.revokedKeys.contains("ak_live_revoked99999"), "Key should be removed from revoked")
    }

    @Test
    fun `test revoke key removes from active and adds to revoked`() = testApplication {
        application { module() }

        val response = client.delete("/keys/revoke") {
            setBody("{\"key\":\"ak_live_x892njkasd891\"}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(!KeyRepository.activeKeys.contains("ak_live_x892njkasd891"), "Key should be removed from active")
        assertTrue(KeyRepository.revokedKeys.contains("ak_live_x892njkasd891"), "Key should now be revoked")
    }
}
