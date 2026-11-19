package com.dinoe.clockwise.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** JVM tests for the one set of event draft rules shared by dialogs, AI parsing, and the writer. */
class EventDraftRulesTest {
    private val nine = LocalTime.of(9, 0)
    private val ten = LocalTime.of(10, 0)

    @Test
    fun aBlankTitleIsRequiredToHaveOne() {
        assertEquals(EventDraftIssue.TITLE_REQUIRED, EventDraftRules.issue(title = "   "))
        assertNull(EventDraftRules.issue(title = "Standup"))
    }

    @Test
    fun titlesAreCappedAtTheSharedLimit() {
        val atLimit = "a".repeat(EventDraftRules.MAX_TITLE_CHARS)
        assertNull(EventDraftRules.issue(title = atLimit))
        assertEquals(
            EventDraftIssue.TITLE_TOO_LONG,
            EventDraftRules.issue(title = atLimit + "b"),
        )
    }

    @Test
    fun endBeforeStartOnTheSameDayIsInvalid() {
        assertEquals(
            EventDraftIssue.TIME_RANGE_INVALID,
            EventDraftRules.timeRangeIssue(allDay = false, start = ten, end = nine, endDayOffset = 0),
        )
    }

    @Test
    fun endEqualToStartIsALegalStartOnlyEvent() {
        assertNull(EventDraftRules.timeRangeIssue(allDay = false, start = nine, end = nine, endDayOffset = 0))
    }

    @Test
    fun anEndOnALaterDayMayBeBeforeTheStart() {
        assertNull(EventDraftRules.timeRangeIssue(allDay = false, start = ten, end = nine, endDayOffset = 1))
    }

    @Test
    fun allDayEventsIgnoreTheTimeRange() {
        assertNull(EventDraftRules.timeRangeIssue(allDay = true, start = ten, end = nine, endDayOffset = 0))
    }

    @Test
    fun theEndDayOffsetIsBounded() {
        assertEquals(
            EventDraftIssue.END_DAY_OFFSET_INVALID,
            EventDraftRules.timeRangeIssue(
                allDay = false,
                start = nine,
                end = ten,
                endDayOffset = Event.MAX_EVENT_DAY_SPAN + 1,
            ),
        )
        assertEquals(
            EventDraftIssue.END_DAY_OFFSET_INVALID,
            EventDraftRules.timeRangeIssue(allDay = false, start = nine, end = ten, endDayOffset = -1),
        )
        assertNull(
            EventDraftRules.timeRangeIssue(
                allDay = false,
                start = nine,
                end = ten,
                endDayOffset = Event.MAX_EVENT_DAY_SPAN,
            ),
        )
    }

    @Test
    fun normalizationTrimsAndTruncates() {
        val title = "  ${"a".repeat(EventDraftRules.MAX_TITLE_CHARS + 10)}  "
        assertEquals(EventDraftRules.MAX_TITLE_CHARS, EventDraftRules.normalizeTitle(title).length)
        assertTrue(EventDraftRules.normalizeTitle(title).startsWith("a"))
        val note = "  ${"n".repeat(EventDraftRules.MAX_NOTE_CHARS + 10)}  "
        assertEquals(EventDraftRules.MAX_NOTE_CHARS, EventDraftRules.normalizeNote(note).length)
    }
}
