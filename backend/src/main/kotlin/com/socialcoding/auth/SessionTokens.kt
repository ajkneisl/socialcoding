package com.socialcoding.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.socialcoding.people.Role
import java.util.Date

/** Issues and verifies the site's own session JWTs after Google sign-in succeeds. */
class SessionTokens(secret: String, private val issuer: String = "socialcoding") {
  private val algorithm = Algorithm.HMAC256(secret)

  val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(issuer).build()

  fun issue(userId: Long, role: Role): String =
      JWT.create()
          .withIssuer(issuer)
          .withSubject(userId.toString())
          .withClaim("role", role.name)
          .withExpiresAt(Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000))
          .sign(algorithm)
}
