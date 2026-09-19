package com.socialcoding.people

import com.socialcoding.Fixtures
import com.socialcoding.TestDatabase
import com.socialcoding.decode
import com.socialcoding.rootModule
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeopleRoutesTest {

    @BeforeTest fun clean() = TestDatabase.reset()

    private fun ApplicationTestBuilder.boot() {
        application { rootModule() }
        TestDatabase.connect()
    }

    @Test
    fun `people directory is public and lists only listed members`() = testApplication {
        boot()
        Fixtures.user(name = "Visible Vera")
        Fixtures.user(name = "Hidden Hank", listed = false)

        val response = client.get("/api/people")
        assertEquals(HttpStatusCode.OK, response.status)
        val names = response.decode<List<User>>().map { it.name }
        assertTrue(names.contains("Visible Vera"))
        assertFalse(names.contains("Hidden Hank"), "unlisted members are excluded")
    }

    @Test
    fun `the directory never exposes email addresses`() = testApplication {
        boot()
        Fixtures.user(name = "Vera", email = "vera@test.umn.edu")

        val people = client.get("/api/people").decode<List<User>>()
        assertTrue(people.isNotEmpty())
        assertTrue(people.all { it.email.isEmpty() }, "emails are blanked for the public listing")
    }

    @Test
    fun `the directory is ordered by name`() = testApplication {
        boot()
        Fixtures.user(name = "Zoe")
        Fixtures.user(name = "Ada")
        Fixtures.user(name = "Mia")

        val names = client.get("/api/people").decode<List<User>>().map { it.name }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun `profile fields the member filled in are published`() = testApplication {
        boot()
        Fixtures.user(name = "Ada", gradYear = 2027, avatarUrl = "https://example.test/ada.png")

        val ada = client.get("/api/people").decode<List<User>>().single { it.name == "Ada" }
        assertEquals(2027, ada.gradYear)
        assertEquals("https://example.test/ada.png", ada.avatarUrl)
    }
}
