package com.socialcoding

import com.socialcoding.auth.AuthException
import com.socialcoding.auth.GoogleVerifier
import com.socialcoding.auth.SessionTokens
import com.socialcoding.auth.authRoutes
import com.socialcoding.board.boardRoutes
import com.socialcoding.common.ApiError
import com.socialcoding.config.AppConfig
import com.socialcoding.db.DatabaseFactory
import com.socialcoding.people.peopleRoutes
import com.socialcoding.projects.projectRoutes
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
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun Application.rootModule() {
    val config = AppConfig.load()

    DatabaseFactory.init(
        jdbcUrl = config.databaseUrl,
        driver = config.databaseDriver,
        user = config.databaseUser,
        password = config.databasePassword,
    )

    val google = GoogleVerifier(clientId = config.googleClientId)
    val sessions = SessionTokens(secret = config.jwtSecret)

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
        route("/api") {
            authRoutes(google, sessions, config.boardEmails)
            peopleRoutes()
            projectRoutes()
            boardRoutes()
        }
    }
}
