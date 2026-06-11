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
import kotlinx.serialization.json.Json

fun Application.rootModule() {
  val env = { key: String, default: String -> System.getenv(key) ?: default }

  DatabaseFactory.init(
      jdbcUrl = env("DATABASE_URL", "jdbc:h2:file:./data/socialcoding;MODE=PostgreSQL"),
      driver = env("DATABASE_DRIVER", "org.h2.Driver"),
      user = env("DATABASE_USER", "sa"),
      password = env("DATABASE_PASSWORD", ""),
  )

  val google = GoogleVerifier(clientId = env("GOOGLE_CLIENT_ID", ""))
  val sessions = SessionTokens(secret = env("JWT_SECRET", "dev-only-secret-change-me"))
  val boardEmails =
      env("BOARD_EMAILS", "").split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

  install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }

  install(CORS) {
    allowHost("localhost:5173")
    allowHost("socialcoding.net", schemes = listOf("https"))
    allowHost("www.socialcoding.net", schemes = listOf("https"))
    allowHeader(HttpHeaders.ContentType)
    allowHeader(HttpHeaders.Authorization)
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
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
