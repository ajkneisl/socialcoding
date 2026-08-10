package com.socialcoding.user

import com.socialcoding.Fixtures
import com.socialcoding.TestDatabase
import com.socialcoding.decode
import com.socialcoding.people.Role
import com.socialcoding.people.User
import com.socialcoding.rootModule
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class UserRoutesTest {

    @BeforeTest fun clean() = TestDatabase.reset()

    @Test
    fun `google login rejects a request with no credential`() = testApplication {
        application { rootModule() }
        TestDatabase.connect()

        // No `credential` field: the request never reaches Google and is rejected.
        val response =
            client.post("/api/auth/google") {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
        assertTrue(response.status.value >= 400, "malformed login should be an error")
    }

    @Test
    fun `me requires authentication`() = testApplication {
        application { rootModule() }
        TestDatabase.connect()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/me").status)
    }

    @Test
    fun `me returns the signed-in user`() = testApplication {
        application { rootModule() }
        TestDatabase.connect()
        val id = Fixtures.user(name = "Ada")

        val response = client.get("/api/me") { bearerAuth(Fixtures.token(id)) }
        assertEquals(HttpStatusCode.OK, response.status)
        val user = response.decode<User>()
        assertEquals(id.toString(), user.id)
        assertEquals("Ada", user.name)
        assertEquals(Role.MEMBER, user.role)
    }

    @Test
    fun `update me writes profile fields and toggles listing`() = testApplication {
        application { rootModule() }
        TestDatabase.connect()
        val id = Fixtures.user()
        val token = Fixtures.token(id)

        val updated =
            client.post("/api/me") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(
                    """{"gradYear": 2027, "github": "octocat", "company": "Acme", "listed": false}""")
            }
        assertEquals(HttpStatusCode.OK, updated.status)
        val user = updated.decode<User>()
        assertEquals(2027, user.gradYear)
        assertEquals("octocat", user.github)
        assertEquals("Acme", user.company)
        assertEquals(false, user.listed)

        // Omitting `listed` leaves it unchanged; nulls clear the other optional fields.
        val cleared =
            client.post("/api/me") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
        val afterClear = cleared.decode<User>()
        assertNull(afterClear.gradYear)
        assertNull(afterClear.github)
        assertEquals(false, afterClear.listed, "listed is left as-is when omitted")
    }

    @Test
    fun `update me requires authentication`() = testApplication {
        application { rootModule() }
        TestDatabase.connect()
        val response =
            client.post("/api/me") {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `a token that isn't ours is turned away`() = testApplication {
        application { rootModule() }
        TestDatabase.connect()

        listOf("not-a-token", "", Fixtures.token(Uuid.random()).dropLast(4)).forEach { token ->
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("/api/me") { bearerAuth(token) }.status,
                "token: $token",
            )
        }
    }

    @Test
    fun `a valid token for a deleted account is not found`() = testApplication {
        application { rootModule() }
        TestDatabase.connect()

        // The signature checks out, but there's no row behind the subject any more.
        val response = client.get("/api/me") { bearerAuth(Fixtures.token(Uuid.random())) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `a member cannot promote themselves through their profile`() = testApplication {
        application { rootModule() }
        TestDatabase.connect()
        val id = Fixtures.user(role = Role.MEMBER)

        // `role` isn't part of the profile payload, so an extra key is ignored rather than applied.
        val user =
            client
                .post("/api/me") {
                    bearerAuth(Fixtures.token(id))
                    contentType(ContentType.Application.Json)
                    setBody("""{"role": "BOARD", "github": "octocat"}""")
                }
                .decode<User>()

        assertEquals(Role.MEMBER, user.role)
        assertEquals("octocat", user.github)
    }

    @Test
    fun `a malformed profile body is rejected`() = testApplication {
        application { rootModule() }
        TestDatabase.connect()
        val token = Fixtures.token(Fixtures.user())

        val response =
            client.post("/api/me") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody("""{"gradYear": "not a year"}""")
            }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `google login rejects a malformed body`() = testApplication {
        application { rootModule() }
        TestDatabase.connect()

        val response =
            client.post("/api/auth/google") {
                contentType(ContentType.Application.Json)
                setBody("this is not json")
            }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
