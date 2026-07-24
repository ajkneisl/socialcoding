package com.socialcoding.events

import com.socialcoding.events.routes.ANALYTICS
import com.socialcoding.events.routes.ATTEND
import com.socialcoding.events.routes.ATTENDANCE
import com.socialcoding.events.routes.CREATE_EVENT
import com.socialcoding.events.routes.DELETE_EVENT
import com.socialcoding.events.routes.LIST_EVENTS
import com.socialcoding.events.routes.UPDATE_EVENT
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** Public event listing plus board-only creation, editing, and removal. */
fun Route.eventRoutes() {
    get("/events", LIST_EVENTS)

    authenticate("session") {
        route("/events") {
            post(CREATE_EVENT)
            put("/{id}", UPDATE_EVENT)
            delete("/{id}", DELETE_EVENT)
            post("/{id}/attend", ATTEND)
            get("/{id}/attendance", ATTENDANCE)
            get("/analytics", ANALYTICS)
        }
    }
}
