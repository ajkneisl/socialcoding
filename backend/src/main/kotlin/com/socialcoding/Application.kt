package com.socialcoding

import com.socialcoding.auth.Auth
import com.socialcoding.auth.authRoutes
import com.socialcoding.board.boardRoutes
import com.socialcoding.common.APIError
import com.socialcoding.events.eventRoutes
import com.socialcoding.people.peopleRoutes
import com.socialcoding.projects.projectRoutes
import com.socialcoding.storage.uploadRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/** Main entry, finds the port too. */
fun main(): Unit = runBlocking {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    runInitializers()

    embeddedServer(Netty, port = port, host = "0.0.0.0") { rootModule() }
        .start(wait = true)
}

/** Ktor root module. */
fun Application.rootModule() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }

    install(CORS) {
        when {
            Environment.isProduction -> {
                allowHost("socialcoding.net", schemes = listOf("https"))
                allowHost("www.socialcoding.net", schemes = listOf("https"))
                allowHost("coding.umn.app", schemes = listOf("https"))
            }

            Environment.isDev -> {
                allowHost("localhost:5173")
            }
        }

        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }

    install(StatusPages) {
        exception<APIError> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause)
        }

        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error", cause)
            cause.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                APIError("Something went wrong"),
            )
        }
    }

    install(Authentication) {
        jwt("session") {
            realm = "socialcoding"
            verifier(Auth.verifier)
            validate { credential ->
                if (credential.payload.subject != null)
                    JWTPrincipal(credential.payload)
                else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    APIError("Sign in required"),
                )
            }
        }
    }

    routing {
        route("/api") {
            authRoutes()
            peopleRoutes()
            projectRoutes()
            boardRoutes()
            eventRoutes()
            uploadRoutes()
        }
    }
}
