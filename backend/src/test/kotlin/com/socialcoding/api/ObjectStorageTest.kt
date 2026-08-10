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
 * The test environment has no S3 credentials, so these cover the type gate and the unconfigured
 * behaviour the deployment falls back to — signing and transfer need a real store.
 */
class ObjectStorageTest {

    @Test
    fun `the image types the site accepts are recognized`() {
        listOf("image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif", "image/svg+xml")
            .forEach { assertTrue(ObjectStorage.supportsContentType(it), "expected $it to be accepted") }
    }

    @Test
    fun `everything else is refused`() {
        listOf("application/pdf", "text/html", "application/octet-stream", "image/tiff", "")
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
        assertFalse(ObjectStorage.isConfigured, "no S3 credentials are set for the test suite")
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
    fun `stored keys live under a single prefix`() {
        // The serve route refuses any key outside it, so the prefix is part of the contract.
        assertEquals("uploads/", ObjectStorage.KEY_PREFIX)
    }
}
