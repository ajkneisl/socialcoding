package com.socialcoding.projects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * User ids arrive from the client as strings and are stored in JSON columns, so these conversions
 * sit between untrusted input and the database.
 */
class ProjectModelsTest {

    @Test
    fun `a well-formed id parses, with surrounding space tolerated`() {
        val id = Uuid.random()
        assertEquals(id, "  $id  ".toUuidOrNull())
        assertEquals(id, "  $id  ".toUuid())
    }

    @Test
    fun `a malformed id is null rather than an exception`() {
        assertNull("not-a-uuid".toUuidOrNull())
        assertNull("".toUuidOrNull())
        // One hex digit short of a uuid.
        assertNull("0189cf3a-9e4d-7c1e-9a3b-1f2e3d4c5b6".toUuidOrNull())
    }

    @Test
    fun `the throwing parse rejects a malformed id`() {
        assertFailsWith<IllegalArgumentException> { "not-a-uuid".toUuid() }
    }

    @Test
    fun `id lists round trip through their JSON column`() {
        val ids = listOf(Uuid.random(), Uuid.random())
        assertEquals(ids, ids.toIdJson().toUserIdList())
    }

    @Test
    fun `an empty list encodes to an empty JSON array`() {
        assertEquals("[]", emptyList<Uuid>().toIdJson())
        assertEquals(emptyList(), "[]".toUserIdList())
    }

    @Test
    fun `an unreadable column decodes to no ids`() {
        // A column written before this encoding existed shouldn't take a request down.
        assertEquals(emptyList(), "".toUserIdList())
        assertEquals(emptyList(), "not json".toUserIdList())
        assertEquals(emptyList(), "{}".toUserIdList())
    }

    @Test
    fun `entries that aren't ids are skipped, and the rest are kept`() {
        val real = Uuid.random()
        assertEquals(listOf(real), """["$real", "nonsense", ""]""".toUserIdList())
    }
}
