package com.socialcoding.api

import com.socialcoding.api.db.Database
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.util.date.getTimeMillis
import kotlin.time.Duration.Companion.seconds

/**
 * Manages the `/health` endpoint.
 *
 * This endpoint requires authorization using the `HEALTH_CHECK_KEY` environment variable. Include
 * that in the bearer.
 */
object Health {
    /**
     * A response from the health endpoint.
     *
     * @param version The version of the backend.
     * @param data All checks that occurred.
     */
    private data class HealthResponse(
        val version: String,
        val data: Map<String, Boolean>,
    )

    /** Checks that are given via the endpoint. */
    private val checks =
        mapOf<String, suspend () -> Boolean>(
            "Database" to { Database.isReachable() },
            "Object Storage" to { ObjectStorage.isConfigured && ObjectStorage.isReachable() },
        )

    /** The amount of time to cache a check for. */
    private val CHECK_PERIOD = 15.seconds.inWholeMilliseconds

    /**
     * The last cached check, cleared every [CHECK_PERIOD]. First is the date cached, second is the
     * checks themselves.
     */
    var lastCheck: Pair<Long, Map<String, Boolean>>? = null

    /** GET `/api/health` */
    private val ROUTE: suspend RoutingContext.() -> Unit = {
        var check = lastCheck

        if (check != null) {
            val (time, checks) = check

            if (getTimeMillis() - time > CHECK_PERIOD) {
                check = getTimeMillis() to performCheck()
                lastCheck = check
            }
        }

        call.respond(
            HealthResponse(
                version = Environment.VERSION,
                data = check?.second ?: mapOf(),
            )
        )
    }

    /** The endpoint itself with the proper auth. */
    fun Route.healthEndpoints() {
        authenticate("health", optional = false) {
            get("/health", ROUTE)
        }
    }

    /** Perform the checks. */
    private suspend fun performCheck(): Map<String, Boolean> {
        return checks.map { (key, value) -> key to value() }.toMap()
    }
}
