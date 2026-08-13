package com.socialcoding.board

import com.socialcoding.board.routes.BOARD_PROJECTS
import com.socialcoding.board.routes.DECIDE
import com.socialcoding.board.routes.DELETE_PROJECT
import com.socialcoding.board.routes.GET_FOOTER
import com.socialcoding.board.routes.GET_SETTINGS
import com.socialcoding.board.routes.LIST_MEMBERS
import com.socialcoding.board.routes.SET_ROLE
import com.socialcoding.board.routes.SET_SETTINGS
import com.socialcoding.board.routes.SET_TITLE
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** Board only routes, plus the public read of what the board configures for the site. */
fun Route.boardRoutes() {
    // Rendered in the footer on every page, signed in or not.
    get("/site/footer", GET_FOOTER)

    authenticate("session") {
        route("/board") {
            get("/projects", BOARD_PROJECTS)
            get("/settings", GET_SETTINGS)
            put("/settings", SET_SETTINGS)
            get("/members", LIST_MEMBERS)
            put("/members/{id}/role", SET_ROLE)
            put("/members/{id}/title", SET_TITLE)
            post("/projects/{id}/{decision}", DECIDE)
            delete("/projects/{id}", DELETE_PROJECT)
        }
    }
}
