package com.socialcoding

import com.socialcoding.api.Environment
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HealthTest {

    /** Liveness takes no dependencies, so it answers on its own. */
    @Test
    fun `health endpoint is unauthenticated and dependency free`() = testApplication {
        application { rootModule() }

        assertEquals(HttpStatusCode.OK, client.get("/health").status)
    }

    /**
     * The build stamp actually reaches the response.
     *
     * `processResources` writes `version.properties` to the resources root, so the lookup has to
     * be absolute. A package-relative one just misses and reports "unknown", which is what shipped
     * to production unnoticed until it was read off a live `/health`.
     */
    @Test
    fun `health endpoint reports the build version`() = testApplication {
        application { rootModule() }

        val body = client.get("/health").bodyAsText()
        assertFalse(body.contains(""""version":"unknown""""), "version.properties was not found: $body")
        assertTrue(body.contains(""""version":"${Environment.VERSION}""""), body)
        assertNotEquals("unknown", Environment.VERSION)
    }

    /** Readiness reports the database round trip by name. */
    @Test
    fun `ready endpoint reports the database check`() = testApplication {
        application { rootModule() }
        TestDatabase.connect()

        val response = client.get("/ready")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"database":true}""", response.bodyAsText())
    }
}
