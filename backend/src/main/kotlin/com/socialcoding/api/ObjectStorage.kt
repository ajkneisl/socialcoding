package com.socialcoding.api

import com.socialcoding.common.ServerError
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

/**
 * Image storage, in the Amazon S3 bucket [BUCKET].
 *
 * The bucket is fixed rather than configured: it's the site's own bucket, and the region and IAM
 * identity are the ones [Environment] already resolves to read Parameter Store, so a deployment
 * that can read its settings can also write its images. Nothing is left to configure: images are
 * linked at the bucket's own public URL unless `S3_PUBLIC_URL` names a CDN to use instead.
 *
 * The AWS SDK's S3 client is synchronous, so every call here hops to [Dispatchers.IO].
 */
@Initialize
object ObjectStorage : Initializable {
    private val log = LoggerFactory.getLogger(javaClass)

    /** The bucket images are written to and served from. */
    const val BUCKET = "socialcoding-website"

    const val KEY_PREFIX = "uploads/"

    private var s3: S3Client? = null
    private var publicOverride = ""

    /**
     * How long a browser may keep an image. Keys carry a random UUID and are never rewritten, so
     * the bytes behind one can't change.
     *
     * Objects served straight out of the bucket carry only the headers they were stored with, so
     * this has to be set at upload time rather than on the way out.
     */
    private const val CACHE_CONTROL = "public, max-age=31536000, immutable"

    /**
     * The base URL stored images are publicly readable at: the bucket's own endpoint, unless
     * `S3_PUBLIC_URL` overrides it with a CDN in front.
     *
     * Derived rather than configured, for the same reason [BUCKET] is — the bucket and region are
     * already known here, and a hand-entered copy could only ever disagree with them.
     */
    private val publicBase: String
        get() =
            publicOverride.ifBlank {
                "https://$BUCKET.s3.${Environment.awsRegion.id()}.amazonaws.com"
            }

    class StoredObject(val bytes: ByteArray, val contentType: String)

    /** Whether the client came up with credentials, so uploads can work. */
    val isConfigured: Boolean
        get() = s3 != null

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
     * Store [bytes] as an image of [contentType] under a random key and return the public URL to
     * display it at, under [publicBase].
     *
     * Reading it back needs `s3:GetObject` granted to everyone for `$KEY_PREFIX*`; without that
     * bucket policy the links resolve to 403. Images stored before this returned public URLs keep
     * their `/api/images/` paths, which is why that route still exists.
     *
     * @throws com.socialcoding.common.ServerError if storage isn't configured, the type is
     *   unsupported, or S3 rejects the upload.
     */
    suspend fun uploadImage(bytes: ByteArray, contentType: String): String {
        val client = s3 ?: throw ServerError("Image uploads aren't configured on this server.")
        val extension =
            extensionFor(contentType) ?: throw ServerError("Unsupported image type: $contentType")

        val key = "$KEY_PREFIX${Uuid.random()}.$extension"
        val request =
            PutObjectRequest.builder()
                .bucket(BUCKET)
                .key(key)
                .contentType(normalize(contentType))
                .cacheControl(CACHE_CONTROL)
                .build()

        withContext(Dispatchers.IO) {
            try {
                client.putObject(request, RequestBody.fromBytes(bytes))
            } catch (e: SdkException) {
                throw ServerError("Object store rejected the upload: ${describe(e)}")
            }
        }

        return "$publicBase/$key"
    }

    /** Fetch an object by [key], or null if it doesn't exist. */
    suspend fun getObject(key: String): StoredObject? {
        val client = s3 ?: return null
        val request = GetObjectRequest.builder().bucket(BUCKET).key(key).build()

        return withContext(Dispatchers.IO) {
            try {
                val bytes = client.getObjectAsBytes(request)
                StoredObject(
                    bytes.asByteArray(),
                    bytes.response().contentType() ?: "application/octet-stream",
                )
            } catch (_: NoSuchKeyException) {
                null
            } catch (e: SdkException) {
                throw ServerError("Object store failed on $key: ${describe(e)}")
            }
        }
    }

    /**
     * Whether S3 is answering right now, for the `/health` check.
     *
     * The probe HEADs a key that shouldn't exist, so a healthy bucket answers 404. A 403 counts as
     * reachable too: a policy granting `s3:GetObject` without `s3:ListBucket` answers 403 rather
     * than 404 for a missing key, and reporting a correctly locked-down bucket as down would be
     * worse than missing a bad key. Only a transport failure or a 5xx marks the store as down.
     */
    suspend fun isReachable(): Boolean {
        val client = s3 ?: return false
        val request = HeadObjectRequest.builder().bucket(BUCKET).key(healthcheckKey()).build()

        return withContext(Dispatchers.IO) {
            try {
                client.headObject(request)
                true
            } catch (_: NoSuchKeyException) {
                true
            } catch (e: S3Exception) {
                if (e.statusCode() >= 500) {
                    log.warn("Object storage health check got {} from {}", e.statusCode(), BUCKET)
                    false
                } else {
                    true
                }
            } catch (e: SdkException) {
                log.warn("Object storage health check failed", e)
                false
            }
        }
    }

    /** A key no upload can collide with, for probing the bucket. */
    private fun healthcheckKey(): String = "$KEY_PREFIX.healthcheck-${Uuid.random()}"

    /** An SDK failure as one line, with the S3 status code when there is one. */
    private fun describe(e: SdkException): String =
        if (e is S3Exception)
            "${e.statusCode()} ${e.awsErrorDetails()?.errorMessage() ?: e.message}"
        else e.message ?: e::class.simpleName.orEmpty()

    override suspend fun initialize() {
        publicOverride = Environment.getVariable("S3_PUBLIC_URL", "").trim().trimEnd('/')

        s3 =
            runCatching {
                    // The provider chain only fails once it's asked, so resolve up front: it's the
                    // difference between uploads reporting themselves unconfigured and every one
                    // of them failing at the call.
                    Environment.awsCredentials.resolveCredentials()

                    S3Client.builder()
                        .httpClient(UrlConnectionHttpClient.create())
                        .region(Environment.awsRegion)
                        .credentialsProvider(Environment.awsCredentials)
                        .build()
                }
                .onFailure {
                    log.warn(
                        "Object storage has no AWS credentials; image uploads are disabled.",
                        it,
                    )
                }
                .getOrNull() ?: return

        if (isReachable()) {
            log.info(
                "Object storage initialized for bucket {} in {}",
                BUCKET,
                Environment.awsRegion,
            )
        } else {
            log.error("Object storage is configured but bucket {} is unreachable.", BUCKET)
        }
    }
}
