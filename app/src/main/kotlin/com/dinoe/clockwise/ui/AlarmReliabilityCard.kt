package com.dinoe.clockwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dinoe.clockwise.R
import com.dinoe.clockwise.alarm.AlarmPermissionStatus
import com.dinoe.clockwise.ui.design.Clockwise
import com.dinoe.clockwise.ui.design.ClockwiseCard
import com.dinoe.clockwise.ui.design.QuietButton

/**
 * A card that states whether alarms can be trusted to ring, and the control that opens the full
 * setting list. Missing permissions speak in signal; a granted set of permissions stays ink.
 */
@Composable
fun AlarmReliabilityCard(
    status: AlarmPermissionStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val hasIssue = status.hasAnyIssue

    ClockwiseCard(
        modifier = modifier.padding(horizontal = 16.dp),
        onClick = onClick,
        color = colors.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (hasIssue) colors.signal else colors.ink, CircleShape),
                )
                Text(
                    text = stringResource(R.string.alarm_permission_title),
                    style = type.title,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                text = stringResource(
                    if (hasIssue) {
                        R.string.alarm_permission_summary_missing
                    } else {
                        R.string.alarm_permission_summary_ok
                    },
                ),
                style = type.bodySmall,
                color = if (hasIssue) colors.signal else colors.inkMuted,
            )
            QuietButton(
                text = stringResource(R.string.alarm_permission_check),
                onClick = onClick,
            )
        }
    }
}

/** How one permission reads in a row: a circular mark and its state beside it. */
@Composable
internal fun AlarmStatusMark(
    granted: Boolean?,
    modifier: Modifier = Modifier,
) {
    val colors = Clockwise.colors
    val missing = granted == false
    val color = when {
        missing -> colors.signal
        granted == true -> colors.ink
        else -> colors.inkMuted
    }
    val labelRes = when {
        missing -> R.string.alarm_permission_missing
        granted == true -> R.string.alarm_permission_granted
        else -> R.string.alarm_permission_manual
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            text = stringResource(labelRes),
            style = Clockwise.type.label,
            color = color,
            modifier = Modifier.padding(start = 6.dp),
            maxLines = 1,
        )
    }
}
