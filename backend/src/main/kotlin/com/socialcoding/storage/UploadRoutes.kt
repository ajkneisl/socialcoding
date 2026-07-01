package com.socialcoding.storage

import com.socialcoding.common.ApiError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable

private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
private const val IMAGE_CACHE_CONTROL = "public, max-age=31536000, immutable"

/** Authenticated image uploads plus public read-through serving of the object store. */
fun Route.uploadRoutes() {
    // GET /api/images/{path...}
    // publicly stream a stored image; lets private buckets (e.g. Garage) be served without ACLs
    get("/images/{path...}") {
        val key = call.parameters.getAll("path")?.joinToString("/").orEmpty()
        if (!key.startsWith(ObjectStorage.KEY_PREFIX) || key.contains("..")) {
            return@get call.respond(HttpStatusCode.NotFound, ApiError("Image not found."))
        }

        val obj = ObjectStorage.getObject(key)
        if (obj == null) {
            return@get call.respond(HttpStatusCode.NotFound, ApiError("Image not found."))
        }

        call.response.headers.append(HttpHeaders.CacheControl, IMAGE_CACHE_CONTROL)
        call.respondBytes(obj.bytes, ContentType.parse(obj.contentType))
    }

    authenticate("session") {
        route("/uploads") {
            /** The stored image, ready to drop into an event or project's `imageUrl`. */
            @Serializable data class UploadResponse(val url: String)

            // POST /api/uploads/image
            // accept a single multipart image file and return the URL to display it at
            post("/image") {
                if (!ObjectStorage.isConfigured) {
                    return@post call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ApiError("Image uploads aren't configured on this server."),
                    )
                }

                var url: String? = null
                var rejection: String? = null
                call.receiveMultipart().forEachPart { part ->
                    if (part is PartData.FileItem && url == null && rejection == null) {
                        val contentType = part.contentType?.toString().orEmpty()
                        val bytes = part.provider().readRemaining().readByteArray()
                        rejection =
                            when {
                                bytes.isEmpty() -> "The uploaded file was empty."
                                bytes.size > MAX_IMAGE_BYTES ->
                                    "Images must be smaller than ${MAX_IMAGE_BYTES / (1024 * 1024)} MB."
                                !ObjectStorage.supportsContentType(contentType) ->
                                    "Unsupported image type. Use PNG, JPEG, WebP, GIF, or SVG."
                                else -> null
                            }
                        if (rejection == null) url = ObjectStorage.uploadImage(bytes, contentType)
                    }
                    part.dispose()
                }

                val finalUrl = url
                val finalRejection = rejection
                when {
                    finalRejection != null ->
                        call.respond(HttpStatusCode.BadRequest, ApiError(finalRejection))
                    finalUrl == null ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiError("No image file was provided."),
                        )
                    else -> call.respond(UploadResponse(finalUrl))
                }
            }
        }
    }
}
