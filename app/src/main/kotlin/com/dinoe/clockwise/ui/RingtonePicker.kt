package com.dinoe.clockwise.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dinoe.clockwise.R
import com.dinoe.clockwise.alarm.AlarmSounds
import com.dinoe.clockwise.ui.design.Clockwise

/**
 * One row that opens the platform ringtone picker. [value] is an alarm ringtone value:
 * [AlarmSounds.DEFAULT], [AlarmSounds.SILENT], or a Uri string. The caller supplies the horizontal
 * margin so the row can sit in a settings group or in a dialog column.
 */
@Composable
internal fun RingtoneSettingRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    useSystemDefaultLabel: Boolean = false,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val context = LocalContext.current
    val label = remember(context, value, useSystemDefaultLabel) {
        if (useSystemDefaultLabel) {
            AlarmSounds.defaultName(context, value)
        } else {
            AlarmSounds.name(context, value)
        }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        @Suppress("DEPRECATION")
        val picked = result.data
            ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        // The picker returns null for its "Silent" row and a Uri for any sound.
        onValueChange(picked?.toString() ?: AlarmSounds.SILENT)
    }
    val canReset = value != AlarmSounds.DEFAULT

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable { launcher.launch(ringtonePickerIntent(title, value)) }
            .heightIn(min = 48.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = type.body,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supportingText != null || canReset) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = supportingText.orEmpty(),
                        style = type.bodySmall,
                        color = colors.inkMuted,
                        modifier = Modifier.weight(1f),
                    )
                    if (canReset) {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .clickable(role = Role.Button) {
                                    onValueChange(AlarmSounds.DEFAULT)
                                }
                                .heightIn(min = 32.dp)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.ringtone_reset),
                                style = type.label,
                                color = colors.ink,
                            )
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .widthIn(max = 160.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(colors.surfaceContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = label,
                style = type.monoMeta,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.inkMuted,
            modifier = Modifier
                .padding(start = 10.dp)
                .size(20.dp),
        )
    }
}

private fun ringtonePickerIntent(title: String, value: String): Intent {
    val existing = when (value) {
        AlarmSounds.SILENT -> null
        AlarmSounds.DEFAULT -> AlarmSounds.systemDefaultUri()
        else -> Uri.parse(value)
    }
    return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
    }
}
