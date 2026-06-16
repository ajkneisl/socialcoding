package com.socialcoding.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.socialcoding.Environment
import java.util.Date

/** Handles JWT */
object Auth {
    private val SECRET = Environment.getVariable("JWT_SECRET")
    private val ALGO = Algorithm.HMAC256(SECRET)
    private const val ISSUER = "socalcoding"

    val verifier: JWTVerifier = JWT.require(ALGO).withIssuer(ISSUER).build()

    /** Issue a JWT to [userID]. */
    fun issue(userID: Long): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withSubject(userID.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000))
            .sign(ALGO)
}
