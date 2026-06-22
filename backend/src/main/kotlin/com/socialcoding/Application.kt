package com.socialcoding

import com.socialcoding.auth.Auth
import com.socialcoding.auth.authRoutes
import com.socialcoding.board.boardRoutes
import com.socialcoding.common.ApiError
import com.socialcoding.db.ProjectMembers
import com.socialcoding.db.Users
import com.socialcoding.events.EventAttendance
import com.socialcoding.events.Events
import com.socialcoding.events.eventRoutes
import com.socialcoding.people.peopleRoutes
import com.socialcoding.projects.ProjectTasks
import com.socialcoding.projects.Projects
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
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Main entry, finds the port too. */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    initDb()

    embeddedServer(Netty, port = port, host = "0.0.0.0") { rootModule() }.start(wait = true)
}

/** Initialize the database and create tables if necessary. */
fun initDb() {
    Database.connect(
        Environment.getVariable("DB_URL"),
        user = Environment.getVariable("DB_USER"),
        password = Environment.getVariable("DB_PASS"),
    )

    transaction {
        SchemaUtils.create(Users, Projects, ProjectMembers, ProjectTasks, Events, EventAttendance)
    }
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
        allowHost("localhost:5173")
        allowHost("socialcoding.net", schemes = listOf("https"))
        allowHost("www.socialcoding.net", schemes = listOf("https"))
        allowHost("coding.umn.app", schemes = listOf("https"))
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Something went wrong"))
        }
    }

    install(Authentication) {
        jwt("session") {
            realm = "socialcoding"
            verifier(Auth.verifier)
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ApiError("Sign in required"))
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
        }
    }
}
