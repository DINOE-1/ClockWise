package com.dinoe.clockwise.ui

import com.dinoe.clockwise.data.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * JVM tests for the timeline's pure engine: day clamping of multi-day events, the interval-graph
 * lane layout, drag resolution and snapping.
 */
class TimelineLayoutTest {
    private val day = LocalDate.of(2026, 1, 15)

    private fun event(
        date: Long = day.toEpochDay(),
        start: Int,
        end: Int,
        allDay: Boolean = false,
    ) = Event(
        title = "E",
        note = "",
        date = date,
        start = start,
        end = end,
        allDay = allDay,
    )

    private fun seconds(hour: Int, minute: Int = 0): Int = hour * 3_600 + minute * 60

    @Test
    fun aMultiDayEventIsClampedToEachDay() {
        // 23:00 today to 07:00 tomorrow: end counts seconds from the start date's midnight.
        val overnight = event(start = seconds(23), end = Event.SECONDS_PER_DAY + seconds(7))

        val firstDay = timelineInstance(overnight, day.toEpochDay())!!
        assertEquals(seconds(23), firstDay.start)
        assertEquals(Event.SECONDS_PER_DAY, firstDay.end)

        val secondDay = timelineInstance(overnight, day.plusDays(1).toEpochDay())!!
        assertEquals(0, secondDay.start)
        assertEquals(seconds(7), secondDay.end)
    }

    @Test
    fun allDayAndNonOccurringEventsHaveNoInstance() {
        assertNull(timelineInstance(event(start = 0, end = 0, allDay = true), day.toEpochDay()))
        assertNull(
            timelineInstance(event(start = seconds(9), end = seconds(10)), day.plusDays(3).toEpochDay()),
        )
    }

    @Test
    fun anEventWithoutAnEndTimeIsZeroLength() {
        val noEnd = event(start = seconds(9), end = seconds(9))
        val instance = timelineInstance(noEnd, day.toEpochDay())!!
        assertEquals(seconds(9), instance.start)
        assertEquals(seconds(9), instance.end)
    }

    @Test
    fun sequentialEventsShareOneLane() {
        val slots = layoutTimelineEvents(
            listOf(
                TimelineInstance(event(start = seconds(9), end = seconds(10)), seconds(9), seconds(10)),
                TimelineInstance(event(start = seconds(10), end = seconds(11)), seconds(10), seconds(11)),
            ),
        )
        assertEquals(listOf(0, 0), slots.map { it.lane })
        assertTrue(slots.all { it.laneCount == 1 })
    }

    @Test
    fun overlappingEventsGetSideBySideLanes() {
        val slots = layoutTimelineEvents(
            listOf(
                TimelineInstance(event(start = seconds(9), end = seconds(11)), seconds(9), seconds(11)),
                TimelineInstance(event(start = seconds(10), end = seconds(12)), seconds(10), seconds(12)),
            ),
        )
        assertEquals(listOf(0, 1), slots.map { it.lane })
        assertTrue(slots.all { it.laneCount == 2 })
    }

    @Test
    fun aLaneIsReusedOnceItsEventEnded() {
        // 9-11, 10-12 overlap; 11-13 starts exactly when the first lane frees up.
        val slots = layoutTimelineEvents(
            listOf(
                TimelineInstance(event(start = seconds(9), end = seconds(11)), seconds(9), seconds(11)),
                TimelineInstance(event(start = seconds(10), end = seconds(12)), seconds(10), seconds(12)),
                TimelineInstance(event(start = seconds(11), end = seconds(13)), seconds(11), seconds(13)),
            ),
        )
        assertEquals(listOf(0, 1, 0), slots.map { it.lane })
        assertTrue(slots.all { it.laneCount == 2 })
    }

    @Test
    fun zeroLengthEventsStillGetTheirOwnLane() {
        val slots = layoutTimelineEvents(
            listOf(
                TimelineInstance(event(start = seconds(9), end = seconds(9)), seconds(9), seconds(9)),
                TimelineInstance(event(start = seconds(9), end = seconds(9)), seconds(9), seconds(9)),
            ),
        )
        assertEquals(listOf(0, 1), slots.map { it.lane })
        assertTrue(slots.all { it.laneCount == 2 })
    }

    @Test
    fun moveKeepsTheDurationAndSnaps() {
        val (start, end) = resolveDrag(
            mode = TimelineDragMode.MOVE,
            start = seconds(10),
            end = seconds(11),
            deltaSeconds = 1_700,
            snapMinutes = 15,
        )
        // 10:00 + 1700s snapped to 15 minutes = 10:30.
        assertEquals(seconds(10, 30), start)
        assertEquals(seconds(11, 30), end)
    }

    @Test
    fun moveCannotPushAnEventPastEndOfDay() {
        val (start, end) = resolveDrag(
            mode = TimelineDragMode.MOVE,
            start = 85_200,
            end = 86_340,
            deltaSeconds = 3_600,
            snapMinutes = 15,
        )
        assertEquals(85_200, start)
        assertEquals(86_340, end)
    }

    @Test
    fun resizeStartKeepsTheEndAndRespectsTheMinimumDuration() {
        val dragged = resolveDrag(
            TimelineDragMode.RESIZE_START,
            start = seconds(10),
            end = seconds(11),
            deltaSeconds = -3_600,
            snapMinutes = 15,
        )
        assertEquals(seconds(9), dragged.first)
        assertEquals(seconds(11), dragged.second)

        // Dragging the start past the end stops at a five-minute event.
        val clamped = resolveDrag(
            TimelineDragMode.RESIZE_START,
            start = seconds(10),
            end = seconds(11),
            deltaSeconds = 3_500,
            snapMinutes = 15,
        )
        assertEquals(seconds(11) - MinEventDurationSeconds, clamped.first)
        assertEquals(seconds(11), clamped.second)
    }

    @Test
    fun resizeEndRespectsTheMinimumDuration() {
        val (start, end) = resolveDrag(
            TimelineDragMode.RESIZE_END,
            start = seconds(10),
            end = seconds(11),
            deltaSeconds = -7_200,
            snapMinutes = 15,
        )
        assertEquals(seconds(10), start)
        assertEquals(seconds(10) + MinEventDurationSeconds, end)
    }

    @Test
    fun snappingRoundsToTheStep() {
        assertEquals(3_600, snapSeconds(4_000, snapMinutes = 15))
        assertEquals(4_500, snapSeconds(4_100, snapMinutes = 15))
        assertEquals(60, snapSeconds(40, snapMinutes = 0)) // a zero step still snaps to one minute
    }

    @Test
    fun defaultDraftTimesSitsOneHourInsideTheDay() {
        assertFalse(TimelineEndOfDaySeconds - DefaultDraftDurationSeconds < 0)
    }

    @Test
    fun defaultTimeRangeStartsAtTheNextHalfHour() {
        assertEquals(
            seconds(8, 30) to seconds(9, 30),
            defaultTimeRange(LocalTime.of(8, 15)),
        )
        assertEquals(
            seconds(9) to seconds(10),
            defaultTimeRange(LocalTime.of(8, 45)),
        )
    }
}
