package com.socialcoding.auth

import com.socialcoding.Environment
import com.socialcoding.common.AuthorizationException
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Verify a user's Google identity when signing in with Google. */
object GoogleVerifier {
    private val httpClient: HttpClient = HttpClient(CIO)
    private val clientID by lazy { Environment.getVariable("GOOGLE_CLIENT_ID") }

    /**
     * The response when signing in with Google.
     *
     * @param googleID The unique Google ID.
     * @param email The user's student email.
     * @param name The user's name.
     * @param picture Optional link to the user's profile picture.
     */
    data class GoogleIdentity(
        val googleID: String,
        val email: String,
        val name: String,
        val picture: String?,
    )

    /** Verify a [idToken]. */
    suspend fun verify(idToken: String): GoogleIdentity {
        val response =
            httpClient.get("https://oauth2.googleapis.com/tokeninfo") {
                parameter("id_token", idToken)
            }
        if (!response.status.isSuccess()) throw AuthorizationException("Invalid Google credential")

        val claims = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        fun claim(key: String) = claims[key]?.jsonPrimitive?.content

        if (claim("aud") != clientID)
            throw AuthorizationException("Credential issued for a different app")
        if (claim("email_verified") != "true") throw AuthorizationException("Email is not verified")

        val email = claim("email") ?: throw AuthorizationException("Credential is missing an email")
        val hostedDomain = claim("hd")
        if (hostedDomain != "umn.edu" && !email.endsWith("@umn.edu")) {
            throw AuthorizationException("A University of Minnesota account is required")
        }

        return GoogleIdentity(
            googleID =
                claim("sub") ?: throw AuthorizationException("Credential is missing a subject"),
            email = email,
            name = claim("name") ?: email.substringBefore('@'),
            picture = claim("picture"),
        )
    }
}
