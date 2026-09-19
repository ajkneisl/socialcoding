package com.socialcoding.api

import com.socialcoding.common.ServerError
import io.github.cdimascio.dotenv.Dotenv
import java.util.Properties
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest

/**
 * The server's configuration.
 *
 * Every setting comes from AWS Systems Manager Parameter Store, under [parameterPath], which is
 * picked by `ENV`: `/socialcoding/prod/` in production and `/socialcoding/dev/` locally. Tests run
 * with `ENV=TEST` and read the process environment instead, so they never reach AWS. `ENV` itself,
 * `AWS_REGION`, and the credentials (`AWS_PROFILE` locally, access keys in the container) come from
 * the environment or `.env`, since they're needed to reach Parameter Store in the first place.
 * [ObjectStorage] signs its S3 calls with that same region and identity.
 */
object Environment {
    private val log = LoggerFactory.getLogger(javaClass)

    private val envVar: Map<String, String> by lazy { System.getenv() }
    private val dotenv: Dotenv? by lazy {
        try {
            Dotenv.load()
        } catch (_: Exception) {
            null
        }
    }

    private val env = fromEnvironment("ENV") ?: "DEV"

    val isProduction
        get() = env == "PROD"

    val isDev
        get() = env == "DEV"

    val isTest
        get() = env == "TEST"

    val siteUrl
        get() =
            when {
                isProduction -> "https://socialcoding.net"
                isDev -> "http://localhost:5173"
                else -> error("Invalid environment.")
            }

    /** The version. */
    val VERSION: String by lazy {
        val stamped =
            Health::class.java.getResourceAsStream("version.properties")?.use {
                Properties().apply { load(it) }.getProperty("version")
            }

        stamped?.takeIf { it.isNotBlank() } ?: "unknown"
    }

    /**
     * Where this environment's parameters live. A parameter's name past this prefix is its key, so
     * `JWT_SECRET` in production is `/socialcoding/prod/JWT_SECRET`.
     */
    private val parameterPath = "/socialcoding/${env.lowercase()}/"

    /** Every parameter under [parameterPath], decrypted, fetched once on first use. */
    private val parameters: Map<String, String> by lazy {
        val request =
            GetParametersByPathRequest.builder()
                .path(parameterPath)
                .recursive(true)
                .withDecryption(true)
                .build()

        val ssm =
            SsmClient.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .region(awsRegion)
                .credentialsProvider(awsCredentials)
                .build()
        try {
            ssm.getParametersByPathPaginator(request)
                .flatMap { page -> page.parameters() }
                .associate { it.name().removePrefix(parameterPath) to it.value() }
                .also { log.info("Loaded {} parameters from {}", it.size, parameterPath) }
        } finally {
            ssm.close()
        }
    }

    /** A value from the process environment, falling back to `.env`. */
    private fun fromEnvironment(key: String): String? = envVar[key] ?: dotenv?.get(key)

    /** The region every AWS client in the server talks to. */
    val awsRegion: Region by lazy { Region.of(fromEnvironment("AWS_REGION") ?: "us-east-1") }

    /**
     * The identity every AWS client in the server signs with.
     *
     * The SDK only sees the process environment, so a profile named in `.env` (how local dev picks
     * its IAM user) has to be handed over explicitly.
     */
    val awsCredentials: AwsCredentialsProvider by lazy {
        fromEnvironment("AWS_PROFILE")?.let(ProfileCredentialsProvider::create)
            ?: DefaultCredentialsProvider.builder().build()
    }

    private fun lookup(key: String): String? =
        if (isTest) fromEnvironment(key) else parameters[key]

    /** Get the setting [key], or [default] if it isn't set. */
    fun getVariable(key: String, default: String): String = lookup(key) ?: default

    /** Get the setting [key], or throw a [ServerError] if it isn't set. */
    fun getVariable(key: String): String =
        lookup(key)
            ?: throw ServerError(
                if (isTest) "Missing environment variable $key"
                else "Missing parameter $parameterPath$key"
            )
}
