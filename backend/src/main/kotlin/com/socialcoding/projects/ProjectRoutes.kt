package com.socialcoding.projects

import com.socialcoding.projects.routes.ACCEPT_INVITE
import com.socialcoding.projects.routes.CREATE_PROJECT
import com.socialcoding.projects.routes.DECLINE_INVITE
import com.socialcoding.projects.routes.GET_PROJECT
import com.socialcoding.projects.routes.INVITES
import com.socialcoding.projects.routes.LIKE
import com.socialcoding.projects.routes.LIST_PROJECTS
import com.socialcoding.projects.routes.MY_PROJECTS
import com.socialcoding.projects.routes.PRESENTATION_DATES
import com.socialcoding.projects.routes.RESUBMIT
import com.socialcoding.projects.routes.SHOWCASE
import com.socialcoding.projects.routes.UPDATE_DESIGN
import com.socialcoding.projects.routes.UPDATE_MEMBERS
import com.socialcoding.projects.routes.UPDATE_SEMESTER_DOC
import com.socialcoding.projects.routes.UPDATE_TASKS
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

/** Public project listing plus the authenticated design doc lifecycle. */
fun Route.projectRoutes() {
    // list every approved project, ordered by hearts; like state is filled in when signed in
    authenticate("session", optional = true) {
        get("/projects", LIST_PROJECTS)
        get("/projects/{id}/showcase", SHOWCASE)
    }

    authenticate("session") {
        post("/projects", CREATE_PROJECT)
        get("/projects/mine", MY_PROJECTS)
        get("/projects/presentation-dates", PRESENTATION_DATES)
        get("/projects/invites", INVITES)
        post("/projects/{id}/invite/accept", ACCEPT_INVITE)
        post("/projects/{id}/invite/decline", DECLINE_INVITE)
        get("/projects/{id}", GET_PROJECT)
        post("/projects/{id}/resubmit", RESUBMIT)
        post("/projects/{id}/like", LIKE)
        put("/projects/{id}/design", UPDATE_DESIGN)
        put("/projects/{id}/semester-doc", UPDATE_SEMESTER_DOC)
        put("/projects/{id}/members", UPDATE_MEMBERS)
        put("/projects/{id}/tasks", UPDATE_TASKS)
    }
}
