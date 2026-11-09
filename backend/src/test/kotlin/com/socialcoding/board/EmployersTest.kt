package com.socialcoding.board

import com.socialcoding.Fixtures
import com.socialcoding.TestDatabase
import com.socialcoding.decode
import com.socialcoding.people.Role
import com.socialcoding.people.User
import com.socialcoding.rootModule
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class EmployersTest {

    @BeforeTest fun clean() = TestDatabase.reset()

    private fun ApplicationTestBuilder.boot() {
        application { rootModule() }
        TestDatabase.connect()
    }

    private suspend fun ApplicationTestBuilder.visibleNames(): List<String> =
        client.get("/api/site/employers").decode()

    @Test
    fun `a company on a profile is listed for the board but hidden from the public`() =
        testApplication {
            boot()
            val board = Fixtures.token(Fixtures.user(role = Role.BOARD, company = "Acme"))

            val listed =
                client.get("/api/board/employers") { bearerAuth(board) }.decode<List<Employer>>()

            assertEquals(listOf(Employer("acme", "Acme", false, 1)), listed)
            assertEquals(emptyList(), visibleNames())
        }

    @Test
    fun `showing a profile employer publishes it and hiding withdraws it`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD, company = "Acme"))

        suspend fun setVisible(visible: Boolean) =
            client.put("/api/board/employers") {
                bearerAuth(board)
                contentType(ContentType.Application.Json)
                setBody("""{"name": "Acme", "visible": $visible}""")
            }

        assertEquals(true, setVisible(true).decode<List<Employer>>().single().visible)
        assertEquals(listOf("Acme"), visibleNames())

        assertEquals(false, setVisible(false).decode<List<Employer>>().single().visible)
        assertEquals(emptyList(), visibleNames())
    }

    @Test
    fun `an added employer starts hidden and re-adding leaves its visibility alone`() =
        testApplication {
            boot()
            val board = Fixtures.token(Fixtures.user(role = Role.BOARD))

            suspend fun add(name: String) =
                client.post("/api/board/employers") {
                    bearerAuth(board)
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "$name"}""")
                }

            assertEquals(
                listOf(Employer("twosigma", "Two Sigma", false, 0)),
                add("  Two Sigma ").decode(),
            )

            EmployerDirectory.setVisible("Two Sigma", true)
            assertEquals(true, add("twosigma").decode<List<Employer>>().single().visible)
        }

    @Test
    fun `an employer with no letters or digits is refused`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))

        val response =
            client.post("/api/board/employers") {
                bearerAuth(board)
                contentType(ContentType.Application.Json)
                setBody("""{"name": " -- "}""")
            }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `spellings of one employer merge and unlisted members don't count`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD, company = "Two Sigma"))
        Fixtures.user(company = "two sigma")
        Fixtures.user(company = "TwoSigma")
        Fixtures.user(company = "Secret Co", listed = false)

        val listed =
            client.get("/api/board/employers") { bearerAuth(board) }.decode<List<Employer>>()

        assertEquals(listOf(Employer("twosigma", "Two Sigma", false, 3)), listed)
    }

    @Test
    fun `removing an employer still on a profile returns it to hidden`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD, company = "Acme"))
        EmployerDirectory.setVisible("Acme", true)

        val listed =
            client.delete("/api/board/employers/acme") { bearerAuth(board) }
                .decode<List<Employer>>()

        assertEquals(false, listed.single().visible)
        assertEquals(emptyList(), visibleNames())
    }

    @Test
    fun `employer management is board only`() = testApplication {
        boot()
        val member = Fixtures.token(Fixtures.user())

        assertEquals(
            HttpStatusCode.InternalServerError,
            client.get("/api/board/employers") { bearerAuth(member) }.status,
        )
        assertEquals(
            HttpStatusCode.InternalServerError,
            client.delete("/api/board/employers/acme") { bearerAuth(member) }.status,
        )
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/board/employers").status)
    }

    @Test
    fun `the seed keeps what was public and runs only once`() = testApplication {
        boot()
        Fixtures.user(company = "Acme")
        Fixtures.user(company = "tesla")
        Fixtures.user(company = "Secret Co", listed = false)

        transaction { EmployerDirectory.seedOnce() }

        val names = EmployerDirectory.visibleNames()
        assertContains(names, "Tesla", "the original spelling wins over a profile's")
        assertContains(names, "Acme", "a profile employer that was already public stays public")
        assertFalse("tesla" in names)
        assertFalse("Secret Co" in names)

        // Anything the board does afterwards, and anyone joining later, isn't overridden.
        EmployerDirectory.setVisible("Tesla", false)
        Fixtures.user(company = "Newcomer")
        transaction { EmployerDirectory.seedOnce() }

        val after = EmployerDirectory.visibleNames()
        assertFalse("Tesla" in after)
        assertFalse("Newcomer" in after)
    }

    @Test
    fun `the board can edit a member's profile`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        val memberId = Fixtures.user()

        val response =
            client.put("/api/board/members/$memberId/profile") {
                bearerAuth(board)
                contentType(ContentType.Application.Json)
                setBody("""{"gradYear": 2027, "company": "Acme", "listed": false}""")
            }

        assertEquals(HttpStatusCode.OK, response.status)
        val user = response.decode<User>()
        assertEquals(2027, user.gradYear)
        assertEquals("Acme", user.company)
        assertEquals(false, user.listed)
    }

    @Test
    fun `a member can't edit someone else's profile`() = testApplication {
        boot()
        val member = Fixtures.token(Fixtures.user())
        val otherId = Fixtures.user()

        val response =
            client.put("/api/board/members/$otherId/profile") {
                bearerAuth(member)
                contentType(ContentType.Application.Json)
                setBody("""{"company": "Acme"}""")
            }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }
}
