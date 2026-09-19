package com.socialcoding.api

import com.socialcoding.common.ServerError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

/**
 * Tests never run the initializers, so the S3 client is never built. These cover the type gate and
 * the unconfigured behaviour the deployment falls back to — transfer needs a real bucket.
 */
class ObjectStorageTest {

    @Test
    fun `the image types the site accepts are recognized`() {
        listOf("image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif")
            .forEach { assertTrue(ObjectStorage.supportsContentType(it), "expected $it to be accepted") }
    }

    @Test
    fun `everything else is refused`() {
        listOf(
                "application/pdf",
                "text/html",
                "application/octet-stream",
                "image/tiff",
                // Served straight from S3, an SVG is a document that can run script.
                "image/svg+xml",
                "",
            )
            .forEach { assertFalse(ObjectStorage.supportsContentType(it), "expected $it to be refused") }
    }

    @Test
    fun `the browser's charset and casing don't confuse the check`() {
        // Multipart parts routinely arrive as `image/png; charset=binary`.
        assertTrue(ObjectStorage.supportsContentType("image/png; charset=binary"))
        assertTrue(ObjectStorage.supportsContentType("IMAGE/PNG"))
        assertTrue(ObjectStorage.supportsContentType("  image/jpeg  "))
    }

    @Test
    fun `uploads report themselves unconfigured rather than half-working`() {
        assertFalse(ObjectStorage.isConfigured, "the S3 client is never built for the test suite")
    }

    @Test
    fun `uploading without a configured store is an error`(): Unit = runBlocking {
        val failure =
            assertFailsWith<ServerError> { ObjectStorage.uploadImage(ByteArray(1), "image/png") }
        assertEquals("Image uploads aren't configured on this server.", failure.message)
    }

    @Test
    fun `reading from an unconfigured store finds nothing`() = runBlocking {
        // The serve route turns this into a 404 rather than a 500.
        assertNull(ObjectStorage.getObject("${ObjectStorage.KEY_PREFIX}anything.png"))
    }

    @Test
    fun `stored keys are served as the type their extension implies`() {
        // The serve route reads the type off the key instead of trusting the
        // bucket, so every extension uploadImage can mint has to map back.
        assertEquals("image/png", ObjectStorage.contentTypeForKey("uploads/a.png"))
        assertEquals("image/jpeg", ObjectStorage.contentTypeForKey("uploads/a.jpg"))
        assertEquals("image/webp", ObjectStorage.contentTypeForKey("uploads/a.webp"))
        assertEquals("image/gif", ObjectStorage.contentTypeForKey("uploads/a.gif"))
        assertEquals("image/png", ObjectStorage.contentTypeForKey("uploads/a.PNG"))
    }

    @Test
    fun `a key with no extension we wrote is not servable`() {
        // These reach the serve route as a 404 rather than being proxied back
        // with whatever content type the bucket felt like returning.
        listOf(
                "uploads/a.html",
                "uploads/a.svg",
                "uploads/a.svg.html",
                "uploads/noextension",
                "uploads/a.",
            )
            .forEach {
                assertNull(ObjectStorage.contentTypeForKey(it), "expected $it to be refused")
            }
    }

    @Test
    fun `stored keys live under a single prefix`() {
        // The serve route refuses any key outside it, so the prefix is part of the contract.
        assertEquals("uploads/", ObjectStorage.KEY_PREFIX)
    }
}
