package com.dinoe.clockwise.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dinoe.clockwise.R
import com.dinoe.clockwise.alarm.AlarmPermissions
import com.dinoe.clockwise.alarm.AlarmScheduler
import com.dinoe.clockwise.data.ScheduleResult
import com.dinoe.clockwise.ui.design.Clockwise
import com.dinoe.clockwise.ui.design.ClockwiseCard
import com.dinoe.clockwise.ui.design.ClockwiseColors
import com.dinoe.clockwise.ui.design.ClockwiseScreenHeader
import com.dinoe.clockwise.ui.design.EnamelLight
import com.dinoe.clockwise.ui.design.GraphiteNight
import com.dinoe.clockwise.ui.design.GruvboxDial
import com.dinoe.clockwise.ui.design.Rule
import com.dinoe.clockwise.ui.design.SectionLabel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    openAiSettingsRequest: Boolean = false,
    onOpenAiSettingsRequestHandled: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    var showAiSettings by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openAiSettingsRequest) {
        if (openAiSettingsRequest) {
            showAiSettings = true
            onOpenAiSettingsRequestHandled()
        }
    }
    if (showAiSettings) {
        AiSettingsScreen(onBack = { showAiSettings = false })
        return
    }

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val snapInterval by viewModel.snapInterval.collectAsStateWithLifecycle()
    val alarmExpandStyle by viewModel.alarmExpandStyle.collectAsStateWithLifecycle()
    val timePickerStyle by viewModel.timePickerStyle.collectAsStateWithLifecycle()
    val defaultRingtone by viewModel.defaultRingtone.collectAsStateWithLifecycle()
    val aiConfig by viewModel.aiConfig.collectAsStateWithLifecycle()
    val activeAi = aiConfig?.active()
    val aiSummary = if (activeAi?.hasKey == true) {
        stringResource(
            R.string.ai_settings_summary_format,
            activeAi.preset.displayName,
            activeAi.modelId,
        )
    } else {
        stringResource(R.string.ai_settings_entry_unconfigured)
    }
    val context = LocalContext.current
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
    val testAlarm: () -> Unit = {
        val message = when (AlarmScheduler.scheduleTestAlarm(context)) {
            is ScheduleResult.Scheduled -> testScheduledMessage
            ScheduleResult.ExactPermissionDenied -> testExactMessage
            else -> testFailedMessage
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    val activity = LocalActivity.current
    val selectLanguage: (AppLanguage) -> Unit = { selected ->
        if (selected != language) {
            viewModel.setLanguage(selected)
            activity?.recreate()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ClockwiseScreenHeader(title = stringResource(R.string.settings_title))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {

            // Owned by the alarm work; the call stays so this screen keeps its permission entry.
            AlarmReliabilityCard(
                status = permissionStatus,
                onClick = { showPermissionDialog = true },
            )

            SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
            SettingsCard {
                Column(modifier = Modifier.selectableGroup()) {
                    SelectableOptionRow(
                        label = stringResource(R.string.theme_system),
                        selected = themeMode == ThemeMode.SYSTEM,
                        specimen = listOf(EnamelLight, GraphiteNight),
                        onSelect = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                    )
                    SettingsDivider()
                    SelectableOptionRow(
                        label = stringResource(R.string.theme_light),
                        selected = themeMode == ThemeMode.LIGHT,
                        specimen = listOf(EnamelLight),
                        onSelect = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                    )
                    SettingsDivider()
                    SelectableOptionRow(
                        label = stringResource(R.string.theme_dark),
                        selected = themeMode == ThemeMode.DARK,
                        specimen = listOf(GraphiteNight),
                        onSelect = { viewModel.setThemeMode(ThemeMode.DARK) },
                    )
                    SettingsDivider()
                    SelectableOptionRow(
                        label = stringResource(R.string.theme_gruvbox),
                        selected = themeMode == ThemeMode.GRUVBOX,
                        specimen = listOf(GruvboxDial),
                        onSelect = { viewModel.setThemeMode(ThemeMode.GRUVBOX) },
                    )
                }
            }

            SettingsSectionHeader(stringResource(R.string.settings_section_language))
            SettingsCard {
                Column(modifier = Modifier.selectableGroup()) {
                    SelectableOptionRow(
                        label = stringResource(R.string.language_system),
                        selected = language == AppLanguage.SYSTEM,
                        onSelect = { selectLanguage(AppLanguage.SYSTEM) },
                    )
                    SettingsDivider()
                    SelectableOptionRow(
                        label = stringResource(R.string.language_traditional_chinese),
                        selected = language == AppLanguage.TRADITIONAL_CHINESE,
                        onSelect = { selectLanguage(AppLanguage.TRADITIONAL_CHINESE) },
                    )
                    SettingsDivider()
                    SelectableOptionRow(
                        label = stringResource(R.string.language_english),
                        selected = language == AppLanguage.ENGLISH,
                        onSelect = { selectLanguage(AppLanguage.ENGLISH) },
                    )
                }
            }
            SettingsGroupNote(stringResource(R.string.set_language_restart_note))

            SettingsSectionHeader(stringResource(R.string.settings_section_time))
            SettingsCard {
                SnapIntervalSetting(
                    value = snapInterval,
                    onValueChangeFinished = { viewModel.setSnapInterval(it) },
                )
                SettingsDivider()
                SegmentedPreference(
                    title = stringResource(R.string.time_picker_style_label),
                    options = listOf(
                        TimePickerStyle.WHEEL to stringResource(R.string.time_picker_style_wheel),
                        TimePickerStyle.CLOCK to stringResource(R.string.time_picker_style_clock),
                    ),
                    selected = timePickerStyle,
                    onSelect = { viewModel.setTimePickerStyle(it) },
                )
            }

            SettingsSectionHeader(stringResource(R.string.settings_section_alarm))
            SettingsCard {
                SegmentedPreference(
                    title = stringResource(R.string.alarm_expand_style_label),
                    options = listOf(
                        AlarmStackExpandStyle.DRAWER to
                            stringResource(R.string.alarm_expand_style_drawer_short),
                        AlarmStackExpandStyle.FADE to
                            stringResource(R.string.alarm_expand_style_fade_short),
                    ),
                    selected = alarmExpandStyle,
                    onSelect = { viewModel.setAlarmExpandStyle(it) },
                    supportingText = stringResource(
                        if (alarmExpandStyle == AlarmStackExpandStyle.DRAWER) {
                            R.string.alarm_expand_style_drawer_hint
                        } else {
                            R.string.alarm_expand_style_fade_hint
                        },
                    ),
                )
                SettingsDivider()
                RingtoneSettingRow(
                    title = stringResource(R.string.settings_default_ringtone_label),
                    value = defaultRingtone,
                    supportingText = stringResource(R.string.settings_default_ringtone_helper),
                    useSystemDefaultLabel = true,
                    onValueChange = viewModel::setDefaultRingtone,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            SettingsSectionHeader(stringResource(R.string.settings_section_ai))
            SettingsCard {
                SettingsActionRow(
                    title = stringResource(R.string.ai_settings_entry_title),
                    subtitle = aiSummary,
                    onClick = { showAiSettings = true },
                )
            }

            SettingsBackupSection()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showPermissionDialog) {
        AlarmPermissionDialog(
            status = permissionStatus,
            onDismiss = { showPermissionDialog = false },
            onRefresh = { permissionStatus = AlarmPermissions.read(context) },
            onRequestNotifications = requestNotifications,
            onTestAlarm = testAlarm,
        )
    }
}

@Composable
internal fun SettingsSectionHeader(text: String) {
    SectionLabel(text = text, modifier = Modifier.semantics { heading() })
}

/** The rows of one group, gathered on a rounded card. */
@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    ClockwiseCard(content = content)
}

/** Dense rows inside one card only; groups are separated by space, never by a rule. */
@Composable
internal fun SettingsDivider() {
    Rule(modifier = Modifier.padding(horizontal = 16.dp))
}

/** A note under a group: one line of helper text aligned with the section labels. */
@Composable
private fun SettingsGroupNote(text: String) {
    Text(
        text = text,
        style = Clockwise.type.bodySmall,
        color = Clockwise.colors.inkMuted,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
    )
}

@Composable
internal fun SettingsActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.wash),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, style = type.body, color = colors.ink)
            Text(
                text = subtitle,
                style = type.bodySmall,
                color = colors.inkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.inkMuted,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(20.dp),
        )
    }
}

// The dragged value lives here so moving the slider does not invalidate the settings page.
@Composable
private fun SnapIntervalSetting(
    value: Int,
    onValueChangeFinished: (Int) -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    var sliderValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.snap_interval_label),
                style = type.body,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    R.string.snap_interval_minutes,
                    sliderValue.roundToInt(),
                ),
                style = type.monoSmall,
                color = colors.inkMuted,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChangeFinished(sliderValue.roundToInt()) },
            valueRange = MIN_SNAP_INTERVAL_MINUTES.toFloat()..MAX_SNAP_INTERVAL_MINUTES.toFloat(),
            steps = (MAX_SNAP_INTERVAL_MINUTES - MIN_SNAP_INTERVAL_MINUTES) /
                SNAP_INTERVAL_STEP_MINUTES - 1,
            colors = SliderDefaults.colors(
                thumbColor = colors.ink,
                activeTrackColor = colors.ink,
                inactiveTrackColor = colors.surfaceHigh,
                activeTickColor = colors.paper,
                inactiveTickColor = colors.ruleStrong,
            ),
        )
        Text(
            text = stringResource(R.string.snap_interval_helper),
            style = type.bodySmall,
            color = colors.inkMuted,
        )
    }
}

@Composable
private fun <T> SegmentedPreference(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    supportingText: String? = null,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = title, style = type.body, color = colors.ink)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (option, label) ->
                val optionSelected = option == selected
                SegmentedButton(
                    selected = optionSelected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier.height(48.dp),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = colors.wash,
                        activeContentColor = colors.ink,
                        activeBorderColor = colors.ruleStrong,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = colors.inkMuted,
                        inactiveBorderColor = colors.rule,
                    ),
                ) {
                    Text(
                        text = label,
                        style = type.label,
                        color = if (optionSelected) colors.ink else colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (supportingText != null) {
            Text(text = supportingText, style = type.bodySmall, color = colors.inkMuted)
        }
    }
}

@Composable
internal fun SelectableOptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    specimen: List<ClockwiseColors> = emptyList(),
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) colors.wash else Color.Transparent)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.ink,
                unselectedColor = colors.inkMuted,
            ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = type.body,
            color = if (selected) colors.ink else colors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (specimen.isNotEmpty()) {
            Spacer(modifier = Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                specimen.forEach { palette -> PaletteSpecimen(palette) }
            }
        }
    }
}

/** A miniature of a palette on a rounded chip: its page, ink and signal as round dots. */
@Composable
private fun PaletteSpecimen(palette: ClockwiseColors) {
    val colors = Clockwise.colors
    Row(
        modifier = Modifier
            .size(width = 54.dp, height = 24.dp)
            .clip(MaterialTheme.shapes.small)
            .background(colors.surfaceContainer)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(palette.paper)
                .border(1.dp, colors.rule, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(palette.ink),
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(palette.signal),
        )
    }
}
