package com.socialcoding

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [camelCase] is how the entity layer lines a snake_case column up with a constructor parameter, so
 * these cases are the mapping rule itself rather than a formatting nicety.
 */
class UtilTest {

    @Test
    fun `a snake case column name becomes a camel case parameter name`() {
        assertEquals("avatarUrl", "avatar_url".camelCase)
        assertEquals("discordChannelId", "discord_channel_id".camelCase)
    }

    @Test
    fun `a single word is left alone`() {
        assertEquals("title", "title".camelCase)
    }

    @Test
    fun `an already camel case name survives unchanged`() {
        assertEquals("assigneeIDs", "assigneeIDs".camelCase)
    }

    @Test
    fun `only the first segment keeps its original case`() {
        assertEquals("aBC", "a_b_c".camelCase)
    }

    @Test
    fun `an empty segment collapses instead of producing a stray character`() {
        assertEquals("", "".camelCase)
        assertEquals("aB", "a__b".camelCase)
    }
}
