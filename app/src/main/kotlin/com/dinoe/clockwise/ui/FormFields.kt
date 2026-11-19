package com.dinoe.clockwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dinoe.clockwise.R
import com.dinoe.clockwise.data.EventDraftIssue
import com.dinoe.clockwise.data.EventDraftRules
import com.dinoe.clockwise.data.MAX_TAGS_PER_ITEM
import com.dinoe.clockwise.data.Tag
import com.dinoe.clockwise.ui.design.Clockwise
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Form primitives shared by the calendar and alarm dialogs: the field every time value opens with,
 * the tag-toggle rule every dialog repeats, and the alarm field block that add, edit, and stack
 * dialogs all render.
 */

/**
 * Adds [tagId] to [selected] unless it is already there or the item already carries [maxTags]
 * tags; removing an existing tag is always allowed. Returns the new selection.
 */
internal fun toggleTagSelection(
    selected: LongArray,
    tagId: Long,
    maxTags: Int = MAX_TAGS_PER_ITEM,
): LongArray = when {
    tagId in selected -> selected.filter { it != tagId }.toLongArray()
    selected.size < maxTags -> selected + tagId
    else -> selected
}

/** A filled field: the label above the value on a rounded wash container, no printed border. */
@Composable
private fun FieldFrame(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    value: @Composable () -> Unit,
) {
    val colors = Clockwise.colors
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (enabled) colors.wash else colors.surfaceContainer)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = Clockwise.type.label,
            color = colors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        value()
    }
}

@Composable
internal fun TimeSlotButton(
    label: String,
    secondsOfDay: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Clockwise.colors
    val clockFormatter = rememberClockFormatter()
    FieldFrame(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(
            text = clockFormatter.format(LocalTime.ofSecondOfDay(secondsOfDay.toLong())),
            style = Clockwise.type.monoMedium,
            color = if (enabled) colors.ink else colors.inkFaint,
            maxLines = 1,
        )
    }
}

@Composable
private fun ClockwiseSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
) {
    val colors = Clockwise.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.paper,
            checkedTrackColor = colors.ink,
            checkedBorderColor = colors.ink,
            uncheckedThumbColor = colors.paper,
            uncheckedTrackColor = colors.wash,
            uncheckedBorderColor = colors.ruleStrong,
        ),
    )
}

/** A settings row: label and switch toggle together, the row carries the switch role. */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = Clockwise.type.body,
            color = Clockwise.colors.ink,
            modifier = Modifier.weight(1f),
        )
        ClockwiseSwitch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun HelperText(text: String) {
    Text(
        text = text,
        style = Clockwise.type.bodySmall,
        color = Clockwise.colors.inkMuted,
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        style = Clockwise.type.bodySmall,
        color = Clockwise.colors.signal,
    )
}

/** The alarm field block shared by the add, edit, and stack dialogs. */
@Composable
internal fun AlarmFormFields(
    name: String,
    onNameChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    oneTime: Boolean,
    onOneTimeChange: (Boolean) -> Unit,
    ringtone: String,
    onRingtoneChange: (String) -> Unit,
    ringtoneSupportingText: String?,
    allTags: List<Tag>,
    selectedTags: List<Tag>,
    onTagToggle: (Tag) -> Unit,
) {
    val colors = Clockwise.colors
    val fieldShape = MaterialTheme.shapes.small
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.wash,
        unfocusedContainerColor = colors.wash,
        disabledContainerColor = colors.surfaceContainer,
        errorContainerColor = colors.washSignal,
        focusedBorderColor = colors.ink,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        errorBorderColor = colors.signal,
    )
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.alarm_name_label)) },
        singleLine = true,
        shape = fieldShape,
        colors = fieldColors,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        label = { Text(stringResource(R.string.alarm_note_label)) },
        shape = fieldShape,
        colors = fieldColors,
        modifier = Modifier.fillMaxWidth(),
    )
    OneTimeAlarmToggle(
        checked = oneTime,
        onCheckedChange = onOneTimeChange,
    )
    RingtoneSettingRow(
        title = stringResource(R.string.alarm_ringtone_label),
        value = ringtone,
        supportingText = ringtoneSupportingText,
        onValueChange = onRingtoneChange,
    )
    TagPicker(
        allTags = allTags,
        selectedTags = selectedTags,
        onToggle = onTagToggle,
    )
}

@Composable
internal fun OneTimeAlarmToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ToggleRow(
            label = stringResource(R.string.alarm_one_time_label),
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        HelperText(stringResource(R.string.alarm_one_time_helper))
    }
}

/**
 * The all-day switch and the whole timing block beneath it, shared by the add and edit event
 * dialogs: start/end slots, the end-time switch, the end-date button, and the draft issue texts.
 * State stays with the calling dialog.
 */
@Composable
internal fun EventTimingSection(
    allDay: Boolean,
    onAllDayChange: (Boolean) -> Unit,
    startSeconds: Int,
    endSeconds: Int,
    hasEndTime: Boolean,
    onHasEndTimeChange: (Boolean) -> Unit,
    onTimeFieldClick: (TimeField) -> Unit,
    endDateEpochDay: Long,
    onEndDateClick: () -> Unit,
    draftIssue: EventDraftIssue?,
    dayFormatter: DateTimeFormatter,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleRow(
            label = stringResource(R.string.event_all_day),
            checked = allDay,
            onCheckedChange = onAllDayChange,
        )
        if (allDay) {
            HelperText(stringResource(R.string.event_all_day_helper))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeSlotButton(
                    label = stringResource(R.string.start_time_label),
                    secondsOfDay = startSeconds,
                    onClick = { onTimeFieldClick(TimeField.START) },
                    modifier = Modifier.weight(1f),
                )
                TimeSlotButton(
                    label = stringResource(R.string.end_time_label),
                    secondsOfDay = endSeconds,
                    onClick = { onTimeFieldClick(TimeField.END) },
                    enabled = hasEndTime,
                    modifier = Modifier.weight(1f),
                )
            }
            ToggleRow(
                label = stringResource(R.string.event_has_end_time),
                checked = hasEndTime,
                onCheckedChange = onHasEndTimeChange,
            )
            if (!hasEndTime) {
                HelperText(stringResource(R.string.event_no_end_time_helper))
            } else {
                FieldFrame(
                    label = stringResource(R.string.event_end_date_label),
                    onClick = onEndDateClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = LocalDate.ofEpochDay(endDateEpochDay).format(dayFormatter),
                        style = Clockwise.type.monoMedium,
                        color = Clockwise.colors.ink,
                    )
                }
            }
            if (draftIssue == EventDraftIssue.TIME_RANGE_INVALID ||
                draftIssue == EventDraftIssue.END_DAY_OFFSET_INVALID
            ) {
                ErrorText(stringResource(R.string.invalid_time_range))
            }
            if (draftIssue == EventDraftIssue.TITLE_TOO_LONG) {
                ErrorText(
                    stringResource(
                        R.string.ai_edit_title_too_long,
                        EventDraftRules.MAX_TITLE_CHARS,
                    ),
                )
            }
        }
    }
}

/** The reminder and event-alarm fields, shown for non-all-day events in both dialogs. */
@Composable
internal fun EventAlarmSection(
    allDay: Boolean,
    reminderMinutes: Int,
    onReminderChange: (Int) -> Unit,
    withAlarm: Boolean,
    onWithAlarmChange: (Boolean) -> Unit,
) {
    if (allDay) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReminderPicker(
            reminderMinutes = reminderMinutes,
            onReminderChange = onReminderChange,
        )
        ToggleRow(
            label = stringResource(R.string.event_alarm_label),
            checked = withAlarm,
            onCheckedChange = onWithAlarmChange,
        )
        if (withAlarm) {
            HelperText(stringResource(R.string.event_alarm_helper))
        }
    }
}
