package com.socialcoding.api

import com.socialcoding.api.db.Database
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.util.date.getTimeMillis
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable

/**
 * Manages the health endpoints.
 *
 * All three are unauthenticated: `/health` and `/ready` at the root for the load balancer, and
 * `/api/health` for the detailed report of every dependency check.
 */
object Health {
    /**
     * A response from the health endpoint.
     *
     * @param version The version of the backend.
     * @param data All checks that occurred.
     */
    @Serializable
    private data class HealthResponse(
        val version: String,
        val data: Map<String, Boolean>,
    )

    /** A response from the unauthenticated liveness probe. */
    @Serializable private data class LiveResponse(val status: String, val version: String)

    /** A response from the unauthenticated readiness probe. */
    @Serializable private data class ReadyResponse(val database: Boolean)

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
        val now = getTimeMillis()
        val cached = lastCheck
        val data =
            if (cached == null || now - cached.first > CHECK_PERIOD) {
                performCheck().also { lastCheck = now to it }
            } else {
                cached.second
            }

        call.respond(HealthResponse(version = Environment.VERSION, data = data))
    }

    /** The detailed report, under `/api`. */
    fun Route.healthEndpoints() {
        get("/health", ROUTE)
    }

    /**
     * Unauthenticated probes, mounted at the root rather than under `/api`.
     *
     * `/health` answers as long as the process is serving and touches nothing external, which is
     * what the load balancer checks; `/ready` reports whether the database is actually answering,
     * which is what decides if this instance should receive traffic.
     */
    fun Route.probeEndpoints() {
        get("/health") {
            call.respond(HttpStatusCode.OK, LiveResponse("ok", Environment.VERSION))
        }

        get("/ready") { call.respond(ReadyResponse(database = Database.isReachable())) }
    }

    /** Perform the checks. */
    private suspend fun performCheck(): Map<String, Boolean> {
        return checks.map { (key, value) -> key to value() }.toMap()
    }
}
