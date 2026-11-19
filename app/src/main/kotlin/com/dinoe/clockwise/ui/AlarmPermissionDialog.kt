package com.dinoe.clockwise.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dinoe.clockwise.R
import com.dinoe.clockwise.alarm.AlarmPermissionStatus
import com.dinoe.clockwise.alarm.AlarmPermissions
import com.dinoe.clockwise.ui.design.Clockwise
import com.dinoe.clockwise.ui.design.ClockwiseBottomSheet
import com.dinoe.clockwise.ui.design.ClockwiseCard
import com.dinoe.clockwise.ui.design.InkButton
import com.dinoe.clockwise.ui.design.QuietButton
import com.dinoe.clockwise.ui.design.SectionLabel

/**
 * Every setting an alarm depends on, on one sheet: what is missing is printed in signal, what is
 * fine stays ink, and each row offers the row's own fix as a quiet button.
 */
@Composable
fun AlarmPermissionDialog(
    status: AlarmPermissionStatus,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onRequestNotifications: () -> Unit,
    onTestAlarm: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val hasIssue = status.hasAnyIssue

    ClockwiseBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.86f)) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.alarm_permission_title),
                    style = type.title,
                    color = colors.ink,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (hasIssue) colors.signal else colors.ink, CircleShape),
                    )
                    Text(
                        text = stringResource(
                            if (hasIssue) {
                                R.string.alarm_permission_summary_missing
                            } else {
                                R.string.alarm_permission_summary_ok
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasIssue) colors.signal else colors.inkMuted,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.alarm_permission_section_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
                AlarmPermissionRows(
                    status = status,
                    onRequestNotifications = onRequestNotifications,
                )
                Text(
                    text = stringResource(R.string.alarm_permission_test_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuietButton(
                    text = stringResource(R.string.alarm_test_button),
                    onClick = onTestAlarm,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuietButton(
                        text = stringResource(R.string.alarm_permission_recheck),
                        onClick = onRefresh,
                    )
                    InkButton(
                        text = stringResource(R.string.alarm_permission_close),
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
fun AlarmPermissionRows(
    status: AlarmPermissionStatus,
    onRequestNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PermissionGroup(title = stringResource(R.string.alarm_android_section_title)) {
            PermissionRow(
                title = stringResource(R.string.alarm_notification_permission_title),
                body = stringResource(R.string.alarm_notification_permission_hint),
                granted = status.notificationsGranted,
                actionLabel = if (status.notificationsGranted) {
                    null
                } else {
                    stringResource(R.string.open_settings)
                },
                onAction = if (status.notificationsGranted) null else onRequestNotifications,
            )
            if (status.channelBlocked) {
                PermissionRow(
                    title = stringResource(R.string.alarm_channel_title),
                    body = stringResource(R.string.alarm_channel_blocked),
                    granted = false,
                    actionLabel = stringResource(R.string.open_settings),
                    onAction = {
                        AlarmPermissions.open(
                            context,
                            AlarmPermissions.appNotificationSettingsIntent(context),
                        )
                    },
                )
            }
            if (status.dndActive) {
                PermissionRow(
                    title = stringResource(R.string.alarm_dnd_title),
                    body = stringResource(R.string.alarm_dnd_body),
                    granted = false,
                    actionLabel = stringResource(R.string.open_settings),
                    onAction = {
                        AlarmPermissions.open(context, AlarmPermissions.dndAccessIntent())
                    },
                )
            }
            PermissionRow(
                title = stringResource(R.string.alarm_exact_permission_title),
                body = stringResource(R.string.alarm_exact_permission_hint),
                granted = status.exactAlarmsGranted,
                actionLabel = if (status.exactAlarmsGranted) {
                    null
                } else {
                    stringResource(R.string.open_settings)
                },
                onAction = if (status.exactAlarmsGranted) {
                    null
                } else {
                    {
                        AlarmPermissions.open(
                            context,
                            AlarmPermissions.exactAlarmIntent(context),
                        )
                    }
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PermissionRow(
                    title = stringResource(R.string.alarm_full_screen_permission_title),
                    body = stringResource(R.string.alarm_full_screen_permission_hint),
                    granted = status.fullScreenIntentGranted,
                    actionLabel = if (status.fullScreenIntentGranted) {
                        null
                    } else {
                        stringResource(R.string.open_settings)
                    },
                    onAction = if (status.fullScreenIntentGranted) {
                        null
                    } else {
                        {
                            AlarmPermissions.open(
                                context,
                                AlarmPermissions.fullScreenIntentIntent(context),
                            )
                        }
                    },
                )
            }
            PermissionRow(
                title = stringResource(R.string.alarm_battery_permission_title),
                body = stringResource(R.string.alarm_battery_permission_hint),
                granted = status.batteryOptimizationIgnored,
                actionLabel = if (status.batteryOptimizationIgnored) {
                    null
                } else {
                    stringResource(R.string.open_settings)
                },
                onAction = if (status.batteryOptimizationIgnored) {
                    null
                } else {
                    {
                        AlarmPermissions.open(
                            context,
                            AlarmPermissions.batteryOptimizationIntent(),
                        )
                    }
                },
            )
            if (status.alarmVolumeZero) {
                PermissionRow(
                    title = stringResource(R.string.alarm_alarm_volume_title),
                    body = stringResource(R.string.alarm_alarm_volume_zero),
                    granted = false,
                    actionLabel = stringResource(R.string.open_settings),
                    onAction = {
                        AlarmPermissions.open(
                            context,
                            AlarmPermissions.soundSettingsIntent(),
                        )
                    },
                )
            }
        }

        if (status.isVivo) {
            PermissionGroup(title = stringResource(R.string.alarm_vivo_section_title)) {
                PermissionRow(
                    title = stringResource(R.string.alarm_vivo_autostart_title),
                    body = stringResource(R.string.alarm_vivo_autostart_body),
                    granted = null,
                    actionLabel = stringResource(R.string.alarm_open_app_info),
                    onAction = {
                        AlarmPermissions.open(
                            context,
                            AlarmPermissions.appDetailsIntent(context),
                        )
                    },
                )
                PermissionRow(
                    title = stringResource(R.string.alarm_vivo_background_title),
                    body = stringResource(R.string.alarm_vivo_background_body),
                    granted = null,
                    actionLabel = stringResource(R.string.alarm_open_app_info),
                    onAction = {
                        AlarmPermissions.open(
                            context,
                            AlarmPermissions.appDetailsIntent(context),
                        )
                    },
                )
                PermissionRow(
                    title = stringResource(R.string.alarm_vivo_full_screen_title),
                    body = stringResource(R.string.alarm_vivo_full_screen_body),
                    granted = status.fullScreenIntentGranted,
                    actionLabel = if (status.fullScreenIntentGranted) {
                        null
                    } else {
                        stringResource(R.string.open_settings)
                    },
                    onAction = if (status.fullScreenIntentGranted) {
                        null
                    } else {
                        {
                            AlarmPermissions.open(
                                context,
                                AlarmPermissions.fullScreenIntentIntent(context),
                            )
                        }
                    },
                )
                PermissionRow(
                    title = stringResource(R.string.alarm_vivo_wake_screen_title),
                    body = stringResource(R.string.alarm_vivo_wake_screen_body),
                    granted = status.vivoWakeScreenOff?.let { !it },
                    actionLabel = stringResource(R.string.alarm_open_app_info),
                    onAction = {
                        AlarmPermissions.open(
                            context,
                            AlarmPermissions.appDetailsIntent(context),
                        )
                    },
                )
                PermissionRow(
                    title = stringResource(R.string.alarm_vivo_lock_screen_title),
                    body = stringResource(R.string.alarm_vivo_lock_screen_body),
                    granted = null,
                    actionLabel = stringResource(R.string.alarm_open_notification_settings),
                    onAction = {
                        AlarmPermissions.open(
                            context,
                            AlarmPermissions.appNotificationSettingsIntent(context),
                        )
                    },
                )
                PermissionRow(
                    title = stringResource(R.string.alarm_vivo_pause_unused_title),
                    body = stringResource(R.string.alarm_vivo_pause_unused_body),
                    granted = null,
                    actionLabel = stringResource(R.string.alarm_open_app_info),
                    onAction = {
                        AlarmPermissions.open(
                            context,
                            AlarmPermissions.appDetailsIntent(context),
                        )
                    },
                )
                PermissionRow(
                    title = stringResource(R.string.alarm_vivo_overlay_title),
                    body = stringResource(R.string.alarm_vivo_overlay_body),
                    granted = null,
                    actionLabel = null,
                    onAction = null,
                )
            }
        }
    }
}

@Composable
private fun PermissionGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionLabel(text = title)
    ClockwiseCard {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    body: String,
    granted: Boolean?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    val colors = Clockwise.colors
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = Clockwise.type.title,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            AlarmStatusMark(granted = granted)
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkMuted,
        )
        if (actionLabel != null && onAction != null) {
            QuietButton(text = actionLabel, onClick = onAction)
        }
    }
}
