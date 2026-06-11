package com.socialcoding

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.util.Date
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AuthException(message: String) : RuntimeException(message)

data class GoogleIdentity(
    val sub: String,
    val email: String,
    val name: String,
    val picture: String?,
)

/**
 * Verifies a Google ID token (the `credential` from Google Identity Services) and enforces that
 * the account belongs to the University of Minnesota Google Workspace.
 */
class GoogleVerifier(
    private val clientId: String,
    private val allowedDomain: String = "umn.edu",
    private val httpClient: HttpClient = HttpClient(CIO),
) {

  suspend fun verify(idToken: String): GoogleIdentity {
    val response =
        httpClient.get("https://oauth2.googleapis.com/tokeninfo") {
          parameter("id_token", idToken)
        }
    if (!response.status.isSuccess()) throw AuthException("Invalid Google credential")

    val claims = Json.parseToJsonElement(response.bodyAsText()).jsonObject
    fun claim(key: String) = claims[key]?.jsonPrimitive?.content

    if (claim("aud") != clientId) throw AuthException("Credential issued for a different app")
    if (claim("email_verified") != "true") throw AuthException("Email is not verified")

    val email = claim("email") ?: throw AuthException("Credential is missing an email")
    val hostedDomain = claim("hd")
    if (hostedDomain != allowedDomain && !email.endsWith("@$allowedDomain")) {
      throw AuthException("A University of Minnesota (@$allowedDomain) account is required")
    }

    return GoogleIdentity(
        sub = claim("sub") ?: throw AuthException("Credential is missing a subject"),
        email = email,
        name = claim("name") ?: email.substringBefore('@'),
        picture = claim("picture"),
    )
  }
}

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
