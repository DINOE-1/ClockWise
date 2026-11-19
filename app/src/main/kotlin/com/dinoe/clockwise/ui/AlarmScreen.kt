package com.dinoe.clockwise.ui

import android.Manifest
import android.content.res.Resources
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dinoe.clockwise.R
import com.dinoe.clockwise.alarm.AlarmPermissions
import com.dinoe.clockwise.alarm.AlarmScheduler
import com.dinoe.clockwise.data.Alarm
import com.dinoe.clockwise.data.ScheduleResult
import com.dinoe.clockwise.data.Tag
import com.dinoe.clockwise.ui.design.Clockwise
import com.dinoe.clockwise.ui.design.ClockwiseBottomSheet
import com.dinoe.clockwise.ui.design.ClockwiseScreenHeader
import com.dinoe.clockwise.ui.design.DangerButton
import com.dinoe.clockwise.ui.design.EmptyNotice
import com.dinoe.clockwise.ui.design.InkButton
import com.dinoe.clockwise.ui.design.PlexMono
import com.dinoe.clockwise.ui.design.QuietButton
import com.dinoe.clockwise.ui.design.TimetableRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val StackPeekStep = 10.dp
private const val StackPeekLayers = 3

// Snackbar has no custom duration, so show it indefinitely and dismiss after this long.
private const val SAVED_NOTICE_MILLIS = 1_500L

// Height and chevron animations of a stack; members leave the drawer list after this long.
private const val STACK_ANIMATION_MILLIS = 220

internal sealed interface AlarmListItem {
    val key: String

    data class Single(val alarm: Alarm) : AlarmListItem {
        override val key: String = "alarm:${alarm.id}"
    }

    data class Stack(val group: AlarmGroup) : AlarmListItem {
        override val key: String = "stack:${group.key}"
    }

    data class Member(val groupKey: String, val alarm: Alarm) : AlarmListItem {
        override val key: String = "alarm:${alarm.id}"
    }
}

// One flat list entry per alarm, so a stack's members become their own keyed lazy items instead
// of a column inside the stack card. Drawer rows stay listed while their collapse animation
// runs so they can shrink out instead of disappearing.
internal fun buildAlarmListItems(
    groups: List<AlarmGroup>,
    expandedGroupKeys: Set<String>,
    drawerExpansion: Boolean,
    collapsingGroupKeys: Set<String> = emptySet(),
): List<AlarmListItem> = buildList {
    groups.forEach { group ->
        if (!group.isStack) {
            add(AlarmListItem.Single(group.front))
            return@forEach
        }
        add(AlarmListItem.Stack(group))
        val showMembers = group.key in expandedGroupKeys ||
            (drawerExpansion && group.key in collapsingGroupKeys)
        if (showMembers) {
            group.members.forEach { add(AlarmListItem.Member(group.key, it)) }
        }
    }
}

private fun formatFireDate(
    epochDay: Long,
    withoutYear: DateTimeFormatter,
    withYear: DateTimeFormatter,
): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val formatter = if (date.year == LocalDate.now().year) withoutYear else withYear
    return formatter.format(date)
}

// Renders the countdown with only the units that matter, e.g. "2d 3h" or "45 min".
private fun formatRingCountdown(resources: Resources, countdown: RingCountdown): String = when {
    countdown.days > 0 -> when {
        countdown.hours > 0 && countdown.minutes > 0 -> resources.getString(
            R.string.duration_days_hours_minutes,
            countdown.days,
            countdown.hours,
            countdown.minutes,
        )
        countdown.hours > 0 -> resources.getString(
            R.string.duration_days_hours,
            countdown.days,
            countdown.hours,
        )
        countdown.minutes > 0 -> resources.getString(
            R.string.duration_days_minutes,
            countdown.days,
            countdown.minutes,
        )
        else -> resources.getString(R.string.duration_days, countdown.days)
    }
    countdown.hours > 0 -> if (countdown.minutes > 0) {
        resources.getString(R.string.duration_hours_minutes, countdown.hours, countdown.minutes)
    } else {
        resources.getString(R.string.duration_hours, countdown.hours)
    }
    else -> resources.getString(R.string.duration_minutes, countdown.minutes)
}

// The banner line keeps its words in Archivo; only the countdown span is set in mono, because a
// count is a numeral. The template places the countdown before or after its words per locale.
private fun ringBannerText(resources: Resources, alarm: Alarm, now: LocalDateTime): AnnotatedString {
    val countdown = formatRingCountdown(resources, ringCountdown(alarm, now))
    val template = resources.getString(R.string.alarm_ring_in, countdown)
    val start = template.indexOf(countdown)
    return buildAnnotatedString {
        append(template)
        if (start >= 0) {
            addStyle(
                SpanStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium),
                start,
                start + countdown.length,
            )
        }
    }
}

@Composable
fun AlarmScreen(
    viewModel: AlarmViewModel = viewModel(factory = AlarmViewModel.factory),
    onOpenEvent: (Long) -> Unit = {},
) {
    val groups by viewModel.alarmGroups.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val alarmTagMap by viewModel.alarmTagMap.collectAsStateWithLifecycle()
    val expandStyle by viewModel.alarmExpandStyle.collectAsStateWithLifecycle()
    val timePickerStyle by viewModel.timePickerStyle.collectAsStateWithLifecycle()
    val expandedGroupKeys = viewModel.expandedGroupKeys
    val drawerExpansion = expandStyle == AlarmStackExpandStyle.DRAWER
    val context = LocalContext.current
    val resources = LocalResources.current
    val errorMessage = writeErrorText(viewModel.writeError)
    val clockFormatter = rememberClockFormatter()
    val fireDateFormatter = rememberDatePatternFormatter("MMMdEEEE")
    val fireDateWithYearFormatter = rememberDatePatternFormatter("yMMMdEEEE")
    val alarmTimeGutter = rememberAlarmTimeGutter()

    // The header counts alarms, not rows: a stack folded shut still holds several alarms.
    val enabledCount = remember(groups) {
        groups.sumOf { group -> group.alarms.count { it.enabled } }
    }

    // Keys of the stacks whose drawer rows are growing into or still shrinking out of the list.
    var expandingGroupKeys by remember { mutableStateOf(emptySet<String>()) }
    var collapsingGroupKeys by remember { mutableStateOf(emptySet<String>()) }
    val listItems = remember(groups, expandedGroupKeys, drawerExpansion, collapsingGroupKeys) {
        buildAlarmListItems(groups, expandedGroupKeys, drawerExpansion, collapsingGroupKeys)
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showAddStackDialog by remember { mutableStateOf(false) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var alarmToEdit by remember { mutableStateOf<Alarm?>(null) }
    var alarmToView by remember { mutableStateOf<Alarm?>(null) }

    // Long-press enters multi-select; taps then toggle rows for a batch delete.
    var selectionMode by remember { mutableStateOf(false) }
    var selectedAlarmIds by remember { mutableStateOf(emptySet<Long>()) }
    var showDeleteSelectionDialog by remember { mutableStateOf(false) }
    val enterSelection: (Alarm) -> Unit = { alarm ->
        selectionMode = true
        selectedAlarmIds = setOf(alarm.id)
        addMenuExpanded = false
    }
    val toggleSelected: (Alarm) -> Unit = { alarm ->
        selectedAlarmIds =
            if (alarm.id in selectedAlarmIds) selectedAlarmIds - alarm.id else selectedAlarmIds + alarm.id
    }
    val exitSelection: () -> Unit = {
        selectionMode = false
        selectedAlarmIds = emptySet()
    }
    BackHandler(enabled = selectionMode) { exitSelection() }

    val openAlarm: (Alarm) -> Unit = { alarm ->
        if (alarm.eventId != null) alarmToView = alarm else alarmToEdit = alarm
    }
    val onAlarmClick: (Alarm) -> Unit = { alarm ->
        if (selectionMode) toggleSelected(alarm) else openAlarm(alarm)
    }
    val onAlarmLongClick: (Alarm) -> Unit = { alarm ->
        if (selectionMode) toggleSelected(alarm) else enterSelection(alarm)
    }

    // Alarms can disappear outside this screen (e.g. linked events deleted), so drop stale ids.
    LaunchedEffect(groups) {
        val currentIds = groups.flatMap { it.alarms }.mapTo(mutableSetOf()) { it.id }
        if (selectedAlarmIds.any { it !in currentIds }) {
            selectedAlarmIds = selectedAlarmIds intersect currentIds
        }
    }

    // Save confirmations appear as a Snackbar so they dismiss themselves after a moment.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showNotice: (String) -> Unit = { message ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = withTimeoutOrNull(SAVED_NOTICE_MILLIS) {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == null) {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
        }
    }
    val showSavedNotice: (Boolean, RingCountdown?) -> Unit = { enabled, countdown ->
        val message = if (enabled && countdown != null) {
            resources.getString(
                R.string.alarm_saved_ring_in,
                formatRingCountdown(resources, countdown),
            )
        } else {
            resources.getString(R.string.alarm_saved_disabled)
        }
        showNotice(message)
    }

    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(message)
        viewModel.consumeError()
    }

    // Drawer rows are listed on their own, so they animate in on expand and stay listed until
    // their shrink finishes; both windows last as long as the row animation.
    val toggleGroup: (String) -> Unit = { key ->
        val collapsing = drawerExpansion && key in expandedGroupKeys
        if (drawerExpansion) {
            if (collapsing) {
                collapsingGroupKeys = collapsingGroupKeys + key
            } else {
                expandingGroupKeys = expandingGroupKeys + key
            }
            scope.launch {
                delay(STACK_ANIMATION_MILLIS.toLong())
                if (collapsing) {
                    collapsingGroupKeys = collapsingGroupKeys - key
                } else {
                    expandingGroupKeys = expandingGroupKeys - key
                }
            }
        }
        viewModel.toggleGroup(key)
    }

    // Bumped on every resume so the permission hints re-evaluate after returning from settings.
    var resumeCount by remember { mutableIntStateOf(0) }
    var permissionStatus by remember { mutableStateOf(AlarmPermissions.read(context)) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeCount++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeCount) {
        permissionStatus = AlarmPermissions.read(context)
    }

    val notificationDeniedMessage = stringResource(R.string.alarm_permission_notification_denied)
    val testScheduledMessage = stringResource(R.string.alarm_test_scheduled)
    val testExactMessage = stringResource(R.string.alarm_test_exact_required)
    val testFailedMessage = stringResource(R.string.alarm_test_failed)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionStatus = AlarmPermissions.read(context)
        if (!granted) {
            Toast.makeText(context, notificationDeniedMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val requestNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(AlarmPermissions.appNotificationSettingsIntent(context))
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Clockwise.colors.paper,
        // The header carries the status bar padding; the tab rail below owns the navigation bar.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (selectionMode) {
                AlarmSelectionHeader(
                    selectedCount = selectedAlarmIds.size,
                    onClose = exitSelection,
                    onDelete = { showDeleteSelectionDialog = true },
                )
            } else {
                AlarmHeader(
                    enabledCount = enabledCount,
                    menuExpanded = addMenuExpanded,
                    onMenuExpandedChange = { addMenuExpanded = it },
                    onAddSingle = {
                        addMenuExpanded = false
                        showAddDialog = true
                    },
                    onAddStack = {
                        addMenuExpanded = false
                        showAddStackDialog = true
                    },
                )
            }
            AlarmRingBanner(
                groups = groups,
                refreshSignal = resumeCount,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (permissionStatus.hasAnyIssue) {
                AlarmReliabilityCard(
                    status = permissionStatus,
                    onClick = { showPermissionDialog = true },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (listItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyNotice(
                        title = stringResource(R.string.alarm_empty),
                        body = stringResource(R.string.alarm_empty_body),
                        actionLabel = stringResource(R.string.add_alarm),
                        onAction = { showAddDialog = true },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    // Drawer rows bring their own gap, so a row that shrinks out takes it with it.
                    verticalArrangement = if (drawerExpansion) {
                        Arrangement.Top
                    } else {
                        Arrangement.spacedBy(8.dp)
                    },
                ) {
                    itemsIndexed(listItems, key = { _, item -> item.key }) { index, item ->
                        // The drawer animates its own height, so let it move the list
                        // directly instead of chasing it with placement animations.
                        val itemModifier = when {
                            !drawerExpansion -> Modifier.animateItem()
                            index == 0 -> Modifier
                            else -> Modifier.padding(top = 8.dp)
                        }
                        when (item) {
                            is AlarmListItem.Single -> AlarmRow(
                                alarm = item.alarm,
                                tags = alarmTagMap[item.alarm.id].orEmpty(),
                                timeGutter = alarmTimeGutter,
                                selecting = selectionMode,
                                selected = item.alarm.id in selectedAlarmIds,
                                modifier = itemModifier,
                                onClick = { onAlarmClick(item.alarm) },
                                onToggle = { enabled -> viewModel.setEnabled(item.alarm, enabled) },
                                onLongClick = { onAlarmLongClick(item.alarm) },
                            )

                            is AlarmListItem.Stack -> AlarmStackCard(
                                group = item.group,
                                tags = alarmTagMap[item.group.front.id].orEmpty(),
                                timeGutter = alarmTimeGutter,
                                selecting = selectionMode,
                                selected = item.group.front.id in selectedAlarmIds,
                                modifier = itemModifier,
                                expanded = item.group.key in expandedGroupKeys,
                                onClick = {
                                    if (selectionMode) {
                                        val groupIds = item.group.alarms.mapTo(mutableSetOf()) { it.id }
                                        selectedAlarmIds = if (groupIds.all { it in selectedAlarmIds }) {
                                            selectedAlarmIds - groupIds
                                        } else {
                                            selectedAlarmIds + groupIds
                                        }
                                    } else {
                                        toggleGroup(item.group.key)
                                    }
                                },
                                onToggle = { enabled -> viewModel.setEnabled(item.group.front, enabled) },
                                onLongClick = { onAlarmLongClick(item.group.front) },
                            )

                            is AlarmListItem.Member -> AlarmMemberRow(
                                alarm = item.alarm,
                                tags = alarmTagMap[item.alarm.id].orEmpty(),
                                timeGutter = alarmTimeGutter,
                                drawer = drawerExpansion,
                                expanded = item.groupKey in expandedGroupKeys,
                                animateIn = item.groupKey in expandingGroupKeys,
                                selecting = selectionMode,
                                selected = item.alarm.id in selectedAlarmIds,
                                onClick = { onAlarmClick(item.alarm) },
                                onToggle = { enabled -> viewModel.setEnabled(item.alarm, enabled) },
                                onLongClick = { onAlarmLongClick(item.alarm) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAlarmDialog(
            allTags = allTags,
            timePickerStyle = timePickerStyle,
            onDismiss = { showAddDialog = false },
            onSave = { seconds, name, note, oneTime, ringtone, tags ->
                viewModel.addAlarm(seconds, name, note, oneTime, ringtone, tags)
                showAddDialog = false
                showSavedNotice(true, ringCountdown(Alarm(time = seconds)))
                if (!permissionStatus.notificationsGranted) {
                    requestNotifications()
                }
            },
        )
    }

    if (showAddStackDialog) {
        AddAlarmStackDialog(
            allTags = allTags,
            timePickerStyle = timePickerStyle,
            onDismiss = { showAddStackDialog = false },
            onSave = { times, name, note, oneTime, ringtone, tags ->
                viewModel.addAlarmStack(times, name, note, oneTime, ringtone, tags)
                showAddStackDialog = false
                showNotice(resources.getString(R.string.alarm_stack_saved, times.size))
                if (!permissionStatus.notificationsGranted) {
                    requestNotifications()
                }
            },
        )
    }

    alarmToEdit?.takeIf { it.eventId == null }?.let { alarm ->
        EditAlarmDialog(
            alarm = alarm,
            allTags = allTags,
            initialTags = alarmTagMap[alarm.id].orEmpty(),
            timePickerStyle = timePickerStyle,
            onDismiss = { alarmToEdit = null },
            onSave = { seconds, name, note, oneTime, ringtone, tags ->
                viewModel.updateAlarmDetails(
                    alarm,
                    seconds,
                    name,
                    note,
                    oneTime,
                    ringtone,
                    tags,
                )
                alarmToEdit = null
                showSavedNotice(
                    alarm.enabled,
                    if (alarm.enabled) ringCountdown(alarm.copy(time = seconds)) else null,
                )
            },
        )
    }

    alarmToView?.let { alarm ->
        var viewedRingtone by remember(alarm.id) { mutableStateOf(alarm.ringtone) }
        AlarmSheet(
            title = alarm.name.ifBlank { stringResource(R.string.alarm_title) },
            onDismiss = { alarmToView = null },
            actions = {
                QuietButton(
                    text = stringResource(R.string.cancel),
                    onClick = { alarmToView = null },
                )
                InkButton(
                    text = stringResource(R.string.edit_source_event),
                    onClick = {
                        alarm.eventId?.let(onOpenEvent)
                        alarmToView = null
                    },
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = clockFormatter.format(LocalTime.ofSecondOfDay(alarm.time.toLong())),
                    style = Clockwise.type.monoMedium,
                    color = Clockwise.colors.ink,
                )
                alarm.fireDate?.let { epochDay ->
                    Text(
                        text = formatFireDate(
                            epochDay,
                            fireDateFormatter,
                            fireDateWithYearFormatter,
                        ),
                        style = Clockwise.type.monoMeta,
                        color = Clockwise.colors.inkMuted,
                    )
                }
            }
            if (alarm.note.isNotBlank()) {
                Text(
                    text = alarm.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Clockwise.colors.ink,
                )
            }
            TagPills(tags = alarmTagMap[alarm.id].orEmpty())
            RingtoneSettingRow(
                title = stringResource(R.string.alarm_ringtone_label),
                value = viewedRingtone,
                supportingText = if (alarm.stackId != null) {
                    stringResource(R.string.alarm_ringtone_stack_hint)
                } else {
                    null
                },
                onValueChange = { value ->
                    viewedRingtone = value
                    viewModel.setRingtone(alarm, value)
                },
            )
            Text(
                text = stringResource(R.string.alarm_auto_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Clockwise.colors.inkMuted,
            )
        }
    }

    if (showDeleteSelectionDialog && selectedAlarmIds.isNotEmpty()) {
        AlarmSheet(
            title = stringResource(R.string.delete_alarm),
            onDismiss = { showDeleteSelectionDialog = false },
            actions = {
                QuietButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showDeleteSelectionDialog = false },
                )
                DangerButton(
                    text = stringResource(R.string.delete),
                    onClick = {
                        val selected =
                            groups.flatMap { it.alarms }.filter { it.id in selectedAlarmIds }
                        viewModel.deleteAlarms(selected)
                        showDeleteSelectionDialog = false
                        exitSelection()
                    },
                )
            },
        ) {
            val count = selectedAlarmIds.size
            Text(
                text = pluralStringResource(
                    R.plurals.alarm_multi_delete_confirmation,
                    count,
                    count,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Clockwise.colors.inkMuted,
            )
        }
    }

    if (showPermissionDialog) {
        AlarmPermissionDialog(
            status = permissionStatus,
            onDismiss = { showPermissionDialog = false },
            onRefresh = { permissionStatus = AlarmPermissions.read(context) },
            onRequestNotifications = requestNotifications,
            onTestAlarm = {
                val message = when (AlarmScheduler.scheduleTestAlarm(context)) {
                    is ScheduleResult.Scheduled -> testScheduledMessage
                    ScheduleResult.ExactPermissionDenied -> testExactMessage
                    else -> testFailedMessage
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

// The two ways to add an alarm live in the menu behind the header's add action.
@Composable
private fun AlarmHeader(
    enabledCount: Int,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onAddSingle: () -> Unit,
    onAddStack: () -> Unit,
) {
    val colors = Clockwise.colors
    ClockwiseScreenHeader(
        title = stringResource(R.string.alarm_title),
        overline = stringResource(R.string.alarm_enabled_count, enabledCount),
        actions = {
            Box {
                IconButton(
                    onClick = { onMenuExpandedChange(!menuExpanded) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.add_alarm),
                        tint = colors.ink,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) },
                    containerColor = colors.sheet,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_alarm_single)) },
                        onClick = onAddSingle,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_alarm_stack)) },
                        onClick = onAddStack,
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmSelectionHeader(
    selectedCount: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Clockwise.colors
    ClockwiseScreenHeader(
        title = stringResource(R.string.alarm_selection_title, selectedCount),
        actions = {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.exit_selection),
                    tint = colors.ink,
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = selectedCount > 0,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = if (selectedCount > 0) colors.signal else colors.inkFaint,
                )
            }
        },
    )
}

// Owns the minute ticker and the next-ring lookup so only the banner, not the whole list,
// refreshes as time passes. [refreshSignal] restarts the tick when the screen resumes.
@Composable
private fun AlarmRingBanner(
    groups: List<AlarmGroup>,
    refreshSignal: Int,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(refreshSignal) {
        while (true) {
            val current = LocalDateTime.now()
            now = current
            val millisIntoMinute = current.second * 1_000L + current.nano / 1_000_000L
            delay((60_000L - millisIntoMinute).coerceAtLeast(1_000L))
        }
    }
    val nextRingAlarm = remember(groups, now) { groups.nextRingAlarm(now) } ?: return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = Clockwise.colors.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Clockwise.colors.signal, CircleShape),
            )
            Text(
                text = ringBannerText(resources, nextRingAlarm, now),
                style = Clockwise.type.body,
                color = Clockwise.colors.ink,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun AlarmStackCard(
    group: AlarmGroup,
    tags: List<Tag>,
    timeGutter: Dp,
    selecting: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = Clockwise.colors
    val expansion by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = STACK_ANIMATION_MILLIS),
        label = "stackExpansion",
    )
    val peekLayers = minOf(StackPeekLayers, group.alarms.size - 1)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = StackPeekStep * peekLayers * (1f - expansion)),
    ) {
        // Draw the deepest slip first so the layers in front paint over it. Expanding slides
        // each slip back under the front card.
        for (depth in peekLayers downTo 1) {
            Surface(
                modifier = Modifier
                    .matchParentSize()
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (StackPeekStep * depth * (1f - expansion)).roundToPx(),
                        )
                    },
                shape = MaterialTheme.shapes.large,
                color = colors.surfaceContainer,
            ) {}
        }
        AlarmRow(
            alarm = group.front,
            tags = tags,
            timeGutter = timeGutter,
            selecting = selecting,
            selected = selected,
            stackCount = group.alarms.size,
            stackExpanded = expanded,
            onClick = onClick,
            onToggle = onToggle,
            onLongClick = onLongClick,
        )
    }
}

// One stack member. Drawer rows are listed on their own, so each reveals itself; fade rows are
// added and removed with the group and animate their placement instead.
@Composable
private fun LazyItemScope.AlarmMemberRow(
    alarm: Alarm,
    tags: List<Tag>,
    timeGutter: Dp,
    drawer: Boolean,
    expanded: Boolean,
    animateIn: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onLongClick: () -> Unit,
) {
    if (!drawer) {
        AlarmRow(
            alarm = alarm,
            tags = tags,
            timeGutter = timeGutter,
            selecting = selecting,
            selected = selected,
            modifier = Modifier
                .padding(start = 12.dp)
                .animateItem(),
            onClick = onClick,
            onToggle = onToggle,
            onLongClick = onLongClick,
        )
        return
    }
    // Only a row added by an expanding stack starts collapsed; a row that merely changed place,
    // or one restored by scrolling, keeps its expanded height.
    var revealed by rememberSaveable { mutableStateOf(!animateIn) }
    LaunchedEffect(expanded) { revealed = expanded }
    AnimatedVisibility(
        visible = revealed,
        enter = expandVertically(
            animationSpec = tween(durationMillis = STACK_ANIMATION_MILLIS),
            expandFrom = Alignment.Top,
        ),
        exit = shrinkVertically(
            animationSpec = tween(durationMillis = STACK_ANIMATION_MILLIS),
            shrinkTowards = Alignment.Top,
        ),
    ) {
        AlarmRow(
            alarm = alarm,
            tags = tags,
            timeGutter = timeGutter,
            selecting = selecting,
            selected = selected,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp),
            onClick = onClick,
            onToggle = onToggle,
            onLongClick = onLongClick,
        )
    }
}

/**
 * The time column of an alarm row. Its width is a locale question, not a design constant: the
 * Chinese 12-hour clock puts two full-width characters in front of the digits, the English one puts
 * two Latin characters after them, and a fixed column let the widest of them paint into the name.
 * Measuring every hour gives one width for the whole list, so the times still line up.
 */
@Composable
private fun rememberAlarmTimeGutter(): Dp {
    val clockFormatter = rememberClockFormatter()
    val style = Clockwise.type.monoLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(clockFormatter, style, measurer, density) {
        val widest = (0..23).maxOf { hour ->
            measurer.measure(
                clockFormatter.formatPaddedHour(LocalTime.of(hour, 0)),
                style,
            ).size.width
        }
        with(density) { widest.toDp() }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    tags: List<Tag>,
    timeGutter: Dp,
    selecting: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    stackCount: Int? = null,
    stackExpanded: Boolean = false,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val timeColor = if (alarm.enabled) colors.ink else colors.inkMuted
    val nameColor = if (alarm.enabled) colors.ink else colors.inkMuted
    val metaColor = if (alarm.enabled) colors.inkMuted else colors.inkFaint
    val clockFormatter = rememberClockFormatter()
    val fireDateFormatter = rememberDatePatternFormatter("MMMdEEEE")
    val fireDateWithYearFormatter = rememberDatePatternFormatter("yMMMdEEEE")
    val fireDateLabel = alarm.fireDate?.let {
        formatFireDate(it, fireDateFormatter, fireDateWithYearFormatter)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        TimetableRow(
            modifier = if (selected) {
                Modifier.border(1.5.dp, colors.signal, MaterialTheme.shapes.large)
            } else {
                Modifier
            },
            gutterWidth = timeGutter,
            selected = selected,
            // The time is set in monoLarge, taller than a name-only entry; center the name on it
            // instead of letting it hang from the top of the row.
            contentAlignment = Alignment.CenterVertically,
            onClick = onClick,
            onLongClick = onLongClick,
            gutter = {
                Text(
                    text = clockFormatter.formatPaddedHour(
                        LocalTime.ofSecondOfDay(alarm.time.toLong()),
                    ),
                    style = type.monoLarge,
                    color = timeColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            },
            trailing = {
                if (selecting) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                } else if (stackCount == null || stackExpanded) {
                    Switch(
                        checked = alarm.enabled,
                        onCheckedChange = onToggle,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            },
        ) {
            if (alarm.name.isNotBlank()) {
                Text(
                    text = alarm.name,
                    style = type.title,
                    color = nameColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (fireDateLabel != null || alarm.oneTime) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (fireDateLabel != null) {
                        Text(text = fireDateLabel, style = type.monoMeta, color = metaColor)
                    }
                    if (alarm.oneTime) {
                        Text(
                            text = stringResource(R.string.alarm_one_time_badge),
                            style = type.bodySmall,
                            color = metaColor,
                        )
                    }
                }
            }
            if (alarm.note.isNotBlank()) {
                Text(
                    text = alarm.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = metaColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (tags.isNotEmpty()) {
                TagPills(tags = tags, modifier = Modifier.padding(top = 6.dp))
            }
            if (stackCount != null) {
                val chevronRotation by animateFloatAsState(
                    targetValue = if (stackExpanded) 180f else 0f,
                    animationSpec = tween(durationMillis = 220),
                    label = "stackChevron",
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.alarm_stack_count, stackCount),
                        style = type.monoMeta,
                        color = metaColor,
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (stackExpanded) {
                                R.string.alarm_stack_collapse
                            } else {
                                R.string.alarm_stack_expand
                            },
                        ),
                        tint = metaColor,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(chevronRotation),
                    )
                }
            }
        }
    }
}

/** A sheet rising from the bottom edge: a title, the fields, then the actions on one line. */
@Composable
private fun AlarmSheet(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ClockwiseBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = title, style = Clockwise.type.title, color = Clockwise.colors.ink)
            content()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAlarmDialog(
    allTags: List<Tag>,
    timePickerStyle: TimePickerStyle,
    onDismiss: () -> Unit,
    onSave: (Int, String, String, Boolean, String, List<Tag>) -> Unit,
) {
    var secondsOfDay by rememberSaveable { mutableIntStateOf(defaultAlarmTime()) }
    var name by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var oneTime by rememberSaveable { mutableStateOf(false) }
    var ringtone by rememberSaveable { mutableStateOf(Alarm.DEFAULT_RINGTONE) }
    var selectedTagIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var showTimePicker by remember { mutableStateOf(false) }

    val selectedTags = allTags.filter { it.id in selectedTagIds }
    val onTagToggle: (Tag) -> Unit = { tag ->
        selectedTagIds = toggleTagSelection(selectedTagIds, tag.id)
    }

    AlarmSheet(
        title = stringResource(R.string.add_alarm),
        onDismiss = onDismiss,
        actions = {
            QuietButton(text = stringResource(R.string.cancel), onClick = onDismiss)
            InkButton(
                text = stringResource(R.string.save),
                onClick = {
                    onSave(
                        secondsOfDay,
                        name.trim(),
                        note.trim(),
                        oneTime,
                        ringtone,
                        selectedTags,
                    )
                },
            )
        },
    ) {
        TimeSlotButton(
            label = stringResource(R.string.alarm_time_label),
            secondsOfDay = secondsOfDay,
            onClick = { showTimePicker = true },
            modifier = Modifier.fillMaxWidth(),
        )
        AlarmFormFields(
            name = name,
            onNameChange = { name = it },
            note = note,
            onNoteChange = { note = it },
            oneTime = oneTime,
            onOneTimeChange = { oneTime = it },
            ringtone = ringtone,
            onRingtoneChange = { ringtone = it },
            ringtoneSupportingText = null,
            allTags = allTags,
            selectedTags = selectedTags,
            onTagToggle = onTagToggle,
        )
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialSecondsOfDay = secondsOfDay,
            style = timePickerStyle,
            onDismiss = { showTimePicker = false },
            onConfirm = { picked ->
                secondsOfDay = picked
                showTimePicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAlarmStackDialog(
    allTags: List<Tag>,
    timePickerStyle: TimePickerStyle,
    onDismiss: () -> Unit,
    onSave: (List<Int>, String, String, Boolean, String, List<Tag>) -> Unit,
) {
    var times by rememberSaveable { mutableStateOf(listOf(defaultAlarmTime())) }
    var name by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var oneTime by rememberSaveable { mutableStateOf(false) }
    var ringtone by rememberSaveable { mutableStateOf(Alarm.DEFAULT_RINGTONE) }
    var selectedTagIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var timePickerIndex by remember { mutableStateOf<Int?>(null) }

    val selectedTags = allTags.filter { it.id in selectedTagIds }
    val onTagToggle: (Tag) -> Unit = { tag ->
        selectedTagIds = toggleTagSelection(selectedTagIds, tag.id)
    }
    val hasDuplicateTimes = times.distinct().size != times.size
    val canSave = times.size >= 2 && !hasDuplicateTimes

    AlarmSheet(
        title = stringResource(R.string.add_alarm_stack_title),
        onDismiss = onDismiss,
        actions = {
            QuietButton(text = stringResource(R.string.cancel), onClick = onDismiss)
            InkButton(
                text = stringResource(R.string.save),
                enabled = canSave,
                onClick = {
                    onSave(
                        times,
                        name.trim(),
                        note.trim(),
                        oneTime,
                        ringtone,
                        selectedTags,
                    )
                },
            )
        },
    ) {
        Text(
            text = stringResource(R.string.alarm_stack_times_helper),
            style = MaterialTheme.typography.bodySmall,
            color = Clockwise.colors.inkMuted,
        )
        times.forEachIndexed { index, seconds ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeSlotButton(
                    label = stringResource(R.string.alarm_stack_time_number, index + 1),
                    secondsOfDay = seconds,
                    onClick = { timePickerIndex = index },
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        times = times.filterIndexed { timeIndex, _ ->
                            timeIndex != index
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(
                            R.string.alarm_stack_remove_time,
                        ),
                        tint = Clockwise.colors.inkMuted,
                    )
                }
            }
        }
        QuietButton(
            text = stringResource(R.string.alarm_stack_add_time),
            onClick = { times = times + nextStackTime(times) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (times.size < 2) {
            Text(
                text = stringResource(R.string.alarm_stack_min_times),
                style = MaterialTheme.typography.bodySmall,
                color = Clockwise.colors.signal,
            )
        } else if (hasDuplicateTimes) {
            Text(
                text = stringResource(R.string.alarm_stack_duplicate_time),
                style = MaterialTheme.typography.bodySmall,
                color = Clockwise.colors.signal,
            )
        }
        AlarmFormFields(
            name = name,
            onNameChange = { name = it },
            note = note,
            onNoteChange = { note = it },
            oneTime = oneTime,
            onOneTimeChange = { oneTime = it },
            ringtone = ringtone,
            onRingtoneChange = { ringtone = it },
            ringtoneSupportingText = stringResource(R.string.alarm_ringtone_stack_hint),
            allTags = allTags,
            selectedTags = selectedTags,
            onTagToggle = onTagToggle,
        )
    }

    timePickerIndex?.let { index ->
        TimePickerDialog(
            initialSecondsOfDay = times.getOrElse(index) { defaultAlarmTime() },
            style = timePickerStyle,
            onDismiss = { timePickerIndex = null },
            onConfirm = { picked ->
                if (index in times.indices) {
                    times = times.toMutableList().also { it[index] = picked }
                }
                timePickerIndex = null
            },
        )
    }
}

@Composable
private fun EditAlarmDialog(
    alarm: Alarm,
    allTags: List<Tag>,
    initialTags: List<Tag>,
    timePickerStyle: TimePickerStyle,
    onDismiss: () -> Unit,
    onSave: (Int, String, String, Boolean, String, List<Tag>) -> Unit,
) {
    var secondsOfDay by rememberSaveable(alarm.id) { mutableIntStateOf(alarm.time) }
    var name by rememberSaveable(alarm.id) { mutableStateOf(alarm.name) }
    var note by rememberSaveable(alarm.id) { mutableStateOf(alarm.note) }
    var oneTime by rememberSaveable(alarm.id) { mutableStateOf(alarm.oneTime) }
    var ringtone by rememberSaveable(alarm.id) { mutableStateOf(alarm.ringtone) }
    var selectedTagIds by rememberSaveable(alarm.id) {
        mutableStateOf(initialTags.map { it.id }.toLongArray())
    }
    var showTimePicker by remember { mutableStateOf(false) }

    val selectedTags = allTags.filter { it.id in selectedTagIds }
    val onTagToggle: (Tag) -> Unit = { tag ->
        selectedTagIds = toggleTagSelection(selectedTagIds, tag.id)
    }

    AlarmSheet(
        title = stringResource(R.string.edit_alarm),
        onDismiss = onDismiss,
        actions = {
            QuietButton(text = stringResource(R.string.cancel), onClick = onDismiss)
            InkButton(
                text = stringResource(R.string.save),
                onClick = {
                    onSave(
                        secondsOfDay,
                        name.trim(),
                        note.trim(),
                        oneTime,
                        ringtone,
                        selectedTags,
                    )
                },
            )
        },
    ) {
        TimeSlotButton(
            label = stringResource(R.string.alarm_time_label),
            secondsOfDay = secondsOfDay,
            onClick = { showTimePicker = true },
            modifier = Modifier.fillMaxWidth(),
        )
        AlarmFormFields(
            name = name,
            onNameChange = { name = it },
            note = note,
            onNoteChange = { note = it },
            oneTime = oneTime,
            onOneTimeChange = { oneTime = it },
            ringtone = ringtone,
            onRingtoneChange = { ringtone = it },
            ringtoneSupportingText = if (alarm.stackId != null) {
                stringResource(R.string.alarm_ringtone_stack_hint)
            } else {
                null
            },
            allTags = allTags,
            selectedTags = selectedTags,
            onTagToggle = onTagToggle,
        )
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialSecondsOfDay = secondsOfDay,
            style = timePickerStyle,
            onDismiss = { showTimePicker = false },
            onConfirm = { picked ->
                secondsOfDay = picked
                showTimePicker = false
            },
        )
    }
}

// Default for a new alarm: the next five-minute mark.
private fun defaultAlarmTime(): Int {
    val now = LocalTime.now()
    val minutes = ((now.minute / 5) + 1) * 5
    return now.withSecond(0).withNano(0).withMinute(0).plusMinutes(minutes.toLong()).toSecondOfDay()
}

private const val STACK_TIME_STEP_SECONDS = 5 * 60
private const val SECONDS_PER_DAY = 24 * 60 * 60

// Next suggested stack slot: five minutes after the latest time, skipping times already used.
private fun nextStackTime(times: List<Int>): Int {
    val latest = times.maxOrNull() ?: defaultAlarmTime()
    var candidate = (latest + STACK_TIME_STEP_SECONDS) % SECONDS_PER_DAY
    var attempts = 0
    while (candidate in times && attempts < SECONDS_PER_DAY / STACK_TIME_STEP_SECONDS) {
        candidate = (candidate + STACK_TIME_STEP_SECONDS) % SECONDS_PER_DAY
        attempts++
    }
    return candidate
}
