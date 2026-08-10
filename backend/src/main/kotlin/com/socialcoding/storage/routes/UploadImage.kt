package com.socialcoding.storage.routes

import com.socialcoding.common.APIError
import com.socialcoding.api.ObjectStorage
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable

@Serializable private data class UploadResponse(val url: String)

private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024

val UPLOAD_IMAGE: suspend RoutingContext.() -> Unit = handler@{
    if (!ObjectStorage.isConfigured) {
        return@handler call.respond(
            HttpStatusCode.ServiceUnavailable,
            APIError("Image uploads aren't configured on this server."),
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
            if (rejection == null)
                url = ObjectStorage.uploadImage(bytes, contentType)
        }
        part.dispose()
    }

    val finalUrl = url
    val finalRejection = rejection
    when {
        finalRejection != null ->
            call.respond(HttpStatusCode.BadRequest, APIError(finalRejection))
        finalUrl == null ->
            call.respond(
                HttpStatusCode.BadRequest,
                APIError("No image file was provided."),
            )
        else -> call.respond(UploadResponse(finalUrl))
    }
}
