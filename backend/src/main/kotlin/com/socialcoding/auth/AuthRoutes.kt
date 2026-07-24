package com.socialcoding.auth

import com.socialcoding.auth.routes.GOOGLE_LOGIN
import com.socialcoding.auth.routes.ME
import com.socialcoding.auth.routes.UPDATE_ME
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** Sign-in and the signed-in user's own profile (`/auth/google`, `/me`). */
fun Route.authRoutes() {
    post("/auth/google", GOOGLE_LOGIN)

    authenticate("session") {
        get("/me", ME)
        post("/me", UPDATE_ME)
    }
}
