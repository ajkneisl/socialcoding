package com.socialcoding.api

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Session tokens are the only thing standing between a request and someone else's account. */
class AuthTest {

    /** Matches the `JWT_SECRET` the `test` task exports, so hand-rolled tokens are signed alike. */
    private val secret = "dev-only-secret-change-me"

    @Test
    fun `a token it issued verifies, carrying the user id as the subject`() {
        val userID = Uuid.random().toString()

        val decoded = Auth.verifier.verify(Auth.issue(userID))

        assertEquals(userID, decoded.subject)
        assertEquals("socalcoding", decoded.issuer)
    }

    @Test
    fun `tokens expire about a week out`() {
        val decoded = Auth.verifier.verify(Auth.issue(Uuid.random().toString()))
        val week = 7L * 24 * 60 * 60 * 1000

        val lifetime = decoded.expiresAt.time - System.currentTimeMillis()
        assertTrue(lifetime in (week - 60_000)..week, "expected roughly a week, got ${lifetime}ms")
    }

    @Test
    fun `a token signed with another secret is rejected`() {
        val forged =
            JWT.create()
                .withIssuer("socalcoding")
                .withSubject(Uuid.random().toString())
                .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.HMAC256("some-other-secret"))

        assertFailsWith<JWTVerificationException> { Auth.verifier.verify(forged) }
    }

    @Test
    fun `a token from a different issuer is rejected`() {
        val foreign =
            JWT.create()
                .withIssuer("somewhere-else")
                .withSubject(Uuid.random().toString())
                .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.HMAC256(secret))

        assertFailsWith<JWTVerificationException> { Auth.verifier.verify(foreign) }
    }

    @Test
    fun `an expired token is rejected`() {
        val stale =
            JWT.create()
                .withIssuer("socalcoding")
                .withSubject(Uuid.random().toString())
                .withExpiresAt(Date(System.currentTimeMillis() - 1_000))
                .sign(Algorithm.HMAC256(secret))

        assertFailsWith<JWTVerificationException> { Auth.verifier.verify(stale) }
    }

    @Test
    fun `garbage is rejected rather than parsed`() {
        assertFailsWith<JWTVerificationException> { Auth.verifier.verify("not.a.token") }
    }

    @Test
    fun `two users get distinguishable tokens`() {
        val first = Auth.issue(Uuid.random().toString())
        val second = Auth.issue(Uuid.random().toString())
        assertTrue(first != second)
    }
}
