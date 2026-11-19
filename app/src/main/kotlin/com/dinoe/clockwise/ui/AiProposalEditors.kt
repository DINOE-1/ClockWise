package com.dinoe.clockwise.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dinoe.clockwise.R
import com.dinoe.clockwise.ai.AiDraftEditor
import com.dinoe.clockwise.ai.AiDraftIssue
import com.dinoe.clockwise.ai.AlarmDraft
import com.dinoe.clockwise.ai.AlarmStackDraft
import com.dinoe.clockwise.ai.EventDraft
import com.dinoe.clockwise.ai.EventRecurrencePlanner
import com.dinoe.clockwise.ai.TagRef
import com.dinoe.clockwise.data.Event
import com.dinoe.clockwise.data.EventDraftRules
import com.dinoe.clockwise.data.MAX_OCCURRENCES_PER_SERIES
import com.dinoe.clockwise.data.MAX_TAGS_PER_ITEM
import com.dinoe.clockwise.data.Tag
import com.dinoe.clockwise.ui.design.Clockwise
import com.dinoe.clockwise.ui.design.ClockwiseBottomSheet
import com.dinoe.clockwise.ui.design.InkButton
import com.dinoe.clockwise.ui.design.QuietButton
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private const val NO_DATE = Long.MIN_VALUE

/** The sheet measure: content inset inside the sheet's own surface. */
private val SheetInset = 16.dp

/** Separator for the parts of one mono stamp: a printed slash, not a middot. */
private const val STAMP_SEPARATOR = " / "

/**
 * The edit sheet: the mono stamp (a date, a count), the title, the fields, then the two actions.
 * Every dialog in this file is one of these.
 */
@Composable
private fun EditorSheet(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    overline: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    ClockwiseBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = SheetInset, end = SheetInset, top = 4.dp, bottom = SheetInset)
                .verticalScroll(rememberScrollState()),
        ) {
            if (overline != null) {
                Text(
                    text = overline,
                    style = type.bodySmall,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
            }
            Text(text = title, style = type.title, color = colors.ink)
            Spacer(modifier = Modifier.height(16.dp))
            content()
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
                InkButton(
                    text = stringResource(R.string.ai_edit_confirm),
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                )
            }
        }
    }
}

/** One line of the sheet: what it is on the left, what it holds on the right. */
@Composable
private fun SheetRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    value: @Composable () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = type.bodySmall,
            color = if (enabled) colors.inkMuted else colors.inkFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        value()
        trailing?.invoke()
    }
}

/** A row whose value is a number or a date, so it is set in mono on the right. */
@Composable
private fun SheetValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    style: TextStyle? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    SheetRow(
        label = label,
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        trailing = trailing,
    ) {
        Text(
            text = value,
            style = style ?: type.monoSmall,
            color = if (enabled) colors.ink else colors.inkFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 200.dp),
        )
    }
}

/** A Material switch in ink: the thumb rides the track, the row carries the label. */
@Composable
private fun SheetToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = type.body,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
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
}

/** A small label for a group inside the sheet; the sheet already has the title. */
@Composable
private fun SheetGroupLabel(text: String) {
    Text(
        text = text,
        style = Clockwise.type.label,
        color = Clockwise.colors.inkMuted,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun SheetHelper(text: String) {
    Text(
        text = text,
        style = Clockwise.type.bodySmall,
        color = Clockwise.colors.inkMuted,
        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
    )
}

/**
 * The sheet's text field: a rounded filled field on the sheet. Shared with the AI settings screen
 * so every field in the AI flow is the same field.
 */
@Composable
internal fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(text = label, style = type.bodySmall) },
        placeholder = {
            if (placeholder != null) {
                Text(text = placeholder, style = type.bodySmall, color = colors.inkFaint)
            }
        },
        singleLine = singleLine,
        textStyle = type.body,
        shape = MaterialTheme.shapes.small,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceLow,
            unfocusedContainerColor = colors.surfaceLow,
            disabledContainerColor = colors.surfaceLow,
            focusedTextColor = colors.ink,
            unfocusedTextColor = colors.ink,
            disabledTextColor = colors.inkFaint,
            cursorColor = colors.ink,
            focusedIndicatorColor = colors.ink,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedLabelColor = colors.inkMuted,
            unfocusedLabelColor = colors.inkMuted,
            focusedPlaceholderColor = colors.inkFaint,
            unfocusedPlaceholderColor = colors.inkFaint,
        ),
    )
}

/**
 * The one choice control for the AI screens: pill chips, the chosen ones filled with wash. Single
 * and multi select share it, so every pick on every AI screen reads the same way.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun <T> AiChipSelect(
    options: List<Pair<T, String>>,
    selected: List<T>,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (option, label) ->
            val chosen = option in selected
            FilterChip(
                selected = chosen,
                onClick = { onToggle(option) },
                label = {
                    Text(
                        text = label,
                        style = type.label,
                        color = if (chosen) colors.ink else colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                shape = MaterialTheme.shapes.extraLarge,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceLow,
                    labelColor = colors.inkMuted,
                    disabledContainerColor = colors.surfaceLow,
                    disabledLabelColor = colors.inkFaint,
                    selectedContainerColor = colors.wash,
                    selectedLabelColor = colors.ink,
                ),
                border = null,
            )
        }
    }
}

@Composable
internal fun AlarmDraftEditDialog(
    draft: AlarmDraft,
    allTags: List<Tag>,
    timePickerStyle: TimePickerStyle,
    onDismiss: () -> Unit,
    onSave: (AlarmDraft) -> Unit,
) {
    var timeSeconds by rememberSaveable { mutableIntStateOf(draft.time.toSecondOfDay()) }
    var dateEpochDay by rememberSaveable {
        mutableLongStateOf(draft.date?.toEpochDay() ?: NO_DATE)
    }
    var name by rememberSaveable { mutableStateOf(draft.name) }
    var note by rememberSaveable { mutableStateOf(draft.note) }
    var enabled by rememberSaveable { mutableStateOf(draft.enabled) }
    var oneTime by rememberSaveable { mutableStateOf(draft.oneTime) }
    var ringtone by rememberSaveable { mutableStateOf(draft.ringtone) }
    var selectedTagIds by rememberSaveable { mutableStateOf(draft.tags.map { it.id }.toLongArray()) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val date = dateEpochDay.takeIf { it != NO_DATE }?.let(LocalDate::ofEpochDay)
    val selectedTags = allTags.filter { it.id in selectedTagIds }
    val current = draft.copy(
        time = LocalTime.ofSecondOfDay(timeSeconds.toLong()),
        date = date,
        name = name,
        note = note,
        enabled = enabled,
        oneTime = oneTime,
        tags = tagRefs(allTags, selectedTagIds),
        ringtone = ringtone,
    )
    val issue = AiDraftEditor.alarmIssue(current, LocalDateTime.now())
    val dayFormatter = rememberDatePatternFormatter("MMMdEEEE")
    val clockFormatter = rememberClockFormatter()
    val timeText = clockFormatter.format(current.time)
    val dateText = date?.format(dayFormatter)
    val stamp = listOfNotNull(
        dateText,
        stringResource(if (enabled) R.string.ai_proposal_enabled else R.string.ai_proposal_disabled),
        if (oneTime) stringResource(R.string.alarm_one_time_badge) else null,
    ).joinToString(STAMP_SEPARATOR)

    EditorSheet(
        title = stringResource(R.string.ai_edit_alarm_title),
        overline = stamp,
        onDismiss = onDismiss,
        confirmEnabled = issue == null,
        onConfirm = { onSave(current) },
    ) {
        SheetValueRow(
            label = stringResource(R.string.alarm_time_label),
            value = timeText,
            style = Clockwise.type.monoMedium,
            onClick = { showTimePicker = true },
        )
        SheetValueRow(
            label = stringResource(R.string.event_date_label),
            value = dateText ?: stringResource(R.string.ai_edit_date_none),
            onClick = { showDatePicker = true },
        )
        if (date != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                QuietButton(
                    text = stringResource(R.string.ai_edit_clear_date),
                    onClick = { dateEpochDay = NO_DATE },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SheetTextField(
            value = name,
            onValueChange = { name = it.take(AiDraftEditor.MAX_ALARM_NAME_CHARS) },
            label = stringResource(R.string.alarm_name_label),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        SheetTextField(
            value = note,
            onValueChange = { note = it.take(EventDraftRules.MAX_NOTE_CHARS) },
            label = stringResource(R.string.alarm_note_label),
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        SheetToggleRow(
            label = stringResource(R.string.ai_proposal_enabled),
            checked = enabled,
            onCheckedChange = { enabled = it },
        )
        SheetToggleRow(
            label = stringResource(R.string.alarm_one_time_label),
            checked = oneTime,
            onCheckedChange = { oneTime = it },
        )
        RingtoneSettingRow(
            title = stringResource(R.string.alarm_ringtone_label),
            value = ringtone,
            onValueChange = { ringtone = it },
        )
        Spacer(modifier = Modifier.height(14.dp))
        TagPicker(
            allTags = allTags,
            selectedTags = selectedTags,
            onToggle = { tag ->
                selectedTagIds = toggleTag(selectedTagIds, tag.id)
            },
        )
        DraftIssueText(issue)
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialSecondsOfDay = timeSeconds,
            style = timePickerStyle,
            onDismiss = { showTimePicker = false },
            onConfirm = { seconds ->
                timeSeconds = seconds
                showTimePicker = false
            },
        )
    }
    if (showDatePicker) {
        DraftDatePickerDialog(
            title = stringResource(R.string.event_date_label),
            initialEpochDay = date?.toEpochDay() ?: LocalDate.now().toEpochDay(),
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                dateEpochDay = picked.toEpochDay()
                showDatePicker = false
            },
        )
    }
}

@Composable
internal fun AlarmStackDraftEditDialog(
    draft: AlarmStackDraft,
    allTags: List<Tag>,
    timePickerStyle: TimePickerStyle,
    onDismiss: () -> Unit,
    onSave: (AlarmStackDraft) -> Unit,
) {
    var times by rememberSaveable {
        mutableStateOf(draft.times.map { it.toSecondOfDay() }.toIntArray())
    }
    var name by rememberSaveable { mutableStateOf(draft.name) }
    var note by rememberSaveable { mutableStateOf(draft.note) }
    var enabled by rememberSaveable { mutableStateOf(draft.enabled) }
    var oneTime by rememberSaveable { mutableStateOf(draft.oneTime) }
    var ringtone by rememberSaveable { mutableStateOf(draft.ringtone) }
    var selectedTagIds by rememberSaveable { mutableStateOf(draft.tags.map { it.id }.toLongArray()) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var addingTime by remember { mutableStateOf(false) }

    val selectedTags = allTags.filter { it.id in selectedTagIds }
    val current = draft.copy(
        times = times.map { LocalTime.ofSecondOfDay(it.toLong()) }.sorted(),
        name = name,
        note = note,
        enabled = enabled,
        oneTime = oneTime,
        tags = tagRefs(allTags, selectedTagIds),
        ringtone = ringtone,
    )
    val issue = AiDraftEditor.stackIssue(current)
    val clockFormatter = rememberClockFormatter()
    val stamp = listOfNotNull(
        stringResource(R.string.alarm_stack_count, times.size),
        stringResource(if (enabled) R.string.ai_proposal_enabled else R.string.ai_proposal_disabled),
    ).joinToString(STAMP_SEPARATOR)

    EditorSheet(
        title = stringResource(R.string.ai_edit_stack_title),
        overline = stamp,
        onDismiss = onDismiss,
        confirmEnabled = issue == null,
        onConfirm = { onSave(current) },
    ) {
        times.forEachIndexed { index, seconds ->
            SheetValueRow(
                label = stringResource(R.string.alarm_stack_time_number, index + 1),
                value = clockFormatter.format(LocalTime.ofSecondOfDay(seconds.toLong())),
                style = Clockwise.type.monoMedium,
                onClick = { editingIndex = index },
                trailing = {
                    IconButton(
                        onClick = {
                            times = times.filterIndexed { i, _ -> i != index }.toIntArray()
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.ai_edit_remove_time),
                            tint = Clockwise.colors.inkMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            QuietButton(
                text = stringResource(R.string.ai_edit_add_time),
                onClick = { addingTime = true },
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        SheetTextField(
            value = name,
            onValueChange = { name = it.take(AiDraftEditor.MAX_ALARM_NAME_CHARS) },
            label = stringResource(R.string.alarm_name_label),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        SheetTextField(
            value = note,
            onValueChange = { note = it.take(EventDraftRules.MAX_NOTE_CHARS) },
            label = stringResource(R.string.alarm_note_label),
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        SheetToggleRow(
            label = stringResource(R.string.ai_proposal_enabled),
            checked = enabled,
            onCheckedChange = { enabled = it },
        )
        SheetToggleRow(
            label = stringResource(R.string.alarm_one_time_label),
            checked = oneTime,
            onCheckedChange = { oneTime = it },
        )
        RingtoneSettingRow(
            title = stringResource(R.string.alarm_ringtone_label),
            value = ringtone,
            supportingText = stringResource(R.string.alarm_ringtone_stack_hint),
            onValueChange = { ringtone = it },
        )
        Spacer(modifier = Modifier.height(14.dp))
        TagPicker(
            allTags = allTags,
            selectedTags = selectedTags,
            onToggle = { tag ->
                selectedTagIds = toggleTag(selectedTagIds, tag.id)
            },
        )
        DraftIssueText(issue)
    }

    if (addingTime) {
        TimePickerDialog(
            initialSecondsOfDay = LocalTime.now().toSecondOfDay(),
            style = timePickerStyle,
            onDismiss = { addingTime = false },
            onConfirm = { seconds ->
                times = times + seconds
                addingTime = false
            },
        )
    }
    editingIndex?.let { index ->
        TimePickerDialog(
            initialSecondsOfDay = times.getOrElse(index) { LocalTime.now().toSecondOfDay() },
            style = timePickerStyle,
            onDismiss = { editingIndex = null },
            onConfirm = { seconds ->
                times = times.copyOf().also { it[index] = seconds }
                editingIndex = null
            },
        )
    }
}

@Composable
internal fun EventDraftEditDialog(
    draft: EventDraft,
    allTags: List<Tag>,
    timePickerStyle: TimePickerStyle,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(draft.title) }
    var note by rememberSaveable { mutableStateOf(draft.note) }
    var dateEpochDay by rememberSaveable { mutableLongStateOf(draft.date.toEpochDay()) }
    var startSeconds by rememberSaveable { mutableIntStateOf(draft.start.toSecondOfDay()) }
    var endSeconds by rememberSaveable { mutableIntStateOf(draft.end.toSecondOfDay()) }
    var endDateEpochDay by rememberSaveable {
        mutableLongStateOf(draft.date.plusDays(draft.endDayOffset.toLong()).toEpochDay())
    }
    var hasEndTime by rememberSaveable {
        mutableStateOf(draft.end.isAfter(draft.start) || draft.endDayOffset > 0)
    }
    var allDay by rememberSaveable { mutableStateOf(draft.allDay) }
    var withAlarm by rememberSaveable { mutableStateOf(draft.withAlarm) }
    var reminderMinutes by rememberSaveable { mutableIntStateOf(draft.reminderMinutes) }
    var selectedTagIds by rememberSaveable { mutableStateOf(draft.tags.map { it.id }.toLongArray()) }
    var repeatType by rememberSaveable { mutableIntStateOf(draft.repeatType) }
    var repeatDayIds by rememberSaveable {
        mutableStateOf(draft.repeatDays.map { it.toLong() }.toLongArray())
    }
    var repeatUntilEpochDay by rememberSaveable {
        mutableLongStateOf(
            draft.repeatUntil?.toEpochDay() ?: draft.date.plusDays(90).toEpochDay(),
        )
    }
    var timeField by remember { mutableStateOf<EventDraftTimeField?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showRepeatDatePicker by remember { mutableStateOf(false) }

    val date = LocalDate.ofEpochDay(dateEpochDay)
    val endDate = LocalDate.ofEpochDay(endDateEpochDay)
    val endDayOffset = (endDateEpochDay - dateEpochDay).toInt().coerceAtLeast(0)
    val until = LocalDate.ofEpochDay(repeatUntilEpochDay)
    val selectedTags = allTags.filter { it.id in selectedTagIds }
    val repeatDays = repeatDayIds.map { it.toInt() }.toSet()
    val plan = remember(dateEpochDay, repeatType, repeatDayIds.toList(), repeatUntilEpochDay) {
        EventRecurrencePlanner.planOrNull(date, repeatType, repeatDays, repeatUntilEpochDay)
    }
    val startTime = LocalTime.ofSecondOfDay(startSeconds.toLong())
    val current = draft.copy(
        title = title,
        note = note,
        date = date,
        start = startTime,
        end = if (hasEndTime) LocalTime.ofSecondOfDay(endSeconds.toLong()) else startTime,
        endDayOffset = if (allDay || !hasEndTime) 0 else endDayOffset,
        allDay = allDay,
        withAlarm = withAlarm && !allDay,
        repeatType = repeatType,
        repeatDays = if (repeatType == Event.REPEAT_WEEKLY) repeatDays else emptySet(),
        repeatUntil = if (repeatType == Event.REPEAT_NONE) null else until,
        occurrenceCount = plan?.totalOccurrences ?: 1,
        reminderMinutes = reminderMinutes,
        tags = tagRefs(allTags, selectedTagIds),
    )
    val issue = AiDraftEditor.eventIssue(current, LocalDateTime.now(), plan)
    val dayFormatter = rememberDatePatternFormatter("MMMdEEEE")
    val weekdayLabels = rememberWeekdayLabels()
    val clockFormatter = rememberClockFormatter()
    val stamp = listOfNotNull(
        date.format(dayFormatter),
        if (repeatType != Event.REPEAT_NONE) {
            stringResource(R.string.series_size_info, current.occurrenceCount)
        } else {
            null
        },
    ).joinToString(STAMP_SEPARATOR)

    EditorSheet(
        title = stringResource(R.string.ai_edit_event_title),
        overline = stamp,
        onDismiss = onDismiss,
        confirmEnabled = issue == null,
        onConfirm = { onSave(current) },
    ) {
        SheetValueRow(
            label = stringResource(R.string.event_date_label),
            value = date.format(dayFormatter),
            onClick = { showDatePicker = true },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SheetTextField(
            value = title,
            onValueChange = { title = it.take(EventDraftRules.MAX_TITLE_CHARS) },
            label = stringResource(R.string.event_title_label),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        SheetToggleRow(
            label = stringResource(R.string.event_all_day),
            checked = allDay,
            onCheckedChange = { allDay = it },
        )
        if (allDay) {
            SheetHelper(stringResource(R.string.event_all_day_helper))
        } else {
            SheetValueRow(
                label = stringResource(R.string.start_time_label),
                value = clockFormatter.format(startTime),
                style = Clockwise.type.monoMedium,
                onClick = { timeField = EventDraftTimeField.START },
            )
            SheetValueRow(
                label = stringResource(R.string.end_time_label),
                value = clockFormatter.format(LocalTime.ofSecondOfDay(endSeconds.toLong())),
                style = Clockwise.type.monoMedium,
                enabled = hasEndTime,
                onClick = { timeField = EventDraftTimeField.END },
            )
            SheetToggleRow(
                label = stringResource(R.string.event_has_end_time),
                checked = hasEndTime,
                onCheckedChange = { hasEndTime = it },
            )
            if (!hasEndTime) {
                SheetHelper(stringResource(R.string.event_no_end_time_helper))
            } else {
                SheetValueRow(
                    label = stringResource(R.string.event_end_date_label),
                    value = endDate.format(dayFormatter),
                    onClick = { showEndDatePicker = true },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SheetTextField(
            value = note,
            onValueChange = { note = it.take(EventDraftRules.MAX_NOTE_CHARS) },
            label = stringResource(R.string.event_note_label),
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        TagPicker(
            allTags = allTags,
            selectedTags = selectedTags,
            onToggle = { tag ->
                selectedTagIds = toggleTag(selectedTagIds, tag.id)
            },
        )
        SheetGroupLabel(stringResource(R.string.repeat_rule_label))
        AiChipSelect(
            options = listOf(
                Event.REPEAT_NONE to stringResource(R.string.repeat_none),
                Event.REPEAT_DAILY to stringResource(R.string.repeat_daily),
                Event.REPEAT_WEEKLY to stringResource(R.string.repeat_weekly),
            ),
            selected = listOf(repeatType),
            onToggle = { repeatType = it },
        )
        if (repeatType == Event.REPEAT_WEEKLY) {
            Spacer(modifier = Modifier.height(10.dp))
            AiChipSelect(
                options = weekdayLabels.mapIndexed { index, label ->
                    (index + 1).toLong() to label
                },
                selected = repeatDayIds.toList(),
                onToggle = { day ->
                    repeatDayIds = if (day in repeatDayIds) {
                        repeatDayIds.filter { it != day }.toLongArray()
                    } else {
                        repeatDayIds + day
                    }
                },
            )
            SheetHelper(stringResource(R.string.repeat_weekday_pick_hint))
        }
        if (repeatType != Event.REPEAT_NONE) {
            Spacer(modifier = Modifier.height(10.dp))
            SheetValueRow(
                label = stringResource(R.string.repeat_until_label),
                value = until.format(dayFormatter),
                onClick = { showRepeatDatePicker = true },
            )
        }
        if (!allDay) {
            Spacer(modifier = Modifier.height(16.dp))
            ReminderPicker(
                reminderMinutes = reminderMinutes,
                onReminderChange = { reminderMinutes = it },
            )
            Spacer(modifier = Modifier.height(10.dp))
            SheetToggleRow(
                label = stringResource(R.string.event_alarm_label),
                checked = withAlarm,
                onCheckedChange = { withAlarm = it },
            )
            if (withAlarm) {
                SheetHelper(stringResource(R.string.event_alarm_helper))
            }
        }
        DraftIssueText(issue)
        if (issue == null && repeatType != Event.REPEAT_NONE) {
            Text(
                text = stringResource(R.string.series_size_info, current.occurrenceCount),
                style = Clockwise.type.monoSmall,
                color = Clockwise.colors.inkMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (showDatePicker) {
        DraftDatePickerDialog(
            title = stringResource(R.string.event_date_label),
            initialEpochDay = dateEpochDay,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                val pickedEpochDay = picked.toEpochDay()
                dateEpochDay = pickedEpochDay
                endDateEpochDay = endDateEpochDay.coerceIn(
                    pickedEpochDay,
                    pickedEpochDay + Event.MAX_EVENT_DAY_SPAN,
                )
                showDatePicker = false
            },
        )
    }
    if (showEndDatePicker) {
        DraftDatePickerDialog(
            title = stringResource(R.string.event_end_date_label),
            initialEpochDay = endDateEpochDay,
            onDismiss = { showEndDatePicker = false },
            onConfirm = { picked ->
                if (picked.toEpochDay() >= dateEpochDay) {
                    endDateEpochDay = picked.toEpochDay().coerceAtMost(
                        dateEpochDay + Event.MAX_EVENT_DAY_SPAN,
                    )
                }
                showEndDatePicker = false
            },
        )
    }
    if (showRepeatDatePicker) {
        DraftDatePickerDialog(
            title = stringResource(R.string.repeat_until_label),
            initialEpochDay = repeatUntilEpochDay,
            onDismiss = { showRepeatDatePicker = false },
            onConfirm = { picked ->
                repeatUntilEpochDay = picked.toEpochDay()
                showRepeatDatePicker = false
            },
        )
    }
    timeField?.let { field ->
        TimePickerDialog(
            initialSecondsOfDay = if (field == EventDraftTimeField.START) {
                startSeconds
            } else {
                endSeconds
            },
            style = timePickerStyle,
            onDismiss = { timeField = null },
            onConfirm = { seconds ->
                if (field == EventDraftTimeField.START) {
                    startSeconds = seconds
                } else {
                    endSeconds = seconds
                }
                timeField = null
            },
        )
    }
}

private enum class EventDraftTimeField { START, END }

@Composable
private fun DraftIssueText(issue: AiDraftIssue?) {
    if (issue == null) return
    val message = when (issue) {
        AiDraftIssue.ALARM_IN_PAST -> stringResource(R.string.ai_edit_alarm_in_past)
        AiDraftIssue.TITLE_REQUIRED -> stringResource(R.string.ai_edit_title_required)
        AiDraftIssue.TITLE_TOO_LONG -> stringResource(
            R.string.ai_edit_title_too_long,
            EventDraftRules.MAX_TITLE_CHARS,
        )
        AiDraftIssue.INVALID_TIME_RANGE -> stringResource(R.string.invalid_time_range)
        AiDraftIssue.REPEAT_UNTIL_INVALID -> stringResource(R.string.repeat_until_invalid)
        AiDraftIssue.REPEAT_WEEKDAYS_REQUIRED,
        AiDraftIssue.REPEAT_WEEKDAY_MISSING,
        -> stringResource(R.string.ai_edit_repeat_weekday_missing)

        AiDraftIssue.TOO_MANY_OCCURRENCES -> stringResource(
            R.string.recurrence_too_large,
            MAX_OCCURRENCES_PER_SERIES,
        )

        AiDraftIssue.STACK_TIMES_INVALID -> stringResource(R.string.ai_edit_stack_times_invalid)
    }
    Text(
        text = message,
        style = Clockwise.type.bodySmall,
        color = Clockwise.colors.signal,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/**
 * The date picker's own sheet. The days are laid out at 48 dp, so the picker takes the full sheet
 * width while the header and the actions keep the content inset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftDatePickerDialog(
    title: String,
    initialEpochDay: Long,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialEpochDay * 86_400_000L,
    )
    val dayFormatter = rememberDatePatternFormatter("MMMdEEEE")
    val selectedText = state.selectedDateMillis
        ?.let { LocalDate.ofEpochDay(it / 86_400_000L) }
        ?.format(dayFormatter)
        ?: stringResource(R.string.ai_edit_date_none)

    ClockwiseBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SheetInset, end = SheetInset, top = 4.dp),
        ) {
            Text(
                text = selectedText,
                style = type.monoMeta,
                color = colors.inkMuted,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = title, style = type.title, color = colors.ink)
        }
        Spacer(modifier = Modifier.height(8.dp))
        DatePicker(
            state = state,
            title = {},
            headline = {},
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SheetInset, end = SheetInset, bottom = SheetInset),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
            )
            InkButton(
                text = stringResource(R.string.ok),
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(LocalDate.ofEpochDay(millis / 86_400_000L))
                    }
                },
            )
        }
    }
}

private fun tagRefs(allTags: List<Tag>, selectedIds: LongArray): List<TagRef> =
    allTags.filter { it.id in selectedIds }.map { TagRef(id = it.id, name = it.name) }

private fun toggleTag(selectedIds: LongArray, id: Long): LongArray = when {
    id in selectedIds -> selectedIds.filter { it != id }.toLongArray()
    selectedIds.size < MAX_TAGS_PER_ITEM -> selectedIds + id
    else -> selectedIds
}
