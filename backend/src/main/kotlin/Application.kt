package com.socialcoding

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File
import kotlinx.serialization.json.Json

/** Loads KEY=VALUE pairs from a local .env file so secrets stay out of source control. */
private fun loadDotEnv(): Map<String, String> {
    val file =
        listOf(File(".env"), File("backend/.env")).firstOrNull { it.isFile } ?: return emptyMap()
    return file
        .readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
        .associate { line ->
            val (key, value) = line.split('=', limit = 2)
            key.trim() to value.trim().removeSurrounding("\"")
        }
}

fun Application.rootModule() {
    val dotenv = loadDotEnv()
    val env = { key: String, default: String -> System.getenv(key) ?: dotenv[key] ?: default }

    DatabaseFactory.init(
        jdbcUrl = env("DATABASE_URL", "jdbc:h2:file:./data/socialcoding;MODE=PostgreSQL"),
        driver = env("DATABASE_DRIVER", "org.h2.Driver"),
        user = env("DATABASE_USER", "sa"),
        password = env("DATABASE_PASSWORD", ""),
    )

    val google = GoogleVerifier(clientId = env("GOOGLE_CLIENT_ID", ""))
    val jwtSecret = env("JWT_SECRET", "")
    require(jwtSecret.isNotBlank()) {
        "JWT_SECRET is not set — add it to backend/.env (see .env.example) or the environment"
    }
    val sessions = SessionTokens(secret = jwtSecret)
    val boardEmails =
        env("BOARD_EMAILS", "").split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    // encodeDefaults keeps blank design doc answers present in responses instead of omitted.
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }

    install(CORS) {
        allowHost("localhost:5173")
        allowHost("socialcoding.net", schemes = listOf("https"))
        allowHost("www.socialcoding.net", schemes = listOf("https"))
        allowHost("coding.umn.app", schemes = listOf("https"))
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
    }

    install(StatusPages) {
        exception<AuthException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ApiError(cause.message ?: "Unauthorized"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Something went wrong"))
        }
    }

    install(Authentication) {
        jwt("session") {
            realm = "socialcoding"
            verifier(sessions.verifier)
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ApiError("Sign in required"))
            }
        }
    }

    routing {
        get("/") { call.respondText("Social Coding API") }
        apiRoutes(google, sessions, boardEmails.toSet())
    }
}
