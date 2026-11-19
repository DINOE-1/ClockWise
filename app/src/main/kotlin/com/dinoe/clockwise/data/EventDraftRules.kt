package com.dinoe.clockwise.data

import java.time.LocalTime

enum class EventDraftIssue {
    TITLE_REQUIRED,
    TITLE_TOO_LONG,
    TIME_RANGE_INVALID,
    END_DAY_OFFSET_INVALID,
}

/**
 * The single home for "is this event draft legal": title and note limits, the time-range rule,
 * and the end-day-offset bound. Every write path (UI dialogs, AI tool parsing, hand-edited
 * proposals, the schedule writer's last line of defense) validates through here.
 *
 * The time-range rule: an end before the start is only legal when it lands on a later day
 * (an overnight event); end == start is a legal event with only a start time.
 */
object EventDraftRules {
    const val MAX_TITLE_CHARS = 120
    const val MAX_NOTE_CHARS = 500

    fun issue(
        title: String,
        allDay: Boolean = false,
        start: LocalTime = LocalTime.MIDNIGHT,
        end: LocalTime = start,
        endDayOffset: Int = 0,
    ): EventDraftIssue? = when {
        title.isBlank() -> EventDraftIssue.TITLE_REQUIRED
        title.length > MAX_TITLE_CHARS -> EventDraftIssue.TITLE_TOO_LONG
        else -> timeRangeIssue(allDay, start, end, endDayOffset)
    }

    fun timeRangeIssue(
        allDay: Boolean,
        start: LocalTime,
        end: LocalTime,
        endDayOffset: Int,
    ): EventDraftIssue? = when {
        allDay -> null
        endDayOffset !in 0..Event.MAX_EVENT_DAY_SPAN -> EventDraftIssue.END_DAY_OFFSET_INVALID
        // Only a same-day end may precede the start; a later end day makes any pair an overnight
        // event, and end == start is a legal event with only a start time.
        endDayOffset == 0 && end < start -> EventDraftIssue.TIME_RANGE_INVALID
        else -> null
    }

    fun normalizeTitle(title: String): String = title.trim().take(MAX_TITLE_CHARS)

    fun normalizeNote(note: String): String = note.trim().take(MAX_NOTE_CHARS)
}
