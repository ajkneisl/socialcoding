package com.socialcoding

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthTest {

    /** Liveness takes no dependencies, so it answers on its own. */
    @Test
    fun `health endpoint is unauthenticated and dependency free`() = testApplication {
        application { rootModule() }

        assertEquals(HttpStatusCode.OK, client.get("/health").status)
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
