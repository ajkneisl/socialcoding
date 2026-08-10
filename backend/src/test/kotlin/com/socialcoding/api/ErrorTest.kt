package com.socialcoding.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * Errors double as response bodies — an [APIError] is what the client receives — so both the
 * message and the serialized shape matter.
 */
class ErrorTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `an API error is serialized as the message the client shows`() {
        assertEquals("""{"error":"Something went wrong"}""", json.encodeToString(APIError("Something went wrong")))
    }

    @Test
    fun `NotFound names the thing that was missing`() {
        assertEquals("That project could not be found.", NotFound("project").error)
    }

    @Test
    fun `an API error carries its message as the throwable message too`() {
        // StatusPages logs the throwable, so the two must not drift apart.
        val error = NotFound("invite")
        assertEquals(error.error, error.message)
    }

    // --- Argument validation ----------------------------------------------------------------

    @Test
    fun `invalidArguments collects the offending fields`() {
        val error = invalidArguments("id" to "not-a-uuid", "decision" to "banish")

        assertEquals(mapOf("id" to "not-a-uuid", "decision" to "banish"), error.arguments)
        assertEquals("There was an issue with the arguments provided.", error.error)
    }

    @Test
    fun `missingArguments lists the fields that weren't sent`() {
        assertEquals(listOf("id", "decision"), missingArguments("id", "decision").arguments)
    }

    @Test
    fun `the arguments builder throws only once something is wrong`() {
        // Nothing collected: validation passed and the handler carries on.
        arguments { }

        val error = assertFailsWith<InvalidArguments> { arguments { put("title", "must not be blank") } }
        assertEquals(mapOf("title" to "must not be blank"), error.arguments)
    }

    @Test
    fun `the arguments builder reports every problem at once`() {
        val error =
            assertFailsWith<InvalidArguments> {
                arguments {
                    put("title", "must not be blank")
                    put("startsAt", "must be in the future")
                }
            }

        // A client fixing one field at a time is a bad experience, so all of them go back together.
        assertEquals(setOf("title", "startsAt"), error.arguments.keys)
    }

    @Test
    fun `invalid arguments serialize with the offending fields attached`() {
        val encoded = json.encodeToString(invalidArguments("id" to "not-a-uuid"))
        assertTrue(encoded.contains("\"id\":\"not-a-uuid\""), encoded)
        assertTrue(encoded.contains("\"error\""), encoded)
    }

    // --- Errors that aren't for the client --------------------------------------------------

    @Test
    fun `authorization and server failures stay off the API error path`() {
        // These aren't APIErrors, so StatusPages sends a generic 500 instead of leaking detail.
        // Checked reflectively because the compiler already knows the answer for a literal type.
        listOf(InvalidAuthorization(), ServerError("boom"), AuthorizationException("nope")).forEach {
            assertFalse(APIError::class.java.isInstance(it), "${it::class.simpleName} would be a 400")
        }
        assertEquals("boom", ServerError("boom").message)
    }

    @Test
    fun `a malformed body reports a generic parse failure`() {
        // The parser's own message could echo the request back, so it isn't used.
        assertEquals("The body of this request is malformed.", MalformedBody().error)
    }
}
