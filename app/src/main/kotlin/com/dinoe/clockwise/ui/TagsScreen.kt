package com.dinoe.clockwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dinoe.clockwise.R
import com.dinoe.clockwise.data.Tag
import com.dinoe.clockwise.ui.design.Clockwise
import com.dinoe.clockwise.ui.design.ClockwiseTextField
import com.dinoe.clockwise.ui.design.ClockwiseBottomSheet
import com.dinoe.clockwise.ui.design.ClockwiseScreenHeader
import com.dinoe.clockwise.ui.design.DangerButton
import com.dinoe.clockwise.ui.design.EmptyNotice
import com.dinoe.clockwise.ui.design.InkButton
import com.dinoe.clockwise.ui.design.QuietButton
import com.dinoe.clockwise.ui.design.TimetableRow

/**
 * The tags page: a list of colour marks and names. Each row opens the edit sheet on tap and asks to
 * delete on long press.
 */
@Composable
fun TagsScreen(viewModel: TagsViewModel = viewModel()) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf<Tag?>(null) }
    var tagToDelete by remember { mutableStateOf<Tag?>(null) }

    val colors = Clockwise.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paper),
    ) {
        ClockwiseScreenHeader(
            title = stringResource(R.string.tags_title),
            overline = if (tags.isEmpty()) {
                null
            } else {
                pluralStringResource(R.plurals.tag_ui_count, tags.size, tags.size)
            },
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.add_tag),
                        tint = colors.ink,
                    )
                }
            },
        )
        if (tags.isEmpty()) {
            EmptyNotice(
                title = stringResource(R.string.tag_empty),
                body = stringResource(R.string.tag_ui_empty_body),
                modifier = Modifier.padding(top = 24.dp),
                actionLabel = stringResource(R.string.add_tag),
                onAction = { showAddDialog = true },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(tags, key = { _, tag -> tag.id }) { _, tag ->
                    TagRow(
                        tag = tag,
                        onEdit = { tagToEdit = tag },
                        onDeleteRequested = { tagToDelete = tag },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        TagDialog(
            title = stringResource(R.string.add_tag),
            initialName = "",
            initialColor = 0,
            onDismiss = { showAddDialog = false },
            onSave = { name, color, onResult ->
                viewModel.addTag(name, color) { success ->
                    onResult(success)
                    if (success) showAddDialog = false
                }
            },
        )
    }

    tagToEdit?.let { tag ->
        TagDialog(
            title = stringResource(R.string.edit_tag),
            initialName = tag.name,
            initialColor = tag.color,
            onDismiss = { tagToEdit = null },
            onSave = { name, color, onResult ->
                viewModel.renameTag(tag, name, color) { success ->
                    onResult(success)
                    if (success) tagToEdit = null
                }
            },
        )
    }

    tagToDelete?.let { tag ->
        TagDeleteDialog(
            tagName = tag.name,
            onDismiss = { tagToDelete = null },
            onDelete = {
                viewModel.deleteTag(tag)
                tagToDelete = null
            },
        )
    }
}

@Composable
private fun TagRow(
    tag: Tag,
    onEdit: () -> Unit,
    onDeleteRequested: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    TimetableRow(
        gutterWidth = 28.dp,
        onClick = onEdit,
        onLongClick = onDeleteRequested,
        gutter = {
            Box(
                modifier = Modifier.size(width = 28.dp, height = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (tag.color == 0) colors.tagFallback else Color(tag.color)),
                )
            }
        },
        trailing = {
            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.edit_tag),
                    tint = colors.inkMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    ) {
        Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = tag.name,
                style = type.title,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TagDialog(
    title: String,
    initialName: String,
    initialColor: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int, (Boolean) -> Unit) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var color by rememberSaveable(initialColor) { mutableIntStateOf(initialColor) }
    var showDuplicateError by remember { mutableStateOf(false) }

    val colors = Clockwise.colors
    val type = Clockwise.type

    ClockwiseBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = title, style = type.title, color = colors.ink)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ClockwiseTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        showDuplicateError = false
                    },
                    label = stringResource(R.string.tag_name_label),
                    singleLine = true,
                    isError = showDuplicateError,
                )
                if (showDuplicateError) {
                    Text(
                        text = stringResource(R.string.tag_duplicate_name),
                        color = colors.signal,
                        style = type.bodySmall,
                    )
                }
            }
            TagColorPicker(
                selectedColor = color,
                onColorSelected = { color = it },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuietButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                InkButton(
                    text = stringResource(R.string.save),
                    onClick = {
                        showDuplicateError = false
                        onSave(name.trim(), color) { success ->
                            if (!success) showDuplicateError = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank(),
                )
            }
        }
    }
}

@Composable
private fun TagDeleteDialog(
    tagName: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    ClockwiseBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.delete_tag),
                style = type.title,
                color = colors.ink,
            )
            Text(
                text = stringResource(R.string.delete_tag_confirmation, tagName),
                style = type.body,
                color = colors.inkMuted,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuietButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                DangerButton(
                    text = stringResource(R.string.delete),
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
