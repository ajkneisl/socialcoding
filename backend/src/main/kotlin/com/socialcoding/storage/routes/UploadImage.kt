package com.socialcoding.storage.routes

import com.socialcoding.api.ObjectStorage
import com.socialcoding.common.APIError
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable

@Serializable private data class UploadResponse(val url: String)

private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
private const val MAX_REQUEST_BYTES = MAX_IMAGE_BYTES + 64 * 1024L
private const val TOO_LARGE = "Images must be smaller than ${MAX_IMAGE_BYTES / (1024 * 1024)} MB."

val UPLOAD_IMAGE: suspend RoutingContext.() -> Unit = handler@{
    if (!ObjectStorage.isConfigured) {
        return@handler call.respond(
            HttpStatusCode.ServiceUnavailable,
            APIError("Image uploads aren't configured on this server."),
        )
    }

    val declared = call.request.contentLength()
    if (declared != null && declared > MAX_REQUEST_BYTES) {
        return@handler call.respond(
            HttpStatusCode.PayloadTooLarge,
            APIError(TOO_LARGE),
        )
    }

    var url: String? = null
    var rejection: String? = null
    var oversize = false

    call.receiveMultipart().forEachPart { part ->
        if (part is PartData.FileItem && url == null && rejection == null) {
            val contentType = part.contentType?.toString().orEmpty()
            val bytes = part.provider().readRemaining(MAX_IMAGE_BYTES + 1L).readByteArray()
            oversize = bytes.size > MAX_IMAGE_BYTES
            rejection =
                when {
                    bytes.isEmpty() -> "The uploaded file was empty."
                    oversize -> TOO_LARGE
                    !ObjectStorage.supportsContentType(contentType) ->
                        "Unsupported image type. Use PNG, JPEG, WebP, or GIF."
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
            call.respond(
                if (oversize) HttpStatusCode.PayloadTooLarge else HttpStatusCode.BadRequest,
                APIError(finalRejection),
            )

        finalUrl == null ->
            call.respond(
                HttpStatusCode.BadRequest,
                APIError("No image file was provided."),
            )

        else -> call.respond(UploadResponse(finalUrl))
    }
}
