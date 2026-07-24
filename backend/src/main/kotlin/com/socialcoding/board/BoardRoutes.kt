package com.socialcoding.board

import com.socialcoding.board.routes.BOARD_PROJECTS
import com.socialcoding.board.routes.DECIDE
import com.socialcoding.board.routes.GET_SETTINGS
import com.socialcoding.board.routes.LIST_MEMBERS
import com.socialcoding.board.routes.SET_ROLE
import com.socialcoding.board.routes.SET_SETTINGS
import com.socialcoding.board.routes.SET_TITLE
import com.socialcoding.board.routes.SYNC_MILESTONES
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** Board-only review queue and project activation. */
fun Route.boardRoutes() {
    authenticate("session") {
        route("/board") {
            get("/projects", BOARD_PROJECTS)
            get("/settings", GET_SETTINGS)
            put("/settings", SET_SETTINGS)
            get("/members", LIST_MEMBERS)
            put("/members/{id}/role", SET_ROLE)
            put("/members/{id}/title", SET_TITLE)
            post("/projects/sync-milestones", SYNC_MILESTONES)
            post("/projects/{id}/{decision}", DECIDE)
        }
    }
}
