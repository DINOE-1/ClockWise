package com.dinoe.clockwise.ui

import androidx.compose.ui.unit.dp
import com.dinoe.clockwise.data.Event
import java.time.LocalTime
import kotlin.math.roundToInt

internal val HourHeight = 96.dp
internal val TimelineLabelWidth = 56.dp
internal const val TimelineEndOfDaySeconds = 86_340
internal const val MinEventDurationSeconds = 300
internal const val DefaultDraftDurationSeconds = 3_600
internal const val MinLaneDurationSeconds = 60

internal data class TimelineInstance(val event: Event, val start: Int, val end: Int)

internal data class TimelineEventSlot(val instance: TimelineInstance, val lane: Int, val laneCount: Int)

/** The part of [event] that falls on [epochDay], clamped to that day's midnight boundaries. */
internal fun timelineInstance(event: Event, epochDay: Long): TimelineInstance? {
    if (event.allDay || !event.occursOn(epochDay)) return null
    val dayOffset = epochDay - event.date
    val daySeconds = (dayOffset * Event.SECONDS_PER_DAY).toInt()
    val start = (event.start - daySeconds).coerceAtLeast(0)
    if (start >= Event.SECONDS_PER_DAY) return null
    val rawEnd = if (event.hasEndTime) event.end else event.start
    val end = (rawEnd - daySeconds).coerceIn(start, Event.SECONDS_PER_DAY)
    return TimelineInstance(event, start, end)
}

internal enum class TimelineDragMode { MOVE, RESIZE_START, RESIZE_END }

internal fun snapSeconds(seconds: Int, snapMinutes: Int): Int {
    val step = (snapMinutes * 60).coerceAtLeast(60)
    return (seconds.toFloat() / step).roundToInt() * step
}

internal fun defaultDraftTimes(snapMinutes: Int): Pair<Int, Int> {
    val (defaultStart, _) = defaultTimeRange()
    val start = snapSeconds(defaultStart, snapMinutes)
        .coerceIn(0, TimelineEndOfDaySeconds - DefaultDraftDurationSeconds)
    return start to (start + DefaultDraftDurationSeconds)
}

internal fun resolveDrag(
    mode: TimelineDragMode,
    start: Int,
    end: Int,
    deltaSeconds: Int,
    snapMinutes: Int,
): Pair<Int, Int> {
    val duration = (end - start).coerceAtLeast(MinEventDurationSeconds)
    return when (mode) {
        TimelineDragMode.MOVE -> {
            val newStart = snapSeconds(start + deltaSeconds, snapMinutes)
                .coerceIn(0, TimelineEndOfDaySeconds - duration)
            newStart to (newStart + duration)
        }

        TimelineDragMode.RESIZE_START -> {
            val newStart = snapSeconds(start + deltaSeconds, snapMinutes)
                .coerceIn(0, end - MinEventDurationSeconds)
            newStart to end
        }

        TimelineDragMode.RESIZE_END -> {
            val newEnd = snapSeconds(end + deltaSeconds, snapMinutes)
                .coerceIn(start + MinEventDurationSeconds, TimelineEndOfDaySeconds)
            start to newEnd
        }
    }
}

internal fun autoScrollDelta(
    pointerY: Float,
    viewportHeight: Float,
    margin: Float,
    maxStep: Float,
): Float = when {
    pointerY < margin -> -maxStep * ((margin - pointerY) / margin).coerceIn(0f, 1f)
    pointerY > viewportHeight - margin ->
        maxStep * ((pointerY - (viewportHeight - margin)) / margin).coerceIn(0f, 1f)

    else -> 0f
}

internal fun layoutTimelineEvents(instances: List<TimelineInstance>): List<TimelineEventSlot> {
    if (instances.isEmpty()) return emptyList()
    val sorted = instances.sortedWith(compareBy({ it.start }, { it.end }))
    val result = mutableListOf<TimelineEventSlot>()
    val laneEnds = mutableListOf<Int>()
    val cluster = mutableListOf<Pair<TimelineInstance, Int>>()
    var clusterEnd = -1

    fun flushCluster() {
        val laneCount = laneEnds.size
        cluster.forEach { (instance, lane) ->
            result += TimelineEventSlot(instance, lane, laneCount)
        }
        cluster.clear()
        laneEnds.clear()
        clusterEnd = -1
    }

    for (instance in sorted) {
        // An event without an end time is zero-length; give it a lane so simultaneous entries
        // are still shown side by side instead of stacking on top of each other.
        val laneEnd = maxOf(instance.end, instance.start + MinLaneDurationSeconds)
        if (cluster.isNotEmpty() && instance.start >= clusterEnd) flushCluster()
        val lane = laneEnds.indexOfFirst { it <= instance.start }
        if (lane == -1) {
            laneEnds += laneEnd
            cluster += instance to (laneEnds.size - 1)
        } else {
            laneEnds[lane] = laneEnd
            cluster += instance to lane
        }
        clusterEnd = maxOf(clusterEnd, laneEnd)
    }
    if (cluster.isNotEmpty()) flushCluster()
    return result
}

// Defaults for a new event: the next half hour and one hour after that.
internal fun defaultTimeRange(now: LocalTime = LocalTime.now()): Pair<Int, Int> {
    val rounded = now.withSecond(0).withNano(0)
    val start = if (rounded.minute < 30) rounded.withMinute(30) else rounded.plusHours(1).withMinute(0)
    return start.toSecondOfDay() to start.plusHours(1).toSecondOfDay()
}
