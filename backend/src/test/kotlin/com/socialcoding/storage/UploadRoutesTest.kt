package com.socialcoding.storage

import com.socialcoding.Fixtures
import com.socialcoding.TestDatabase
import com.socialcoding.rootModule
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UploadRoutesTest {

    @BeforeTest fun clean() = TestDatabase.reset()

    /**
     * The test environment has no S3 credentials, so every read misses and the route answers 404
     * whether the key was turned away by its prefix guard or simply isn't in the bucket. These pin
     * the status the client sees; telling the two apart needs a configured store.
     */
    @Test
    fun `serving an image with an invalid key is not found`() = testApplication {
        application { rootModule() }
        // A key outside the uploads prefix never touches storage.
        assertEquals(HttpStatusCode.NotFound, client.get("/api/images/not-an-upload").status)
    }

    @Test
    fun `serving an image is public but finds nothing without a store`() = testApplication {
        application { rootModule() }
        // No session required, and a well-formed key still misses with no bucket behind it.
        assertEquals(HttpStatusCode.NotFound, client.get("/api/images/uploads/missing.png").status)
    }

    @Test
    fun `image upload requires authentication`() = testApplication {
        application { rootModule() }
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/uploads/image").status)
    }

    @Test
    fun `image upload is unavailable when storage is not configured`() = testApplication {
        application { rootModule() }
        TestDatabase.connect()
        val token = Fixtures.token(Fixtures.user())
        // No S3 credentials in the test environment, so the endpoint reports it's unconfigured.
        assertEquals(
            HttpStatusCode.ServiceUnavailable,
            client.post("/api/uploads/image") { bearerAuth(token) }.status)
    }
}
