package com.socialcoding

import com.socialcoding.common.ServerError
import io.github.cdimascio.dotenv.Dotenv

object Environment {
    private val envVar: Map<String, String> by lazy { System.getenv() }
    private val dotenv: Dotenv? by lazy {
        try {
            Dotenv.load()
        } catch (_: Exception) {
            null
        }
    }

    private val env = getVariable("ENV", "DEV")

    val isProduction
        get() = env == "PROD"

    val isDev
        get() = env == "DEV"

    /** Get an environment variable by its [key], or [default] if empty. */
    fun getVariable(key: String, default: String): String {
        if (envVar.containsKey(key)) return envVar.getValue(key)

        return dotenv?.get(key) ?: default
    }

    /** Get an environment variable by its [key], or throw a [ServerError] if empty. */
    fun getVariable(key: String): String {
        if (envVar.containsKey(key)) return envVar.getValue(key)

        return dotenv?.get(key) ?: throw ServerError("Missing environment variable $key")
    }
}
