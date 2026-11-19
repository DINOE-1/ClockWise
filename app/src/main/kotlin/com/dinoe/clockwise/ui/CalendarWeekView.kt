package com.dinoe.clockwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dinoe.clockwise.R
import com.dinoe.clockwise.data.Event
import com.dinoe.clockwise.data.Tag
import com.dinoe.clockwise.ui.design.Clockwise
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private const val WeekDayCount = 7

/** Two rows of all-day pills fit above the hour axis; anything beyond collapses into a count. */
private const val WeekAllDayRows = 2

private val WeekPageMargin = 16.dp
private val WeekDayMarkSize = 22.dp
private val WeekAllDayPillSpacing = 4.dp

/** How much air the week leaves above the first event it scrolls to. */
private val WeekScrollLead = 48.dp

/**
 * Seven day columns sharing one hour axis. The week answers how crowded the days are and where the
 * gaps sit: blocks carry their tag stripe and start hour only, and opening a day's column header
 * shows that day at a readable width.
 */
@Composable
internal fun WeekView(
    selectedDate: LocalDate,
    eventsByDay: Map<Long, List<Event>>,
    eventTags: Map<Long, List<Tag>>,
    onDayOpened: (LocalDate) -> Unit,
    onEditRequested: (Event) -> Unit,
    onDeleteRequested: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Clockwise.colors
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val week = remember(selectedDate) { weekRange(selectedDate.toEpochDay()) }
    val days = remember(week) { week.map(LocalDate::ofEpochDay) }
    val today = LocalDate.now()
    val gutterWidth = rememberHourGutterWidth()
    val weekdayLabels = rememberWeekdayLabels()

    val timedByDay = remember(eventsByDay, week) {
        week.associateWith { epochDay ->
            layoutTimelineEvents(
                eventsByDay[epochDay].orEmpty()
                    .filterNot { it.allDay }
                    .mapNotNull { timelineInstance(it, epochDay) },
            )
        }
    }
    val allDayByDay = remember(eventsByDay, week) {
        week.associateWith { epochDay ->
            eventsByDay[epochDay].orEmpty().filter { it.allDay }
        }
    }
    val eventCountByDay = remember(eventsByDay, week) {
        week.associateWith { epochDay -> eventsByDay[epochDay].orEmpty().size }
    }

    LaunchedEffect(week) {
        val firstStart = timedByDay.values
            .flatten()
            .minOfOrNull { it.instance.start }
            ?: if (today in days) LocalTime.now().toSecondOfDay() else 8 * 3_600
        val targetPx = with(density) { (HourHeight * (firstStart / 3_600f)).roundToPx() }
        val leadPx = with(density) { WeekScrollLead.roundToPx() }
        scrollState.scrollTo((targetPx - leadPx).coerceAtLeast(0))
    }

    Column(modifier = modifier.fillMaxSize()) {
        WeekDayHeaderRow(
            days = days,
            weekdayLabels = weekdayLabels,
            selectedDate = selectedDate,
            today = today,
            eventCountByDay = eventCountByDay,
            gutterWidth = gutterWidth,
            onDayOpened = onDayOpened,
        )
        if (allDayByDay.values.any { it.isNotEmpty() }) {
            WeekAllDayBand(
                days = days,
                allDayByDay = allDayByDay,
                gutterWidth = gutterWidth,
                onDayOpened = onDayOpened,
            )
        }
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val columnWidth = (maxWidth - gutterWidth - WeekPageMargin) / WeekDayCount
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                WeekHourGutter(gutterWidth = gutterWidth)
                Box(
                    modifier = Modifier
                        .width(columnWidth * WeekDayCount)
                        .height(HourHeight * 24),
                ) {
                    for (hour in 1..23) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = HourHeight * hour)
                                .height(1.dp)
                                .background(colors.rule),
                        )
                    }
                    for (column in 1 until WeekDayCount) {
                        Box(
                            modifier = Modifier
                                .offset(x = columnWidth * column)
                                .width(1.dp)
                                .height(HourHeight * 24)
                                .background(colors.rule),
                        )
                    }
                    days.forEachIndexed { index, day ->
                        val epochDay = day.toEpochDay()
                        timedByDay[epochDay].orEmpty().forEach { slot ->
                            val instance = slot.instance
                            val blockHeight =
                                (HourHeight * ((instance.end - instance.start) / 3_600f))
                                    .coerceAtLeast(20.dp)
                            val laneWidth = columnWidth / slot.laneCount
                            TimelineEventBlock(
                                event = instance.event,
                                startSeconds = instance.start,
                                endSeconds = instance.end,
                                tags = eventTags[instance.event.id].orEmpty(),
                                compact = true,
                                onClick = { onEditRequested(instance.event) },
                                onLongClick = { onDeleteRequested(instance.event) },
                                modifier = Modifier
                                    .offset(
                                        x = columnWidth * index + laneWidth * slot.lane + 1.dp,
                                        y = HourHeight * (instance.start / 3_600f),
                                    )
                                    .width(laneWidth - 2.dp)
                                    .height(blockHeight),
                            )
                        }
                    }
                    val todayIndex = days.indexOf(today)
                    if (todayIndex >= 0) {
                        Row(
                            modifier = Modifier
                                .offset(
                                    x = columnWidth * todayIndex,
                                    y = HourHeight *
                                        (LocalTime.now().toSecondOfDay() / 3_600f),
                                )
                                .width(columnWidth),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .offset(x = (-3).dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.signal),
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .background(colors.signal),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekDayHeaderRow(
    days: List<LocalDate>,
    weekdayLabels: List<String>,
    selectedDate: LocalDate,
    today: LocalDate,
    eventCountByDay: Map<Long, Int>,
    gutterWidth: Dp,
    onDayOpened: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, end = WeekPageMargin),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(Modifier.width(gutterWidth))
        days.forEach { day ->
            WeekDayHeader(
                day = day,
                weekdayLabel = weekdayLabels[day.dayOfWeek.value - 1],
                isSelected = day == selectedDate,
                isToday = day == today,
                eventCount = eventCountByDay[day.toEpochDay()] ?: 0,
                onOpen = { onDayOpened(day) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeekDayHeader(
    day: LocalDate,
    weekdayLabel: String,
    isSelected: Boolean,
    isToday: Boolean,
    eventCount: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val fullDateFormatter = rememberLocalizedDateFormatter(FormatStyle.FULL)
    val todayLabel = stringResource(R.string.today)
    val selectedLabel = stringResource(R.string.day_cell_selected)
    val eventsLabel = if (eventCount > 0) {
        pluralStringResource(R.plurals.day_event_count, eventCount, eventCount)
    } else {
        stringResource(R.string.empty_day)
    }
    val description = remember(
        day,
        isToday,
        isSelected,
        eventsLabel,
        fullDateFormatter,
        todayLabel,
        selectedLabel,
    ) {
        buildString {
            append(fullDateFormatter.format(day))
            if (isToday) append(", ").append(todayLabel)
            if (isSelected) append(", ").append(selectedLabel)
            append(", ").append(eventsLabel)
        }
    }
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = weekdayLabel,
            style = type.monoMicro,
            color = colors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(WeekDayMarkSize)
                .clip(CircleShape)
                .then(if (isSelected) Modifier.background(colors.wash) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = type.monoSmall,
                color = colors.ink,
                maxLines = 1,
            )
        }
        if (isToday) {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(colors.signal),
            )
        }
    }
}

@Composable
private fun WeekAllDayBand(
    days: List<LocalDate>,
    allDayByDay: Map<Long, List<Event>>,
    gutterWidth: Dp,
    onDayOpened: (LocalDate) -> Unit,
) {
    val colors = Clockwise.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, end = WeekPageMargin, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(Modifier.width(gutterWidth))
        days.forEach { day ->
            val items = allDayByDay[day.toEpochDay()].orEmpty()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 1.dp),
                verticalArrangement = Arrangement.spacedBy(WeekAllDayPillSpacing),
            ) {
                if (items.size <= WeekAllDayRows) {
                    items.forEach { event ->
                        AllDayPill(
                            text = event.title,
                            container = colors.surfaceLow,
                            textColor = colors.ink,
                            modifier = Modifier.fillMaxWidth(),
                            dense = true,
                        )
                    }
                } else {
                    AllDayPill(
                        text = items.first().title,
                        container = colors.surfaceLow,
                        textColor = colors.ink,
                        modifier = Modifier.fillMaxWidth(),
                        dense = true,
                    )
                    AllDayPill(
                        text = stringResource(R.string.cal_more_events, items.size - 1),
                        container = colors.surfaceContainer,
                        textColor = colors.inkMuted,
                        modifier = Modifier.fillMaxWidth(),
                        dense = true,
                        onClick = { onDayOpened(day) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekHourGutter(gutterWidth: Dp) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val hourFormatter = rememberHourLabelFormatter()
    Column(modifier = Modifier.width(gutterWidth)) {
        for (hour in 0..23) {
            Box(
                modifier = Modifier.height(HourHeight),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    text = hourFormatter.format(LocalTime.ofSecondOfDay(hour * 3_600L)),
                    style = type.monoMicro,
                    color = colors.inkMuted,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = WeekPageMargin, end = 4.dp, top = 2.dp),
                )
            }
        }
    }
}

/**
 * The day view's fixed 64 dp gutter fits every locale; seven columns cannot afford that, so the
 * week gutter is measured from the labels this locale will actually print.
 */
@Composable
private fun rememberHourGutterWidth(): Dp {
    val style = Clockwise.type.monoMicro
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val hourFormatter = rememberHourLabelFormatter()
    return remember(style, measurer, density, hourFormatter) {
        val widest = (0..23).maxOf { hour ->
            measurer.measure(
                hourFormatter.format(LocalTime.ofSecondOfDay(hour * 3_600L)),
                style,
            ).size.width
        }
        with(density) { (widest + (WeekPageMargin + 4.dp).roundToPx()).toDp() }
    }
}
