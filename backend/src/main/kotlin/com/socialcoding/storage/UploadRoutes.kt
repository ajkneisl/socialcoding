package com.socialcoding.storage

import com.socialcoding.storage.routes.SERVE_IMAGE
import com.socialcoding.storage.routes.UPLOAD_IMAGE
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Authenticated image uploads plus public read-through serving of the object
 * store.
 */
fun Route.uploadRoutes() {
    get("/images/{path...}", SERVE_IMAGE)

    authenticate("session") {
        route("/uploads") { post("/image", UPLOAD_IMAGE) }
    }
}
