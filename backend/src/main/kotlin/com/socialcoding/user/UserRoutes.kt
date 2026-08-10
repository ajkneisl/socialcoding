package com.socialcoding.user

import com.socialcoding.user.routes.GOOGLE_LOGIN
import com.socialcoding.user.routes.ME
import com.socialcoding.user.routes.UPDATE_ME
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
