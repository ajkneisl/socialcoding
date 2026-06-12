package com.socialcoding.config

import java.io.File

/** Runtime configuration, resolved from environment variables with a .env fallback. */
data class AppConfig(
    val databaseUrl: String,
    val databaseDriver: String,
    val databaseUser: String,
    val databasePassword: String,
    val googleClientId: String,
    val jwtSecret: String,
    val boardEmails: Set<String>,
) {
  companion object {

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

    fun load(): AppConfig {
      val dotenv = loadDotEnv()
      fun env(key: String, default: String) = System.getenv(key) ?: dotenv[key] ?: default

      val jwtSecret = env("JWT_SECRET", "")
      require(jwtSecret.isNotBlank()) {
        "JWT_SECRET is not set — add it to backend/.env (see .env.example) or the environment"
      }

      return AppConfig(
          databaseUrl = env("DATABASE_URL", "jdbc:h2:file:./data/socialcoding;MODE=PostgreSQL"),
          databaseDriver = env("DATABASE_DRIVER", "org.h2.Driver"),
          databaseUser = env("DATABASE_USER", "sa"),
          databasePassword = env("DATABASE_PASSWORD", ""),
          googleClientId = env("GOOGLE_CLIENT_ID", ""),
          jwtSecret = jwtSecret,
          boardEmails =
              env("BOARD_EMAILS", "")
                  .split(',')
                  .map { it.trim().lowercase() }
                  .filter { it.isNotEmpty() }
                  .toSet(),
      )
    }
  }
}
