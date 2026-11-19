package com.dinoe.clockwise.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dinoe.clockwise.R
import com.dinoe.clockwise.ai.EventRecurrencePlanner
import com.dinoe.clockwise.ai.RecurrenceDraftIssue
import com.dinoe.clockwise.data.Event
import com.dinoe.clockwise.data.MAX_OCCURRENCES_PER_SERIES
import com.dinoe.clockwise.data.EventDraftRules
import com.dinoe.clockwise.data.RecurrenceRule
import com.dinoe.clockwise.data.Tag
import com.dinoe.clockwise.ui.design.Clockwise
import com.dinoe.clockwise.ui.design.ClockwiseTextField
import com.dinoe.clockwise.ui.design.ClockwiseBottomSheet
import com.dinoe.clockwise.ui.design.ClockwiseScreenHeader
import com.dinoe.clockwise.ui.design.DangerButton
import com.dinoe.clockwise.ui.design.EmptyNotice
import com.dinoe.clockwise.ui.design.InkButton
import com.dinoe.clockwise.ui.design.QuietButton
import com.dinoe.clockwise.ui.design.Rule
import com.dinoe.clockwise.ui.design.SectionLabel
import com.dinoe.clockwise.ui.design.TimetableRow
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Hour-label gutter: wide enough for "下午11時" and "11 PM" alike, plus its trailing margin. */
private val HourGutter = 64.dp

/** The picker shows a wider slice of the day than the day view, so it uses a tighter hour. */
private val PickerHourHeight = 64.dp

/** The page margin the timeline panel keeps on the right, matching every other block. */
private val TimelineEndMargin = 20.dp

/** The round selection behind the chosen day's numeral, and the height a grid row needs. */
private val SelectedDayMarkSize = 36.dp
private val SelectedDayMarkSizeCompact = 22.dp
private val DayCellHeight = 48.dp
private val DayCellHeightCompact = 32.dp

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.factory),
    pendingEventId: Long? = null,
    onPendingEventConsumed: () -> Unit = {},
) {
    val eventsByDay by viewModel.eventsByDay.collectAsStateWithLifecycle()
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val eventTagMap by viewModel.eventTagMap.collectAsStateWithLifecycle()
    val snapInterval by viewModel.snapInterval.collectAsStateWithLifecycle()
    val timePickerStyle by viewModel.timePickerStyle.collectAsStateWithLifecycle()
    val displayedMonth by viewModel.displayedMonth.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val selectedDayEvents = remember(eventsByDay, selectedDate) {
        eventsByDay[selectedDate.toEpochDay()].orEmpty()
    }
    val viewMode = viewModel.viewMode
    val errorMessage = writeErrorText(viewModel.writeError)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val permissionScope = rememberCoroutineScope()
    val reminderPermissionDenied = stringResource(R.string.reminder_permission_denied)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            permissionScope.launch {
                snackbarHostState.showSnackbar(reminderPermissionDenied)
            }
        }
    }
    val ensureReminderPermission: (Int) -> Unit = { reminderMinutes ->
        if (reminderMinutes > 0 &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeError()
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogInitialTimes by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var eventToDelete by remember { mutableStateOf<Event?>(null) }
    var eventToEdit by remember { mutableStateOf<Event?>(null) }

    LaunchedEffect(pendingEventId) {
        val id = pendingEventId ?: return@LaunchedEffect
        val event = viewModel.getEventOnce(id)
        onPendingEventConsumed()
        if (event == null) return@LaunchedEffect
        viewModel.selectDate(LocalDate.ofEpochDay(event.date))
        eventToEdit = event
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Clockwise.colors.paper,
        // The header owns the status bar inset; the tab rail owns the bottom one.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            CalendarHeader(
                displayedMonth = displayedMonth,
                selectedDate = selectedDate,
                viewMode = viewMode,
                onViewModeChange = { viewModel.viewMode = it },
                onPrevious = {
                    if (viewMode == ViewMode.WEEK) {
                        viewModel.showWeek(-1)
                    } else {
                        viewModel.showMonth(displayedMonth.minusMonths(1))
                    }
                },
                onNext = {
                    if (viewMode == ViewMode.WEEK) {
                        viewModel.showWeek(1)
                    } else {
                        viewModel.showMonth(displayedMonth.plusMonths(1))
                    }
                },
                onToday = viewModel::selectToday,
                onAddEvent = { showTimePicker = true },
            )
            when (viewMode) {
                ViewMode.MONTH -> MonthView(
                    displayedMonth = displayedMonth,
                    selectedDate = selectedDate,
                    eventsByDay = eventsByDay,
                    eventTags = eventTagMap,
                    onDaySelected = viewModel::selectDate,
                    onDayAddRequested = { date ->
                        viewModel.selectDate(date)
                        showTimePicker = true
                    },
                    onEditRequested = { eventToEdit = it },
                    onDeleteRequested = { eventToDelete = it },
                    modifier = Modifier.weight(1f),
                )

                ViewMode.WEEK -> WeekView(
                    selectedDate = selectedDate,
                    eventsByDay = eventsByDay,
                    eventTags = eventTagMap,
                    onDayOpened = { date ->
                        viewModel.selectDate(date)
                        viewModel.viewMode = ViewMode.DAY
                    },
                    onEditRequested = { eventToEdit = it },
                    onDeleteRequested = { eventToDelete = it },
                    modifier = Modifier.weight(1f),
                )

                ViewMode.DAY -> DayTimelineView(
                    date = selectedDate,
                    events = selectedDayEvents,
                    eventTags = eventTagMap,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showTimePicker) {
        TimeRangePickerDialog(
            date = selectedDate,
            events = selectedDayEvents,
            eventTags = eventTagMap,
            snapMinutes = snapInterval,
            onDismiss = { showTimePicker = false },
            onConfirm = { start, end ->
                showTimePicker = false
                addDialogInitialTimes = start to end
                showAddDialog = true
            },
        )
    }

    if (showAddDialog) {
        AddEventDialog(
            date = selectedDate,
            allTags = allTags,
            timePickerStyle = timePickerStyle,
            initialStartSeconds = addDialogInitialTimes?.first,
            initialEndSeconds = addDialogInitialTimes?.second,
            onDismiss = {
                showAddDialog = false
                addDialogInitialTimes = null
            },
            onSave = { title, note, start, end, endDayOffset, allDay, withAlarm,
                reminderMinutes, tags, repeat ->
                viewModel.addEvent(
                    title = title,
                    note = note,
                    date = selectedDate,
                    start = start,
                    end = end,
                    withAlarm = withAlarm,
                    tags = tags,
                    repeat = repeat,
                    reminderMinutes = reminderMinutes,
                    endDayOffset = endDayOffset,
                    allDay = allDay,
                )
                ensureReminderPermission(reminderMinutes)
                showAddDialog = false
                addDialogInitialTimes = null
            },
        )
    }

    eventToEdit?.let { event ->
        EditEventDialog(
            event = event,
            allTags = allTags,
            timePickerStyle = timePickerStyle,
            initialTags = eventTagMap[event.id].orEmpty(),
            initialWithAlarm = alarms.any { it.eventId == event.id },
            onDismiss = { eventToEdit = null },
            onSave = { title, note, date, start, end, endDayOffset, allDay, withAlarm,
                reminderMinutes, tags, scope ->
                viewModel.updateEventScoped(
                    event = event,
                    scope = scope,
                    title = title,
                    note = note,
                    date = date,
                    start = start,
                    end = end,
                    withAlarm = withAlarm,
                    tags = tags,
                    reminderMinutes = reminderMinutes,
                    endDayOffset = endDayOffset,
                    allDay = allDay,
                )
                ensureReminderPermission(reminderMinutes)
                eventToEdit = null
            },
        )
    }

    eventToDelete?.let { event ->
        val isSeries = event.seriesId != 0L
        // Series siblings can lie outside the loaded calendar range, so they are read once here.
        var seriesMembers by remember(event.id) { mutableStateOf<List<Event>?>(null) }
        LaunchedEffect(event.id) {
            if (isSeries) seriesMembers = viewModel.getSeriesEvents(event.seriesId)
        }
        val members = seriesMembers.orEmpty()
        val isReady = !isSeries || seriesMembers != null
        var scope by remember(event.id) { mutableStateOf(EventSeriesScope.THIS_EVENT) }
        val affectedEvents = when (scope) {
            EventSeriesScope.THIS_AND_SUBSEQUENT ->
                members.filter { it.occurrenceIndex >= event.occurrenceIndex }

            EventSeriesScope.ENTIRE_SERIES -> members
            EventSeriesScope.THIS_EVENT -> listOf(event)
        }
        val hasLinkedAlarm = affectedEvents.any { candidate ->
            alarms.any { it.eventId == candidate.id }
        }
        var deleteAlarmToo by remember(event.id) { mutableStateOf(true) }
        if (isReady) {
            val colors = Clockwise.colors
            val type = Clockwise.type
            ClockwiseBottomSheet(onDismiss = { eventToDelete = null }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.delete_event),
                        style = type.title,
                        color = colors.ink,
                    )
                    Text(
                        text = if (isSeries && scope != EventSeriesScope.THIS_EVENT) {
                            stringResource(
                                R.string.delete_series_confirmation,
                                event.title,
                                affectedEvents.size,
                            )
                        } else {
                            stringResource(R.string.delete_event_confirmation, event.title)
                        },
                        style = type.body,
                        color = colors.inkMuted,
                    )
                    if (isSeries) {
                        ScopeSelector(scope = scope, onScopeChange = { scope = it })
                    }
                    if (hasLinkedAlarm) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = deleteAlarmToo,
                                onCheckedChange = { deleteAlarmToo = it },
                            )
                            Text(
                                text = stringResource(R.string.delete_event_with_alarm),
                                style = type.body,
                                color = colors.ink,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QuietButton(
                            text = stringResource(R.string.cancel),
                            onClick = { eventToDelete = null },
                        )
                        DangerButton(
                            text = stringResource(R.string.delete),
                            onClick = {
                                viewModel.deleteEventScoped(
                                    event = event,
                                    scope = if (isSeries) {
                                        scope
                                    } else {
                                        EventSeriesScope.THIS_EVENT
                                    },
                                    deleteAlarm = deleteAlarmToo && hasLinkedAlarm,
                                )
                                eventToDelete = null
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The visible range is the hero: the month in the month view, the week's dates in the week view,
 * set in the display face with its navigation on the same line. Beneath them the control row: a
 * filled add action, the 月 / 週 / 日 segmented switch and today, on the page margin.
 */
@Composable
private fun CalendarHeader(
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onAddEvent: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val isWeek = viewMode == ViewMode.WEEK
    // Neither title repeats the year: the overline's selected day already carries it.
    val monthFormatter = rememberDatePatternFormatter("MMMM")
    val weekDayFormatter = rememberDatePatternFormatter("MMMd")
    val dayFormatter = rememberDatePatternFormatter("yMMMdEEEE")
    val title = if (isWeek) {
        val week = weekRange(selectedDate.toEpochDay())
        val start = LocalDate.ofEpochDay(week.first).format(weekDayFormatter)
        val end = LocalDate.ofEpochDay(week.last).format(weekDayFormatter)
        "$start – $end"
    } else {
        displayedMonth.format(monthFormatter)
    }

    ClockwiseScreenHeader(
        title = title,
        overline = selectedDate.format(dayFormatter),
        // A date range is longer than a month name, so the week states itself one size down.
        titleStyle = if (isWeek) type.headline else type.display,
        actions = {
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(
                        if (isWeek) R.string.prev_week else R.string.prev_month,
                    ),
                    tint = colors.ink,
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(
                        if (isWeek) R.string.next_week else R.string.next_month,
                    ),
                    tint = colors.ink,
                )
            }
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = onAddEvent,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.add_event),
            )
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            SegmentedButton(
                selected = viewMode == ViewMode.MONTH,
                onClick = { onViewModeChange(ViewMode.MONTH) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            ) {
                Text(stringResource(R.string.view_mode_month))
            }
            SegmentedButton(
                selected = isWeek,
                onClick = { onViewModeChange(ViewMode.WEEK) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            ) {
                Text(stringResource(R.string.view_mode_week))
            }
            SegmentedButton(
                selected = viewMode == ViewMode.DAY,
                onClick = { onViewModeChange(ViewMode.DAY) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            ) {
                Text(stringResource(R.string.view_mode_day))
            }
        }
        QuietButton(
            text = stringResource(R.string.today),
            onClick = onToday,
        )
    }
}

@Composable
private fun MonthView(
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    eventsByDay: Map<Long, List<Event>>,
    eventTags: Map<Long, List<Tag>>,
    onDaySelected: (LocalDate) -> Unit,
    onDayAddRequested: (LocalDate) -> Unit,
    onEditRequested: (Event) -> Unit,
    onDeleteRequested: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val gridRange = monthGridRange(displayedMonth)
    val gridStart = LocalDate.ofEpochDay(gridRange.first)
    val weekRows = ((gridRange.last - gridRange.first + 1) / 7).toInt()
    val dayEvents = eventsByDay[selectedDate.toEpochDay()].orEmpty()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Landscape phones have too little height for square cells plus the day list. Split the
        // month and the selected day into panes instead of squeezing both into one column.
        if (maxWidth > maxHeight) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1.25f).fillMaxHeight()) {
                    WeekdayHeader()
                    MonthGrid(
                        gridStart = gridStart,
                        displayedMonth = displayedMonth,
                        selectedDate = selectedDate,
                        today = today,
                        eventsByDay = eventsByDay,
                        weekRows = weekRows,
                        fillHeight = true,
                        compact = true,
                        onDaySelected = onDaySelected,
                        onDayAddRequested = onDayAddRequested,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                SelectedDayEvents(
                    date = selectedDate,
                    events = dayEvents,
                    eventTags = eventTags,
                    onEditRequested = onEditRequested,
                    onDeleteRequested = onDeleteRequested,
                    onAddRequested = { onDayAddRequested(selectedDate) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                WeekdayHeader()
                MonthGrid(
                    gridStart = gridStart,
                    displayedMonth = displayedMonth,
                    selectedDate = selectedDate,
                    today = today,
                    eventsByDay = eventsByDay,
                    weekRows = weekRows,
                    fillHeight = false,
                    compact = false,
                    onDaySelected = onDaySelected,
                    onDayAddRequested = onDayAddRequested,
                )
                SelectedDayEvents(
                    date = selectedDate,
                    events = dayEvents,
                    eventTags = eventTags,
                    onEditRequested = onEditRequested,
                    onDeleteRequested = onDeleteRequested,
                    onAddRequested = { onDayAddRequested(selectedDate) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val weekdayLabels = rememberWeekdayLabels()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
    ) {
        weekdayLabels.forEach { label ->
            Text(
                text = label,
                style = type.label,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ColumnScope.MonthGrid(
    gridStart: LocalDate,
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    eventsByDay: Map<Long, List<Event>>,
    weekRows: Int,
    fillHeight: Boolean,
    compact: Boolean,
    onDaySelected: (LocalDate) -> Unit,
    onDayAddRequested: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        for (row in 0 until weekRows) {
            Row(
                modifier = if (fillHeight) {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                } else {
                    Modifier.fillMaxWidth()
                },
            ) {
                for (column in 0 until 7) {
                    val date = gridStart.plusDays((row * 7 + column).toLong())
                    DayCell(
                        date = date,
                        inDisplayedMonth = YearMonth.from(date) == displayedMonth,
                        isSelected = date == selectedDate,
                        isToday = date == today,
                        hasEvent = eventsByDay.containsKey(date.toEpochDay()),
                        onClick = { onDaySelected(date) },
                        onDoubleClick = { onDayAddRequested(date) },
                        square = !fillHeight,
                        compact = compact,
                        modifier = Modifier
                            .weight(1f)
                            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedDayEvents(
    date: LocalDate,
    events: List<Event>,
    eventTags: Map<Long, List<Tag>>,
    onEditRequested: (Event) -> Unit,
    onDeleteRequested: (Event) -> Unit,
    onAddRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayFormatter = rememberDatePatternFormatter("MMMdEEEE")
    val timeGutter = rememberEventTimeGutter(events)
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(
            text = date.format(dayFormatter),
            meta = if (events.isEmpty()) {
                null
            } else {
                pluralStringResource(R.plurals.day_event_count, events.size, events.size)
            },
        )
        if (events.isEmpty()) {
            EmptyNotice(
                title = stringResource(R.string.empty_day),
                body = stringResource(R.string.cal_empty_day_body),
                actionLabel = stringResource(R.string.add_event),
                onAction = onAddRequested,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(events, key = { it.id }) { event ->
                    EventRow(
                        event = event,
                        tags = eventTags[event.id].orEmpty(),
                        timeGutter = timeGutter,
                        onClick = { onEditRequested(event) },
                        onDeleteRequested = { onDeleteRequested(event) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inDisplayedMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvent: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    modifier: Modifier = Modifier,
    square: Boolean = true,
    compact: Boolean = false,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val numberColor = when {
        isToday -> colors.signal
        isSelected -> colors.ink
        !inDisplayedMonth -> colors.inkFaint
        else -> colors.ink
    }
    val markSize = if (compact) SelectedDayMarkSizeCompact else SelectedDayMarkSize
    val cellHeight = if (compact) DayCellHeightCompact else DayCellHeight
    val markerSize = if (compact) 3.dp else 4.dp
    val fullDateFormatter = rememberLocalizedDateFormatter(FormatStyle.FULL)
    val selectedLabel = stringResource(R.string.day_cell_selected)
    val todayLabel = stringResource(R.string.today)
    val outsideMonthLabel = stringResource(R.string.day_cell_outside_month)
    val eventsLabel = stringResource(
        if (hasEvent) R.string.day_cell_has_events else R.string.empty_day,
    )
    val description = remember(
        date,
        isSelected,
        isToday,
        inDisplayedMonth,
        hasEvent,
        fullDateFormatter,
        selectedLabel,
        todayLabel,
        outsideMonthLabel,
        eventsLabel,
    ) {
        buildString {
            append(fullDateFormatter.format(date))
            if (isToday) append(", ").append(todayLabel)
            if (isSelected) append(", ").append(selectedLabel)
            if (!inDisplayedMonth) append(", ").append(outsideMonthLabel)
            append(", ").append(eventsLabel)
        }
    }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)
    Box(
        modifier = modifier
            .then(if (square) Modifier.height(cellHeight) else Modifier)
            .padding(if (compact) 1.dp else 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = description }
            // clickable has no single-tap delay. Select immediately on the first pointer-down and
            // detect the second tap ourselves, so a quick tap on another day cannot be reverted by
            // a delayed click from the previously selected cell.
            .pointerInput(date) {
                var lastDownMillis = 0L
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (lastDownMillis != 0L &&
                        down.uptimeMillis - lastDownMillis <= viewConfiguration.doubleTapTimeoutMillis
                    ) {
                        lastDownMillis = 0L
                        currentOnDoubleClick()
                    } else {
                        lastDownMillis = down.uptimeMillis
                        currentOnClick()
                    }
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(markSize)
                    .clip(CircleShape)
                    .then(if (isSelected) Modifier.background(colors.wash) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    color = numberColor,
                    style = type.monoSmall,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = if (compact) 1.dp else 2.dp)
                    .size(markerSize),
                contentAlignment = Alignment.Center,
            ) {
                if (hasEvent) {
                    Box(
                        modifier = Modifier
                            .size(markerSize)
                            .clip(CircleShape)
                            .background(colors.ink),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayTimelineView(
    date: LocalDate,
    events: List<Event>,
    eventTags: Map<Long, List<Tag>>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val allDayEvents = events.filter { it.allDay }
    val timedEvents = events.filterNot { it.allDay }

    LaunchedEffect(date) {
        val targetSeconds = timedEvents
            .mapNotNull { timelineInstance(it, date.toEpochDay()) }
            .minOfOrNull { it.start }
            ?: if (date == LocalDate.now()) LocalTime.now().toSecondOfDay() else 8 * 3_600
        val targetPx = with(density) { (HourHeight * (targetSeconds / 3_600f)).roundToPx() }
        val initialOffset = (targetPx - with(density) { 48.dp.roundToPx() }).coerceAtLeast(0)
        scrollState.scrollTo(initialOffset)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (allDayEvents.isNotEmpty()) {
            AllDayEventStrip(events = allDayEvents)
        }
        TimelineContent(
            date = date,
            events = timedEvents,
            eventTags = eventTags,
            snapMinutes = DEFAULT_SNAP_INTERVAL_MINUTES,
            draftTimes = null,
            onDraftTimesChange = {},
            scrollState = scrollState,
            modifier = Modifier.weight(1f),
        )
    }
}

/** All-day entries are rounded pills above the hour grid. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllDayEventStrip(events: List<Event>) {
    val colors = Clockwise.colors
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AllDayPill(
            text = stringResource(R.string.event_all_day),
            container = colors.surfaceLow,
            textColor = colors.inkMuted,
        )
        events.forEach { event ->
            AllDayPill(
                text = event.title,
                container = colors.surfaceLow,
                textColor = colors.ink,
            )
        }
    }
}

@Composable
internal fun AllDayPill(
    text: String,
    container: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(container)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(
                horizontal = if (dense) 8.dp else 12.dp,
                vertical = if (dense) 4.dp else 6.dp,
            ),
    ) {
        Text(
            text = text,
            style = Clockwise.type.label,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TimeRangePickerDialog(
    date: LocalDate,
    events: List<Event>,
    eventTags: Map<Long, List<Tag>>,
    snapMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val dayFormatter = rememberDatePatternFormatter("MMMdEEEE")
    var draftTimes by remember { mutableStateOf(defaultDraftTimes(snapMinutes)) }

    LaunchedEffect(Unit) {
        val draftTop = with(density) { (PickerHourHeight * (draftTimes.first / 3_600f)).roundToPx() }
        val targetOffset = (draftTop - with(density) { 96.dp.roundToPx() }).coerceAtLeast(0)
        scrollState.scrollTo(targetOffset)
    }

    ClockwiseBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                text = date.format(dayFormatter),
                style = type.monoMeta,
                color = colors.inkMuted,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.select_time),
                style = type.title,
                color = colors.ink,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TimelineContent(
                date = date,
                events = events,
                eventTags = eventTags,
                snapMinutes = snapMinutes,
                draftTimes = draftTimes,
                onDraftTimesChange = { draftTimes = it },
                scrollState = scrollState,
                hourHeight = PickerHourHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
                InkButton(
                    text = stringResource(R.string.cal_picker_next),
                    onClick = { onConfirm(draftTimes.first, draftTimes.second) },
                )
            }
        }
    }
}

@Composable
private fun TimelineContent(
    date: LocalDate,
    events: List<Event>,
    eventTags: Map<Long, List<Tag>>,
    snapMinutes: Int,
    draftTimes: Pair<Int, Int>?,
    onDraftTimesChange: (Pair<Int, Int>) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    hourHeight: Dp = HourHeight,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val slots = remember(events, date) {
        layoutTimelineEvents(
            events.mapNotNull { timelineInstance(it, date.toEpochDay()) },
        )
    }
    val hourFormatter = rememberHourLabelFormatter()
    // The gutter has to hold the locale's longest hour label: "10 AM" and "下午11時" are the same
    // idea but not the same width, and a clipped hour label tells the reader nothing.
    val gutterWidth = HourGutter
    // While a block is being dragged the timeline must not scroll under it: the parent scroll
    // would otherwise claim the vertical drag and the block would stay put while the paper moved.
    var blockDragActive by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = modifier) {
        val panelWidth = maxWidth - gutterWidth - TimelineEndMargin
        val viewportHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = !blockDragActive),
        ) {
            Column(modifier = Modifier.width(gutterWidth)) {
                for (hour in 0..23) {
                    Box(
                        modifier = Modifier.height(hourHeight),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Text(
                            text = hourFormatter.format(
                                LocalTime.ofSecondOfDay(hour * 3_600L),
                            ),
                            style = type.monoMicro,
                            color = colors.inkMuted,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = 20.dp, end = 4.dp, top = 2.dp),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .width(panelWidth)
                    .height(hourHeight * 24),
            ) {
                for (hour in 1..23) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = hourHeight * hour)
                            .height(1.dp)
                            .background(colors.rule),
                    )
                }

                slots.forEach { slot ->
                    val instance = slot.instance
                    val event = instance.event
                    val blockHeight =
                        (hourHeight * ((instance.end - instance.start) / 3_600f))
                            .coerceAtLeast(20.dp)
                    val laneWidth = panelWidth / slot.laneCount
                    TimelineEventBlock(
                        event = event,
                        startSeconds = instance.start,
                        endSeconds = instance.end,
                        tags = eventTags[event.id].orEmpty(),
                        onPointerDownChange = { blockDragActive = it },
                        modifier = Modifier
                            .offset(
                                x = laneWidth * slot.lane + 1.dp,
                                y = hourHeight * (instance.start / 3_600f),
                            )
                            .width(laneWidth - 2.dp)
                            .height(blockHeight),
                    )
                }

                draftTimes?.let { (draftStart, draftEnd) ->
                    val draftTop = hourHeight * (draftStart / 3_600f)
                    val draftHeight = (hourHeight * ((draftEnd - draftStart) / 3_600f))
                        .coerceAtLeast(20.dp)
                    DraftEventBlock(
                        startSeconds = draftStart,
                        endSeconds = draftEnd,
                        snapMinutes = snapMinutes,
                        hourHeight = hourHeight,
                        scrollState = scrollState,
                        viewportHeightPx = viewportHeightPx,
                        onTimesChange = onDraftTimesChange,
                        onPointerDownChange = { blockDragActive = it },
                        modifier = Modifier
                            .offset(x = 2.dp, y = draftTop)
                            .width(panelWidth - 4.dp)
                            .height(draftHeight),
                    )
                }

                if (date == LocalDate.now()) {
                    val nowSeconds = LocalTime.now().toSecondOfDay()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = hourHeight * (nowSeconds / 3_600f)),
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

@Composable
private fun DraftEventBlock(
    startSeconds: Int,
    endSeconds: Int,
    snapMinutes: Int,
    hourHeight: Dp,
    scrollState: ScrollState,
    viewportHeightPx: Float,
    onTimesChange: (Pair<Int, Int>) -> Unit,
    onPointerDownChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val latestStart = rememberUpdatedState(startSeconds)
    val latestEnd = rememberUpdatedState(endSeconds)
    val latestSnap = rememberUpdatedState(snapMinutes)
    val latestChange = rememberUpdatedState(onTimesChange)
    val clockFormatter = rememberClockFormatter()

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(colors.ink)
            .pointerInput(Unit) { detectTapGestures { } }
            // Stop the timeline scrolling from the moment the finger lands on the draft, so the
            // drag moves the block instead of sliding the paper under it.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPointerDownChange(true)
                    try {
                        do {
                            val pointer = awaitPointerEvent()
                        } while (pointer.changes.any { it.pressed })
                    } finally {
                        onPointerDownChange(false)
                    }
                }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    var mode = TimelineDragMode.MOVE
                    var originStart = 0
                    var originEnd = 0
                    var accumulated = 0f
                    var pointerViewportY = 0f
                    val resizeZone = 20.dp.toPx()
                    val pxPerSecond = hourHeight.toPx() / 3_600f
                    val autoScrollMargin = 56.dp.toPx()
                    val maxScrollPerFrame = 16.dp.toPx()

                    fun blockTopInViewport(): Float =
                        hourHeight.toPx() * (latestStart.value / 3_600f) - scrollState.value

                    fun updateTimes() {
                        val deltaSeconds = (accumulated / pxPerSecond).roundToInt()
                        latestChange.value(
                            resolveDrag(
                                mode,
                                originStart,
                                originEnd,
                                deltaSeconds,
                                latestSnap.value,
                            ),
                        )
                    }

                    var scrollJob: Job? = null
                    detectDragGestures(
                        onDragStart = { position ->
                            originStart = latestStart.value
                            originEnd = latestEnd.value
                            accumulated = 0f
                            pointerViewportY = blockTopInViewport() + position.y
                            mode = when {
                                position.y <= resizeZone -> TimelineDragMode.RESIZE_START
                                position.y >= size.height - resizeZone ->
                                    TimelineDragMode.RESIZE_END

                                else -> TimelineDragMode.MOVE
                            }
                            scrollJob?.cancel()
                            scrollJob = launch {
                                while (true) {
                                    val scrollDelta = autoScrollDelta(
                                        pointerY = pointerViewportY,
                                        viewportHeight = viewportHeightPx,
                                        margin = autoScrollMargin,
                                        maxStep = maxScrollPerFrame,
                                    )
                                    if (scrollDelta != 0f) {
                                        val consumed = scrollState.dispatchRawDelta(scrollDelta)
                                        if (consumed != 0f) {
                                            accumulated += consumed
                                            updateTimes()
                                        }
                                    }
                                    withFrameNanos { }
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulated += dragAmount.y
                            pointerViewportY = blockTopInViewport() + change.position.y
                            updateTimes()
                        },
                        onDragEnd = {
                            scrollJob?.cancel()
                            scrollJob = null
                        },
                        onDragCancel = {
                            scrollJob?.cancel()
                            scrollJob = null
                        },
                    )
                }
            },
    ) {
        Text(
            text = "${clockFormatter.format(LocalTime.ofSecondOfDay(startSeconds.toLong()))} – " +
                clockFormatter.format(LocalTime.ofSecondOfDay(endSeconds.toLong())),
            style = type.monoSmall,
            color = colors.paper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, end = 28.dp, top = 8.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-6).dp, y = 6.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(colors.paper),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 6.dp, y = (-6).dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(colors.paper),
        )
    }
}

/**
 * The width of the time column in the day list, measured from the labels that day actually shows.
 * A clock's width is a locale question — the Chinese 12-hour clock prefixes two full-width
 * characters — and a fixed column either clipped it or left the name floating far to the right.
 */
@Composable
private fun rememberEventTimeGutter(events: List<Event>): Dp {
    val style = Clockwise.type.monoSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labels = events.flatMap { listOfNotNull(eventStartLabel(it), eventEndLabel(it)) }
    return remember(labels, style, measurer, density) {
        val width = labels.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0
        with(density) { (width + 2.dp.roundToPx()).toDp() }
    }
}

@Composable
private fun eventStartLabel(event: Event): String {
    return if (event.allDay) {
        stringResource(R.string.event_all_day)
    } else {
        rememberClockFormatter().formatPaddedHour(LocalTime.ofSecondOfDay(event.start.toLong()))
    }
}

@Composable
private fun EventRow(
    event: Event,
    tags: List<Tag>,
    timeGutter: Dp,
    onClick: () -> Unit,
    onDeleteRequested: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val endLabel = eventEndLabel(event)
    val gutterLabel = eventStartLabel(event)
    TimetableRow(
        gutterWidth = timeGutter,
        onClick = onClick,
        onLongClick = onDeleteRequested,
        gutter = {
            Column {
                Text(
                    text = gutterLabel,
                    style = type.monoSmall,
                    color = if (event.allDay) colors.inkMuted else colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (endLabel != null) {
                    Text(
                        text = endLabel,
                        style = type.monoSmall,
                        color = colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = event.title,
                style = type.title,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (event.isRecurring) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.series_row_label),
                    tint = colors.inkMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (event.note.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = event.note,
                style = type.bodySmall,
                color = colors.inkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            TagPills(tags = tags)
        }
    }
}

@Composable
internal fun eventTimeRangeLabel(event: Event): String? {
    if (event.allDay || !event.hasEndTime) return null
    val clockFormatter = rememberClockFormatter()
    val start = clockFormatter.format(LocalTime.ofSecondOfDay(event.start.toLong()))
    val end = clockFormatter.format(LocalTime.ofSecondOfDay(event.endTimeOfDay.toLong()))
    return when (event.endDayOffset) {
        0 -> "$start – $end"
        1 -> stringResource(R.string.event_time_range_next_day, start, end)
        else -> stringResource(
            R.string.event_time_range_days_later,
            start,
            event.endDayOffset,
            end,
        )
    }
}

/**
 * The end of an event alone: the list's time column already states the start, and the two lines
 * stack there without a dash so the digits stay on the same columns.
 */
@Composable
private fun eventEndLabel(event: Event): String? {
    if (event.allDay || !event.hasEndTime) return null
    val end = rememberClockFormatter()
        .formatPaddedHour(LocalTime.ofSecondOfDay(event.endTimeOfDay.toLong()))
    return when (event.endDayOffset) {
        0 -> end
        1 -> stringResource(R.string.event_end_time_next_day, end)
        else -> stringResource(R.string.event_end_time_days_later, event.endDayOffset, end)
    }
}

internal enum class TimeField { START, END }

/** A selectable pill: wash-filled when chosen, transparent when not. */
@Composable
private fun SegmentedChoice(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
) {
    val colors = Clockwise.colors
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(if (active) colors.wash else Color.Transparent)
            .selectable(
                selected = active,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = textStyle ?: Clockwise.type.label,
            color = if (active) colors.ink else colors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventDialog(
    date: LocalDate,
    allTags: List<Tag>,
    timePickerStyle: TimePickerStyle,
    initialStartSeconds: Int? = null,
    initialEndSeconds: Int? = null,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        note: String,
        start: LocalTime,
        end: LocalTime,
        endDayOffset: Int,
        allDay: Boolean,
        withAlarm: Boolean,
        reminderMinutes: Int,
        tags: List<Tag>,
        repeat: RecurrenceRule,
    ) -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val (defaultStart, defaultEnd) = remember { defaultTimeRange() }
    val startInit = initialStartSeconds ?: defaultStart
    val endInit = initialEndSeconds ?: defaultEnd
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var startSeconds by rememberSaveable { mutableIntStateOf(startInit) }
    var endSeconds by rememberSaveable { mutableIntStateOf(endInit) }
    var hasEndTime by rememberSaveable { mutableStateOf(true) }
    var allDay by rememberSaveable { mutableStateOf(false) }
    var endDateEpochDay by rememberSaveable {
        mutableLongStateOf(
            date.plusDays(if (endInit <= startInit) 1 else 0).toEpochDay(),
        )
    }
    var timeField by remember { mutableStateOf<TimeField?>(null) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var withAlarm by rememberSaveable { mutableStateOf(false) }
    var reminderMinutes by rememberSaveable { mutableIntStateOf(Event.REMINDER_OFF) }
    var selectedTagIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var repeatType by rememberSaveable { mutableIntStateOf(Event.REPEAT_NONE) }
    var repeatDayIds by rememberSaveable {
        mutableStateOf(longArrayOf(date.dayOfWeek.value.toLong()))
    }
    var repeatUntilEpochDay by rememberSaveable {
        mutableLongStateOf(date.plusDays(90).toEpochDay())
    }
    var showRepeatDatePicker by remember { mutableStateOf(false) }

    val selectedTags = allTags.filter { it.id in selectedTagIds }
    val onTagToggle: (Tag) -> Unit = { tag ->
        selectedTagIds = toggleTagSelection(selectedTagIds, tag.id)
    }
    val repeatDays = repeatDayIds.map { it.toInt() }.toSet()
    val repeatRule = RecurrenceRule(
        type = repeatType,
        days = repeatDays,
        untilEpochDay = repeatUntilEpochDay,
    )
    val (repeatPlan, recurrenceIssue) = remember(
        date.toEpochDay(),
        repeatType,
        repeatDayIds.toList(),
        repeatUntilEpochDay,
    ) {
        val plan = EventRecurrencePlanner.planOrNull(
            date = date,
            type = repeatType,
            days = repeatDays,
            untilEpochDay = repeatUntilEpochDay,
        )
        plan to EventRecurrencePlanner.issue(
            date = date,
            type = repeatType,
            days = repeatDays,
            untilEpochDay = repeatUntilEpochDay,
            requireWeekdayMatch = false,
            planned = plan,
        )
    }
    val endDayOffset = (endDateEpochDay - date.toEpochDay()).toInt().coerceAtLeast(0)
    // Validate exactly what the confirm button will submit.
    val submitStart = LocalTime.ofSecondOfDay(startSeconds.toLong())
    val submitEnd = if (hasEndTime && !allDay) {
        LocalTime.ofSecondOfDay(endSeconds.toLong())
    } else {
        submitStart
    }
    val draftIssue = EventDraftRules.issue(
        title = title,
        allDay = allDay,
        start = submitStart,
        end = submitEnd,
        endDayOffset = if (allDay || !hasEndTime) 0 else endDayOffset,
    )
    val isValid = draftIssue == null && recurrenceIssue == null
    val occurrenceCount = repeatPlan?.totalOccurrences ?: 1
    val dayFormatter = rememberDatePatternFormatter("MMMdEEEE")
    val untilDateFormatter = rememberLocalizedDateFormatter()
    val weekdayLabels = rememberWeekdayLabels()

    ClockwiseBottomSheet(onDismiss = onDismiss) {
        // Bounded height so the weighted field area resolves and the actions never leave the sheet.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .imePadding(),
        ) {
            Column {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = date.format(dayFormatter),
                        style = type.monoMeta,
                        color = colors.inkMuted,
                    )
                    Text(
                        text = stringResource(R.string.add_event),
                        style = type.title,
                        color = colors.ink,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ClockwiseTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = stringResource(R.string.event_title_label),
                        singleLine = true,
                    )
                    ClockwiseTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = stringResource(R.string.event_note_label),
                    )
                    TagPicker(
                        allTags = allTags,
                        selectedTags = selectedTags,
                        onToggle = onTagToggle,
                    )
                    EventTimingSection(
                        allDay = allDay,
                        onAllDayChange = { allDay = it },
                        startSeconds = startSeconds,
                        endSeconds = endSeconds,
                        hasEndTime = hasEndTime,
                        onHasEndTimeChange = { hasEndTime = it },
                        onTimeFieldClick = { timeField = it },
                        endDateEpochDay = endDateEpochDay,
                        onEndDateClick = { showEndDatePicker = true },
                        draftIssue = draftIssue,
                        dayFormatter = dayFormatter,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.repeat_rule_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.ink,
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            SegmentedChoice(
                                text = stringResource(R.string.repeat_none),
                                active = repeatType == Event.REPEAT_NONE,
                                onClick = { repeatType = Event.REPEAT_NONE },
                                modifier = Modifier.weight(1f),
                            )
                            SegmentedChoice(
                                text = stringResource(R.string.repeat_daily),
                                active = repeatType == Event.REPEAT_DAILY,
                                onClick = { repeatType = Event.REPEAT_DAILY },
                                modifier = Modifier.weight(1f),
                            )
                            SegmentedChoice(
                                text = stringResource(R.string.repeat_weekly),
                                active = repeatType == Event.REPEAT_WEEKLY,
                                onClick = { repeatType = Event.REPEAT_WEEKLY },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (repeatType == Event.REPEAT_WEEKLY) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                weekdayLabels.forEachIndexed { index, label ->
                                    val day = (index + 1).toLong()
                                    SegmentedChoice(
                                        text = label,
                                        active = day in repeatDayIds,
                                        onClick = {
                                            repeatDayIds = if (day in repeatDayIds) {
                                                repeatDayIds.filter { it != day }.toLongArray()
                                            } else {
                                                repeatDayIds + day
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.repeat_weekday_pick_hint),
                                style = type.bodySmall,
                                color = colors.inkMuted,
                            )
                        }
                        if (repeatType != Event.REPEAT_NONE) {
                            OutlinedButton(
                                onClick = { showRepeatDatePicker = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = stringResource(R.string.repeat_until_label),
                                        style = type.monoMicro,
                                        color = colors.inkMuted,
                                    )
                                    Text(
                                        text = LocalDate.ofEpochDay(repeatUntilEpochDay)
                                            .format(untilDateFormatter),
                                        style = type.monoSmall,
                                        color = colors.ink,
                                    )
                                }
                            }
                            when (recurrenceIssue) {
                                RecurrenceDraftIssue.UNTIL_INVALID -> Text(
                                    text = stringResource(R.string.repeat_until_invalid),
                                    color = colors.signal,
                                    style = type.bodySmall,
                                )

                                RecurrenceDraftIssue.TOO_MANY_OCCURRENCES -> Text(
                                    text = stringResource(
                                        R.string.recurrence_too_large,
                                        MAX_OCCURRENCES_PER_SERIES,
                                    ),
                                    color = colors.signal,
                                    style = type.bodySmall,
                                )

                                else -> Text(
                                    text = stringResource(
                                        R.string.series_size_info,
                                        occurrenceCount,
                                    ),
                                    style = type.bodySmall,
                                    color = colors.inkMuted,
                                )
                            }
                        }
                    }
                    EventAlarmSection(
                        allDay = allDay,
                        reminderMinutes = reminderMinutes,
                        onReminderChange = { reminderMinutes = it },
                        withAlarm = withAlarm,
                        onWithAlarmChange = { withAlarm = it },
                    )
                }
                Rule()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuietButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                    )
                    InkButton(
                        text = stringResource(R.string.add_event),
                        onClick = {
                            val startTime = LocalTime.ofSecondOfDay(startSeconds.toLong())
                            onSave(
                                title.trim(),
                                note.trim(),
                                startTime,
                                if (hasEndTime && !allDay) {
                                    LocalTime.ofSecondOfDay(endSeconds.toLong())
                                } else {
                                    startTime
                                },
                                if (allDay || !hasEndTime) 0 else endDayOffset,
                                allDay,
                                withAlarm && !allDay,
                                if (allDay) Event.REMINDER_OFF else reminderMinutes,
                                selectedTags,
                                repeatRule,
                            )
                        },
                        enabled = isValid,
                    )
                }
            }
        }
    }

    if (showRepeatDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = repeatUntilEpochDay * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { showRepeatDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            repeatUntilEpochDay = millis / 86_400_000L
                        }
                        showRepeatDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRepeatDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val endDatePickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDateEpochDay * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endDatePickerState.selectedDateMillis?.let { millis ->
                            val picked = millis / 86_400_000L
                            if (picked >= date.toEpochDay()) {
                                endDateEpochDay = picked.coerceAtMost(
                                    date.toEpochDay() + Event.MAX_EVENT_DAY_SPAN,
                                )
                            }
                        }
                        showEndDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = endDatePickerState)
        }
    }

    timeField?.let { field ->
        TimePickerDialog(
            initialSecondsOfDay = if (field == TimeField.START) startSeconds else endSeconds,
            style = timePickerStyle,
            onDismiss = { timeField = null },
            onConfirm = { picked ->
                if (field == TimeField.START) startSeconds = picked else endSeconds = picked
                timeField = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEventDialog(
    event: Event,
    allTags: List<Tag>,
    timePickerStyle: TimePickerStyle,
    initialTags: List<Tag>,
    initialWithAlarm: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        note: String,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        endDayOffset: Int,
        allDay: Boolean,
        withAlarm: Boolean,
        reminderMinutes: Int,
        tags: List<Tag>,
        scope: EventSeriesScope,
    ) -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    var title by rememberSaveable(event.id) { mutableStateOf(event.title) }
    var note by rememberSaveable(event.id) { mutableStateOf(event.note) }
    var dateEpochDay by rememberSaveable(event.id) { mutableLongStateOf(event.date) }
    var startSeconds by rememberSaveable(event.id) { mutableIntStateOf(event.start) }
    var endSeconds by rememberSaveable(event.id) { mutableIntStateOf(event.endTimeOfDay) }
    var endDateEpochDay by rememberSaveable(event.id) {
        mutableLongStateOf(event.endDateEpochDay)
    }
    var hasEndTime by rememberSaveable(event.id) { mutableStateOf(event.hasEndTime) }
    var allDay by rememberSaveable(event.id) { mutableStateOf(event.allDay) }
    var withAlarm by rememberSaveable(event.id) { mutableStateOf(initialWithAlarm) }
    var reminderMinutes by rememberSaveable(event.id) {
        mutableIntStateOf(event.reminderMinutes)
    }
    var selectedTagIds by rememberSaveable(event.id) {
        mutableStateOf(initialTags.map { it.id }.toLongArray())
    }
    var scope by rememberSaveable(event.id) {
        mutableStateOf(EventSeriesScope.THIS_EVENT)
    }
    var timeField by remember { mutableStateOf<TimeField?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val isSeries = event.seriesId != 0L
    val selectedTags = allTags.filter { it.id in selectedTagIds }
    val onTagToggle: (Tag) -> Unit = { tag ->
        selectedTagIds = toggleTagSelection(selectedTagIds, tag.id)
    }
    val endDayOffset = (endDateEpochDay - dateEpochDay).toInt().coerceAtLeast(0)
    // Validate exactly what the confirm button will submit.
    val submitStart = LocalTime.ofSecondOfDay(startSeconds.toLong())
    val submitEnd = if (hasEndTime && !allDay) {
        LocalTime.ofSecondOfDay(endSeconds.toLong())
    } else {
        submitStart
    }
    val draftIssue = EventDraftRules.issue(
        title = title,
        allDay = allDay,
        start = submitStart,
        end = submitEnd,
        endDayOffset = if (allDay || !hasEndTime) 0 else endDayOffset,
    )
    val isValid = draftIssue == null
    val dayFormatter = rememberDatePatternFormatter("MMMdEEEE")

    ClockwiseBottomSheet(onDismiss = onDismiss) {
        // Bounded height so the weighted field area resolves and the actions never leave the sheet.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .imePadding(),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.edit_event),
                    style = type.title,
                    color = colors.ink,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        enabled = scope != EventSeriesScope.ENTIRE_SERIES,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.event_date_label),
                                style = type.monoMicro,
                                color = colors.inkMuted,
                            )
                            Text(
                                text = LocalDate.ofEpochDay(dateEpochDay).format(dayFormatter),
                                style = type.monoSmall,
                                color = colors.ink,
                            )
                        }
                    }
                    ClockwiseTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = stringResource(R.string.event_title_label),
                        singleLine = true,
                    )
                    EventTimingSection(
                        allDay = allDay,
                        onAllDayChange = { allDay = it },
                        startSeconds = startSeconds,
                        endSeconds = endSeconds,
                        hasEndTime = hasEndTime,
                        onHasEndTimeChange = { hasEndTime = it },
                        onTimeFieldClick = { timeField = it },
                        endDateEpochDay = endDateEpochDay,
                        onEndDateClick = { showEndDatePicker = true },
                        draftIssue = draftIssue,
                        dayFormatter = dayFormatter,
                    )
                    ClockwiseTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = stringResource(R.string.event_note_label),
                    )
                    TagPicker(
                        allTags = allTags,
                        selectedTags = selectedTags,
                        onToggle = onTagToggle,
                    )
                    EventAlarmSection(
                        allDay = allDay,
                        reminderMinutes = reminderMinutes,
                        onReminderChange = { reminderMinutes = it },
                        withAlarm = withAlarm,
                        onWithAlarmChange = { withAlarm = it },
                    )
                    if (isSeries) {
                        Rule()
                        Text(
                            text = stringResource(R.string.apply_scope_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.ink,
                        )
                        ScopeSelector(scope = scope, onScopeChange = { scope = it })
                        when (scope) {
                            EventSeriesScope.ENTIRE_SERIES -> Text(
                                text = stringResource(R.string.scope_all_series_helper),
                                style = type.bodySmall,
                                color = colors.inkMuted,
                            )

                            EventSeriesScope.THIS_AND_SUBSEQUENT ->
                                if (dateEpochDay != event.date) {
                                    Text(
                                        text = stringResource(
                                            R.string.scope_subsequent_date_helper,
                                        ),
                                        style = type.bodySmall,
                                        color = colors.inkMuted,
                                    )
                                }

                            EventSeriesScope.THIS_EVENT -> Unit
                        }
                    }
                }
                Rule()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuietButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                    )
                    InkButton(
                        text = stringResource(R.string.save),
                        onClick = {
                            val startTime = LocalTime.ofSecondOfDay(startSeconds.toLong())
                            onSave(
                                title.trim(),
                                note.trim(),
                                LocalDate.ofEpochDay(dateEpochDay),
                                startTime,
                                if (hasEndTime && !allDay) {
                                    LocalTime.ofSecondOfDay(endSeconds.toLong())
                                } else {
                                    startTime
                                },
                                if (allDay || !hasEndTime) 0 else endDayOffset,
                                allDay,
                                withAlarm && !allDay,
                                if (allDay) Event.REMINDER_OFF else reminderMinutes,
                                selectedTags,
                                scope,
                            )
                        },
                        enabled = isValid,
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateEpochDay * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = millis / 86_400_000L
                            dateEpochDay = picked
                            endDateEpochDay = endDateEpochDay.coerceIn(
                                picked,
                                picked + Event.MAX_EVENT_DAY_SPAN,
                            )
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val endDatePickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDateEpochDay * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endDatePickerState.selectedDateMillis?.let { millis ->
                            val picked = millis / 86_400_000L
                            if (picked >= dateEpochDay) {
                                endDateEpochDay = picked.coerceAtMost(
                                    dateEpochDay + Event.MAX_EVENT_DAY_SPAN,
                                )
                            }
                        }
                        showEndDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = endDatePickerState)
        }
    }

    timeField?.let { field ->
        TimePickerDialog(
            initialSecondsOfDay = if (field == TimeField.START) startSeconds else endSeconds,
            style = timePickerStyle,
            onDismiss = { timeField = null },
            onConfirm = { picked ->
                if (field == TimeField.START) startSeconds = picked else endSeconds = picked
                timeField = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReminderPicker(
    reminderMinutes: Int,
    onReminderChange: (Int) -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    var showCustomDialog by rememberSaveable { mutableStateOf(false) }
    val isCustom = reminderMinutes > 0 && reminderMinutes !in Event.REMINDER_MINUTE_OPTIONS
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.event_reminder_label),
            style = MaterialTheme.typography.titleSmall,
            color = colors.ink,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SegmentedChoice(
                text = stringResource(R.string.reminder_off),
                active = reminderMinutes == Event.REMINDER_OFF,
                onClick = { onReminderChange(Event.REMINDER_OFF) },
            )
            Event.REMINDER_MINUTE_OPTIONS.forEach { minutes ->
                SegmentedChoice(
                    text = stringResource(R.string.reminder_minutes_before, minutes),
                    active = reminderMinutes == minutes,
                    onClick = { onReminderChange(minutes) },
                )
            }
            SegmentedChoice(
                text = if (isCustom) {
                    stringResource(R.string.reminder_minutes_before, reminderMinutes)
                } else {
                    stringResource(R.string.reminder_custom)
                },
                active = isCustom,
                onClick = { showCustomDialog = true },
            )
        }
        if (reminderMinutes > 0) {
            Text(
                text = stringResource(R.string.event_reminder_helper),
                style = type.bodySmall,
                color = colors.inkMuted,
            )
        }
    }
    if (showCustomDialog) {
        ReminderCustomDialog(
            initialMinutes = reminderMinutes.takeIf { isCustom },
            onDismiss = { showCustomDialog = false },
            onConfirm = { minutes ->
                onReminderChange(minutes)
                showCustomDialog = false
            },
        )
    }
}

@Composable
private fun ReminderCustomDialog(
    initialMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    var text by rememberSaveable { mutableStateOf(initialMinutes?.toString().orEmpty()) }
    val minutes = text.toIntOrNull()
    val valid = minutes != null &&
        minutes in Event.MIN_REMINDER_MINUTES..Event.MAX_REMINDER_MINUTES
    ClockwiseBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.reminder_custom_title),
                style = type.title,
                color = colors.ink,
            )
            ClockwiseTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it in '0'..'9' }.take(5) },
                label = stringResource(R.string.reminder_custom_minutes_label),
                supportingText = if (text.isNotEmpty() && !valid) {
                    stringResource(
                        R.string.reminder_custom_invalid,
                        Event.MIN_REMINDER_MINUTES,
                        Event.MAX_REMINDER_MINUTES,
                    )
                } else {
                    stringResource(
                        R.string.reminder_custom_helper,
                        Event.MIN_REMINDER_MINUTES,
                        Event.MAX_REMINDER_MINUTES,
                    )
                },
                isError = text.isNotEmpty() && !valid,
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
                InkButton(
                    text = stringResource(R.string.ok),
                    onClick = { minutes?.let(onConfirm) },
                    enabled = valid,
                )
            }
        }
    }
}

@Composable
private fun ScopeSelector(
    scope: EventSeriesScope,
    onScopeChange: (EventSeriesScope) -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Column {
        EventSeriesScope.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onScopeChange(option) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = scope == option,
                    onClick = { onScopeChange(option) },
                )
                Text(
                    text = stringResource(option.labelRes()),
                    style = type.body,
                    color = colors.ink,
                )
            }
        }
    }
}

private fun EventSeriesScope.labelRes(): Int = when (this) {
    EventSeriesScope.THIS_EVENT -> R.string.scope_this_event
    EventSeriesScope.THIS_AND_SUBSEQUENT -> R.string.scope_this_and_subsequent
    EventSeriesScope.ENTIRE_SERIES -> R.string.scope_entire_series
}
