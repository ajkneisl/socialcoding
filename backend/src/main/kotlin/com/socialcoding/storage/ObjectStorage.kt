package com.socialcoding.storage

import com.socialcoding.Environment
import com.socialcoding.common.ServerError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.uuid.Uuid

object ObjectStorage {
    private const val SERVICE = "s3"

    const val KEY_PREFIX = "uploads/"

    private val AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd")

    private val httpClient = HttpClient(CIO)

    private val endpoint by lazy { Environment.getVariable("S3_ENDPOINT", "").trim().trimEnd('/') }
    private val region by lazy { Environment.getVariable("S3_REGION", "us-east-1").trim() }
    private val bucket by lazy { Environment.getVariable("S3_BUCKET", "").trim() }
    private val accessKey by lazy { Environment.getVariable("S3_ACCESS_KEY", "").trim() }
    private val secretKey by lazy { Environment.getVariable("S3_SECRET_KEY", "").trim() }

    private val acl by lazy { Environment.getVariable("S3_ACL", "").trim() }
    private val publicBase by lazy { Environment.getVariable("S3_PUBLIC_URL", "").trim().trimEnd('/') }

    private val host: String
        get() = endpoint.substringAfter("://").substringBefore('/')

    class StoredObject(val bytes: ByteArray, val contentType: String)

    /** Whether enough of the environment is set for uploads to work. */
    val isConfigured: Boolean
        get() =
            endpoint.isNotBlank() &&
                bucket.isNotBlank() &&
                accessKey.isNotBlank() &&
                secretKey.isNotBlank()

    /** The stored file extension for an image [contentType], or null if it isn't a supported type. */
    private fun extensionFor(contentType: String): String? =
        when (contentType.substringBefore(';').trim().lowercase()) {
            "image/png" -> "png"
            "image/jpeg",
            "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/svg+xml" -> "svg"
            else -> null
        }

    /** Whether [contentType] is an image type this store accepts. */
    fun supportsContentType(contentType: String): Boolean = extensionFor(contentType) != null

    /**
     * Store [bytes] as an image of [contentType] under a random key and return the URL to display it
     * at — a direct link when `S3_PUBLIC_URL` is set, otherwise the backend proxy path.
     *
     * @throws ServerError if storage isn't configured, the type is unsupported, or the store
     *   rejects the upload.
     */
    suspend fun uploadImage(bytes: ByteArray, contentType: String): String {
        if (!isConfigured) throw ServerError("Image uploads aren't configured on this server.")
        val extension =
            extensionFor(contentType) ?: throw ServerError("Unsupported image type: $contentType")

        val key = "$KEY_PREFIX${Uuid.random()}.$extension"
        putObject(key, bytes, contentType.substringBefore(';').trim().lowercase())
        return if (publicBase.isNotBlank()) "$publicBase/$key" else "/api/images/$key"
    }

    /** PUT an object into the bucket using path-style addressing, signed with AWS Signature V4. */
    private suspend fun putObject(key: String, bytes: ByteArray, contentType: String) {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val amzDate = now.format(AMZ_DATE)
        val dateStamp = now.format(DATE_STAMP)
        val payloadHash = hex(sha256(bytes))

        // These are exactly the headers the request sends (Host is added by the client from the URL).
        // The ACL header is only present when configured, since not every store accepts it.
        val signed =
            sortedMapOf(
                "content-type" to contentType,
                "host" to host,
                "x-amz-content-sha256" to payloadHash,
                "x-amz-date" to amzDate,
            )
        if (acl.isNotBlank()) signed["x-amz-acl"] = acl

        val authorization = authorization("PUT", key, signed, payloadHash, amzDate, dateStamp)

        val response =
            httpClient.put("$endpoint/$bucket/$key") {
                if (acl.isNotBlank()) header("x-amz-acl", acl)
                header("x-amz-content-sha256", payloadHash)
                header("x-amz-date", amzDate)
                header(HttpHeaders.Authorization, authorization)
                contentType(ContentType.parse(contentType))
                setBody(bytes)
            }

        if (!response.status.isSuccess()) {
            throw ServerError(
                "Object store rejected the upload (${response.status.value}): " +
                    response.bodyAsText().take(300)
            )
        }
    }

    /** Fetch an object by [key], or null if it doesn't exist. */
    suspend fun getObject(key: String): StoredObject? {
        if (!isConfigured) return null
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val amzDate = now.format(AMZ_DATE)
        val dateStamp = now.format(DATE_STAMP)
        val payloadHash = hex(sha256(ByteArray(0)))

        val signed =
            sortedMapOf(
                "host" to host,
                "x-amz-content-sha256" to payloadHash,
                "x-amz-date" to amzDate,
            )
        val authorization = authorization("GET", key, signed, payloadHash, amzDate, dateStamp)

        val response =
            httpClient.get("$endpoint/$bucket/$key") {
                header("x-amz-content-sha256", payloadHash)
                header("x-amz-date", amzDate)
                header(HttpHeaders.Authorization, authorization)
            }

        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw ServerError("Object store returned ${response.status.value} for $key")
        }
        val contentType = response.headers[HttpHeaders.ContentType] ?: "application/octet-stream"
        return StoredObject(response.body<ByteArray>(), contentType)
    }

    /**
     * Build the AWS Signature V4 `Authorization` header for a request. [signed] must contain exactly
     * the headers the request sends, keyed by lowercase name; a sorted map keeps them canonical.
     */
    private fun authorization(
        method: String,
        key: String,
        signed: Map<String, String>,
        payloadHash: String,
        amzDate: String,
        dateStamp: String,
    ): String {
        val canonicalHeaders = signed.entries.joinToString("") { "${it.key}:${it.value}\n" }
        val signedHeaderList = signed.keys.joinToString(";")
        val canonicalUri = "/$bucket/" + key.split('/').joinToString("/") { uriEncode(it) }
        val canonicalRequest =
            listOf(method, canonicalUri, "", canonicalHeaders, signedHeaderList, payloadHash)
                .joinToString("\n")

        val scope = "$dateStamp/$region/$SERVICE/aws4_request"
        val stringToSign =
            listOf("AWS4-HMAC-SHA256", amzDate, scope, hex(sha256(canonicalRequest.toByteArray())))
                .joinToString("\n")

        val signature = hex(hmac(signingKey(dateStamp), stringToSign.toByteArray()))
        return "AWS4-HMAC-SHA256 Credential=$accessKey/$scope, " +
            "SignedHeaders=$signedHeaderList, Signature=$signature"
    }

    /** Derive the SigV4 signing key for [dateStamp]. */
    private fun signingKey(dateStamp: String): ByteArray {
        val kDate = hmac("AWS4$secretKey".toByteArray(), dateStamp.toByteArray())
        val kRegion = hmac(kDate, region.toByteArray())
        val kService = hmac(kRegion, SERVICE.toByteArray())
        return hmac(kService, "aws4_request".toByteArray())
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /** RFC 3986 encoding S3 expects for a path segment (unreserved characters stay literal). */
    private fun uriEncode(segment: String): String =
        URLEncoder.encode(segment, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
}
