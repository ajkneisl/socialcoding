package com.socialcoding.storage.routes

import com.socialcoding.common.APIError
import com.socialcoding.api.ObjectStorage
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.RoutingContext

private const val IMAGE_CACHE_CONTROL = "public, max-age=31536000, immutable"

val SERVE_IMAGE: suspend RoutingContext.() -> Unit = handler@{
    val key = call.parameters.getAll("path")?.joinToString("/").orEmpty()
    if (!key.startsWith(ObjectStorage.KEY_PREFIX) || key.contains("..")) {
        return@handler call.respond(
            HttpStatusCode.NotFound,
            APIError("Image not found."),
        )
    }

    val obj =
        ObjectStorage.getObject(key)
            ?: return@handler call.respond(
                HttpStatusCode.NotFound,
                APIError("Image not found."),
            )

    call.response.headers.append(HttpHeaders.CacheControl, IMAGE_CACHE_CONTROL)
    call.respondBytes(obj.bytes, ContentType.parse(obj.contentType))
}
