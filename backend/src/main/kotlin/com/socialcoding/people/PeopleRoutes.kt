package com.socialcoding.people

import com.socialcoding.people.routes.PEOPLE
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** The public member directory. */
fun Route.peopleRoutes() {
    get("/people", PEOPLE)
}
