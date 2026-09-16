package com.socialcoding.api

import com.socialcoding.common.ServerError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
import org.slf4j.LoggerFactory

@Initialize
object ObjectStorage : Initializable {
    private val log = LoggerFactory.getLogger(javaClass)

    private const val SERVICE = "s3"

    const val KEY_PREFIX = "uploads/"

    private val AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd")

    private val httpClient = HttpClient(CIO)

    private var endpoint = ""
    private var region = "us-east-1"
    private var bucket = ""
    private var accessKey = ""
    private var secretKey = ""
    private var acl = ""
    private var publicBase = ""

    private val host: String
        get() {
            val authority = endpoint.substringAfter("://").substringBefore('/')
            val defaultPort = if (endpoint.startsWith("http://")) ":80" else ":443"
            return authority.removeSuffix(defaultPort)
        }

    class StoredObject(val bytes: ByteArray, val contentType: String)

    /** Whether enough of the environment is set for uploads to work. */
    val isConfigured: Boolean
        get() =
            endpoint.isNotBlank() &&
                bucket.isNotBlank() &&
                accessKey.isNotBlank() &&
                secretKey.isNotBlank()

    /**
     * Every extension this store writes, and the content type a key with that extension is served
     * back as.
     *
     * The upload gate and the serve-side type are both read out of this one map, in opposite
     * directions, so the two can't drift apart.
     */
    private val CONTENT_TYPES =
        mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "webp" to "image/webp",
            "gif" to "image/gif",
            "svg" to "image/svg+xml",
        )

    /** Types browsers send that are really one of [CONTENT_TYPES] under another name. */
    private val TYPE_ALIASES = mapOf("image/jpg" to "jpg")

    /** A content type stripped of parameters and casing, as the maps key it. */
    private fun normalize(contentType: String): String =
        contentType.substringBefore(';').trim().lowercase()

    /**
     * The stored file extension for an image [contentType], or null if it isn't a supported type.
     */
    private fun extensionFor(contentType: String): String? {
        val type = normalize(contentType)
        return CONTENT_TYPES.entries.firstOrNull { it.value == type }?.key ?: TYPE_ALIASES[type]
    }

    /** Whether [contentType] is an image type this store accepts. */
    fun supportsContentType(contentType: String): Boolean = extensionFor(contentType) != null

    /**
     * The content type to serve a stored [key] as, from the extension [uploadImage] gave it, or
     * null if the key isn't one this store wrote.
     *
     * Reading the type off our own key means a proxied response never repeats whatever type the
     * bucket claims for the bytes.
     */
    fun contentTypeForKey(key: String): String? =
        CONTENT_TYPES[key.substringAfterLast('.', "").lowercase()]

    /**
     * Store [bytes] as an image of [contentType] under a random key and return the URL to display
     * it at — a direct link when `S3_PUBLIC_URL` is set, otherwise the backend proxy path.
     *
     * @throws com.socialcoding.common.ServerError if storage isn't configured, the type is
     *   unsupported, or the store rejects the upload.
     */
    suspend fun uploadImage(bytes: ByteArray, contentType: String): String {
        if (!isConfigured) throw ServerError("Image uploads aren't configured on this server.")
        val extension =
            extensionFor(contentType) ?: throw ServerError("Unsupported image type: $contentType")

        val key = "$KEY_PREFIX${Uuid.random()}.$extension"
        putObject(key, bytes, normalize(contentType))
        return if (publicBase.isNotBlank()) "$publicBase/$key" else "/api/images/$key"
    }

    /** PUT an object into the bucket using path-style addressing, signed with AWS Signature V4. */
    private suspend fun putObject(
        key: String,
        bytes: ByteArray,
        contentType: String,
    ) {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val amzDate = now.format(AMZ_DATE)
        val dateStamp = now.format(DATE_STAMP)
        val payloadHash = hex(sha256(bytes))

        val signed =
            sortedMapOf(
                "content-type" to contentType,
                "host" to host,
                "x-amz-content-sha256" to payloadHash,
                "x-amz-date" to amzDate,
            )
        if (acl.isNotBlank()) signed["x-amz-acl"] = acl

        val path = canonicalPath(key)
        val authorization = authorization("PUT", path, signed, payloadHash, amzDate, dateStamp)

        val response =
            httpClient.put("$endpoint$path") {
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
        val response = signedGet(key)

        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw ServerError("Object store returned ${response.status.value} for $key")
        }
        val contentType = response.headers[HttpHeaders.ContentType] ?: "application/octet-stream"
        return StoredObject(response.body<ByteArray>(), contentType)
    }

    /**
     * Whether the store is answering signed requests right now, for the `/health` check.
     *
     * The probe asks for a key that shouldn't exist, so a healthy store replies
     * 404. A 403 counts as reachable too: a bucket policy granting `s3:GetObject` without
     *      `s3:ListBucket` answers 403 rather than 404 for a missing key, and reporting a correctly
     *      locked-down bucket as down would be worse than missing a bad key. Only a transport
     *      failure or a 5xx marks the store as down.
     */
    suspend fun isReachable(): Boolean {
        return isConfigured &&
            try {
                val status = signedGet(healthcheckKey()).status
                if (status.value >= 500) {
                    log.warn(
                        "Object storage health check got {} from {}",
                        status.value,
                        endpoint,
                    )
                    false
                } else {
                    true
                }
            } catch (e: Exception) {
                log.warn("Object storage health check failed", e)
                false
            }
    }

    /** A key no upload can collide with, for probing the store. */
    private fun healthcheckKey(): String = "$KEY_PREFIX.healthcheck-${Uuid.random()}"

    /**
     * GET an object from the bucket using path-style addressing, signed with AWS Signature V4. The
     * response status is left for the caller to read.
     */
    private suspend fun signedGet(key: String): HttpResponse {
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
        val path = canonicalPath(key)
        val authorization = authorization("GET", path, signed, payloadHash, amzDate, dateStamp)

        return httpClient.get("$endpoint$path") {
            header("x-amz-content-sha256", payloadHash)
            header("x-amz-date", amzDate)
            header(HttpHeaders.Authorization, authorization)
        }
    }

    /**
     * Build the AWS Signature V4 `Authorization` header for a request. [signed] must contain
     * exactly the headers the request sends, keyed by lowercase name; a sorted map keeps them
     * canonical.
     */
    private fun authorization(
        method: String,
        canonicalUri: String,
        signed: Map<String, String>,
        payloadHash: String,
        amzDate: String,
        dateStamp: String,
    ): String {
        val canonicalHeaders = signed.entries.joinToString("") { "${it.key}:${it.value}\n" }
        val signedHeaderList = signed.keys.joinToString(";")
        val canonicalRequest =
            listOf(
                    method,
                    canonicalUri,
                    "",
                    canonicalHeaders,
                    signedHeaderList,
                    payloadHash,
                )
                .joinToString("\n")

        val scope = "$dateStamp/$region/$SERVICE/aws4_request"
        val stringToSign =
            listOf(
                    "AWS4-HMAC-SHA256",
                    amzDate,
                    scope,
                    hex(sha256(canonicalRequest.toByteArray())),
                )
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

    /**
     * The request path for [key], encoded once per segment exactly as SigV4 wants it.
     *
     * Signing and the request itself both go through here, so the canonical URI can never drift
     * from the path on the wire. It also makes a hostile key harmless: once encoded, a segment
     * holds nothing but unreserved characters and `%` escapes, so a `?` or `#` in a key can't open
     * a query or fragment.
     */
    private fun canonicalPath(key: String): String =
        "/$bucket/" + key.split('/').joinToString("/") { uriEncode(it) }

    /** RFC 3986 encoding S3 expects for a path segment (unreserved characters stay literal). */
    private fun uriEncode(segment: String): String =
        URLEncoder.encode(segment, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")

    override suspend fun initialize() {
        endpoint = Environment.getVariable("S3_ENDPOINT", "").trim().trimEnd('/')
        region = Environment.getVariable("S3_REGION", "us-east-1").trim()
        bucket = Environment.getVariable("S3_BUCKET", "").trim()
        accessKey = Environment.getVariable("S3_ACCESS_KEY", "").trim()
        secretKey = Environment.getVariable("S3_SECRET_KEY", "").trim()
        acl = Environment.getVariable("S3_ACL", "").trim()
        publicBase = Environment.getVariable("S3_PUBLIC_URL", "").trim().trimEnd('/')

        if (!isConfigured) {
            log.warn("Object storage is not configured; image uploads are disabled.")
            return
        }

        runCatching { signedGet(healthcheckKey()).status }
            .onSuccess { status ->
                if (status == HttpStatusCode.NotFound || status.isSuccess())
                    log.info("Object storage initialized for bucket {} at {}", bucket, endpoint)
                else
                    log.warn(
                        "Object storage answered {} probing a missing key in bucket {}; " +
                            "expected 404 unless the bucket policy withholds s3:ListBucket.",
                        status.value,
                        bucket,
                    )
            }
            .onFailure { log.error("Object storage is configured but unreachable.", it) }
    }
}
