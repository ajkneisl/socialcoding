package com.socialcoding.board

import com.socialcoding.TestDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Settings are a key/value table read through typed [Setting] descriptors, so the interesting
 * behaviour is what happens at the edges: unset keys, values a previous version wrote, and the
 * whole-payload write the board dashboard uses.
 */
class BoardSettingsTest {

    @BeforeTest fun clean() = TestDatabase.reset()

    /** The semester the calendar puts today in, which is what an unpinned setting reads as. */
    private val thisSemester = semesterLabel(System.currentTimeMillis())

    // --- Individual settings ----------------------------------------------------------------

    @Test
    fun `an unset setting reads its default`() {
        assertEquals("", BoardSettings.get(BoardSettings.MVP_DATE))
        assertEquals("", BoardSettings.get(BoardSettings.FOOTER))
        assertEquals(12, BoardSettings.get(BoardSettings.ANNOUNCEMENT_TIME))
    }

    @Test
    fun `a stored value comes back`() {
        BoardSettings.set(BoardSettings.FOOTER, "We meet Wednesdays at 6pm")
        assertEquals("We meet Wednesdays at 6pm", BoardSettings.get(BoardSettings.FOOTER))
    }

    @Test
    fun `writing the same key twice replaces rather than duplicates`() {
        BoardSettings.set(BoardSettings.FOOTER, "First")
        BoardSettings.set(BoardSettings.FOOTER, "Second")
        assertEquals("Second", BoardSettings.get(BoardSettings.FOOTER))
    }

    @Test
    fun `a value that no longer parses falls back to the default`() {
        // The int setting shares the string-keyed table, so anything could be in there.
        BoardSettings.set(BoardSettings.FOOTER, "not a number")
        val stray = Setting("footer_text", 99, Integer::parseInt, Integer::toString)

        assertEquals(99, BoardSettings.get(stray), "an unreadable value doesn't blow up a read")
    }

    @Test
    fun `text settings are sanitized on the way in`() {
        BoardSettings.set(BoardSettings.MVP_DATE, "  2026-09-01T12:00  ")
        assertEquals("2026-09-01", BoardSettings.get(BoardSettings.MVP_DATE), "trimmed to a date")

        BoardSettings.set(BoardSettings.FOOTER, "   padded   ")
        assertEquals("padded", BoardSettings.get(BoardSettings.FOOTER))
    }

    @Test
    fun `the announcement channel keeps only its digits`() {
        // The board typically pastes a channel mention rather than a raw id.
        BoardSettings.set(BoardSettings.ANNOUNCEMENT_CHANNEL, " <#123456789012345678> ")
        assertEquals("123456789012345678", BoardSettings.announcementChannelID())
    }

    @Test
    fun `an int setting round trips`() {
        BoardSettings.set(BoardSettings.ANNOUNCEMENT_TIME, 9)
        assertEquals(9, BoardSettings.get(BoardSettings.ANNOUNCEMENT_TIME))
    }

    // --- The dashboard payload --------------------------------------------------------------

    @Test
    fun `config reads defaults before the board has configured anything`() {
        // The semester isn't stored until the board pins one; until then it's the
        // calendar's answer, so that's what the payload carries.
        assertEquals(BoardConfig(currentSemester = thisSemester), BoardSettings.config())
    }

    @Test
    fun `the config round trips through the settings table`() {
        val config =
            BoardConfig(
                presentationDates = PresentationDates("2026-09-01", "2026-12-01"),
                announcementChannelID = "123456789012345678",
                currentSemester = thisSemester,
            )
        BoardSettings.setConfig(config)

        assertEquals(config, BoardSettings.config())
        // The same values are readable one setting at a time.
        assertEquals("2026-09-01", BoardSettings.get(BoardSettings.MVP_DATE))
        assertEquals(PresentationDates("2026-09-01", "2026-12-01"), BoardSettings.presentationDates())
    }

    @Test
    fun `setConfig writes the whole payload, so a partial one blanks the rest`() {
        BoardSettings.setConfig(BoardConfig(announcementChannelID = "123456789012345678"))
        BoardSettings.setConfig(BoardConfig(presentationDates = PresentationDates("2026-09-01", "")))

        // Callers editing one field are expected to copy from config() first; sending a bare
        // payload clears everything it omits.
        assertEquals("", BoardSettings.announcementChannelID())
        assertEquals("2026-09-01", BoardSettings.presentationDates().mvpDate)
    }

    @Test
    fun `copying from config preserves the fields being left alone`() {
        BoardSettings.setConfig(BoardConfig(announcementChannelID = "123456789012345678"))

        val existing = BoardSettings.config()
        BoardSettings.setConfig(
            existing.copy(presentationDates = existing.presentationDates.copy(mvpDate = "2026-10-01"))
        )

        val after = BoardSettings.config()
        assertEquals("123456789012345678", after.announcementChannelID)
        assertEquals("2026-10-01", after.presentationDates.mvpDate)
    }
}
