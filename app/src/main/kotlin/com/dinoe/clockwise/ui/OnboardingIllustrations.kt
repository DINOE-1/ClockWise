package com.dinoe.clockwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dinoe.clockwise.R
import com.dinoe.clockwise.ui.design.Clockwise
import com.dinoe.clockwise.ui.design.Rule
import java.time.LocalDate
import java.time.LocalTime

/*
 * The tutorial panels are drawn as miniatures of the new interface: rounded cards and rows, mono
 * numerals, round dots and one signal mark. They are decorative, so the caller clears their
 * semantics.
 */

@Composable
fun CalendarMonthIllustration(modifier: Modifier = Modifier) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val today = LocalDate.now()
    val monthLabel = rememberDatePatternFormatter("yMMMM").format(today)
    val weekdayLabels = rememberWeekdayLabels()
    val firstWeekdayOffset = today.withDayOfMonth(1).dayOfWeek.value - 1
    val daysInMonth = today.lengthOfMonth()
    val weekCount = (firstWeekdayOffset + daysInMonth + 6) / 7
    val eventDays = remember(today) {
        listOf(4, 9, 16, 23).filter { it <= daysInMonth }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = monthLabel,
            style = type.monoSmall,
            color = colors.ink,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            weekdayLabels.forEach { label ->
                Text(
                    text = label,
                    style = type.monoMeta,
                    color = colors.inkMuted,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            repeat(weekCount) { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    repeat(7) { column ->
                        val day = week * 7 + column - firstWeekdayOffset + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            if (day in 1..daysInMonth) {
                                DayMark(
                                    day = day,
                                    isToday = day == today.dayOfMonth,
                                    hasEvent = day in eventDays,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A day number; today is a filled ink disc, an event a small dot beneath. */
@Composable
private fun DayMark(day: Int, isToday: Boolean, hasEvent: Boolean) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Column(
        modifier = Modifier.padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isToday) colors.ink else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                style = type.monoSmall,
                color = if (isToday) colors.paper else colors.ink,
            )
        }
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (hasEvent) colors.ink else Color.Transparent),
        )
    }
}

@Composable
fun CalendarDayIllustration(modifier: Modifier = Modifier) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val clockFormatter = rememberClockFormatter()
    val hourFormatter = rememberHourLabelFormatter()
    val hours = (8..14).toList()
    val gutter = 44.dp

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val rowHeight = maxHeight / hours.size

        Column(modifier = Modifier.fillMaxSize()) {
            hours.forEach { hour ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                ) {
                    Text(
                        text = hourFormatter.format(LocalTime.of(hour, 0)),
                        style = type.monoMeta,
                        color = colors.inkMuted,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .width(gutter)
                            .padding(top = 4.dp),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(colors.rule),
                    )
                }
            }
        }

        AppointmentMark(
            title = stringResource(R.string.onboarding_sample_meeting),
            detail = clockFormatter.format(LocalTime.of(8, 30)) +
                " – " +
                clockFormatter.format(LocalTime.of(9, 15)),
            modifier = Modifier
                .padding(start = gutter + 8.dp, end = 12.dp)
                .offset(y = rowHeight * 0.5f)
                .fillMaxWidth()
                .height(rowHeight * 0.75f),
        )
        AppointmentMark(
            title = stringResource(R.string.onboarding_sample_break),
            detail = clockFormatter.format(LocalTime.of(11, 30)) +
                " – " +
                clockFormatter.format(LocalTime.of(12, 30)),
            modifier = Modifier
                .padding(start = gutter + 8.dp, end = 56.dp)
                .offset(y = rowHeight * 3.5f)
                .fillMaxWidth()
                .height(rowHeight * 1f),
        )
        Row(
            modifier = Modifier
                .padding(start = gutter)
                .offset(y = rowHeight * 2.1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
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

/** An event on the day sheet: a rounded wash card with a tag stripe and its times in mono. */
@Composable
private fun AppointmentMark(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(colors.wash),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(colors.ink),
        )
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = title,
                style = type.body,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = type.monoMeta,
                color = colors.inkMuted,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
fun AlarmIllustration(modifier: Modifier = Modifier) {
    val clockFormatter = rememberClockFormatter()
    val weekdayLabels = rememberWeekdayLabels()
    val weekdays = weekdayLabels.take(5).joinToString(" ")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AlarmMark(
            time = clockFormatter.format(LocalTime.of(7, 30)),
            days = weekdays,
            enabled = true,
        )
        AlarmMark(
            time = clockFormatter.format(LocalTime.of(9, 0)),
            days = weekdayLabels.take(2).joinToString(" "),
            enabled = false,
        )
        AlarmMark(
            time = clockFormatter.format(LocalTime.of(12, 30)),
            days = weekdays,
            enabled = true,
        )
        AlarmMark(
            time = clockFormatter.format(LocalTime.of(18, 0)),
            days = weekdayLabels.drop(5).joinToString(" "),
            enabled = true,
        )
        AlarmMark(
            time = clockFormatter.format(LocalTime.of(21, 15)),
            days = weekdayLabels.first(),
            enabled = true,
        )
    }
}

/** An alarm row: a rounded card, the time in the gutter, its days as meta, a switch as a pill. */
@Composable
private fun AlarmMark(time: String, days: String, enabled: Boolean) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceLow)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = time,
            style = type.monoSmall,
            color = if (enabled) colors.ink else colors.inkMuted,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = days,
            style = type.monoMeta,
            color = colors.inkMuted,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(width = 28.dp, height = 16.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(if (enabled) colors.ink else colors.surfaceHighest),
            contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (enabled) colors.paper else colors.inkFaint),
            )
        }
    }
}

@Composable
fun AiIllustration(modifier: Modifier = Modifier) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val clockFormatter = rememberClockFormatter()

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = stringResource(R.string.onb_ai_prompt),
                style = type.body,
                color = colors.paper,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(colors.ink)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Text(
            text = stringResource(R.string.onboarding_sample_reply),
            style = type.body,
            color = colors.inkMuted,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(colors.surfaceLow)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.onboarding_sample_meeting),
                    style = type.body,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.onb_ai_pending),
                    style = type.monoMeta,
                    color = colors.inkMuted,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Rule()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = clockFormatter.format(LocalTime.of(10, 0)) +
                    " – " +
                    clockFormatter.format(LocalTime.of(11, 0)),
                style = type.monoMeta,
                color = colors.inkMuted,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
fun TagsIllustration(modifier: Modifier = Modifier) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val tags = listOf(
        stringResource(R.string.onboarding_sample_tag_work) to TagColorHues[0],
        stringResource(R.string.onboarding_sample_tag_personal) to TagColorHues[6],
        stringResource(R.string.onboarding_sample_tag_birthday) to TagColorHues[9],
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { (name, color) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(colors.surfaceLow)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Text(
                    text = name,
                    style = type.body,
                    color = colors.ink,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .border(1.dp, colors.rule, CircleShape),
            )
            Text(
                text = stringResource(R.string.add_tag),
                style = type.label,
                color = colors.inkMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun SettingsIllustration(modifier: Modifier = Modifier) {
    val clockFormatter = rememberClockFormatter()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsGroupIllustration(
            label = stringResource(R.string.settings_section_appearance),
            rows = listOf(MiniSetting(stringResource(R.string.theme_light))),
        )
        SettingsGroupIllustration(
            label = stringResource(R.string.settings_section_language),
            rows = listOf(MiniSetting(stringResource(R.string.language_traditional_chinese))),
        )
        SettingsGroupIllustration(
            label = stringResource(R.string.settings_section_time),
            rows = listOf(MiniSetting(stringResource(R.string.time_picker_style_wheel))),
        )
        SettingsGroupIllustration(
            label = stringResource(R.string.settings_section_alarm),
            rows = listOf(
                MiniSetting(clockFormatter.format(LocalTime.of(7, 30)), mono = true),
                MiniSetting(stringResource(R.string.alarm_expand_style_drawer_short)),
            ),
        )
        SettingsGroupIllustration(
            label = stringResource(R.string.settings_section_data),
            rows = listOf(MiniSetting(stringResource(R.string.backup_export_title))),
        )
    }
}

private data class MiniSetting(val text: String, val mono: Boolean = false)

/** A settings group in miniature: its name, then its rows on a rounded card. */
@Composable
private fun SettingsGroupIllustration(label: String, rows: List<MiniSetting>) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = type.label,
            color = colors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(colors.surfaceLow),
        ) {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Rule(modifier = Modifier.padding(horizontal = 12.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.text,
                        style = if (row.mono) type.monoSmall else type.body,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
