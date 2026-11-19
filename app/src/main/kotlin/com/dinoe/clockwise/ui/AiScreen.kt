package com.dinoe.clockwise.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dinoe.clockwise.R
import com.dinoe.clockwise.alarm.AlarmSounds
import com.dinoe.clockwise.ai.AiChatItem
import com.dinoe.clockwise.ai.AiTokenBudget
import com.dinoe.clockwise.ai.AiUsage
import com.dinoe.clockwise.ai.AlarmDeleteDraft
import com.dinoe.clockwise.ai.AlarmDraft
import com.dinoe.clockwise.ai.AlarmStackDraft
import com.dinoe.clockwise.ai.AlarmUpdateDraft
import com.dinoe.clockwise.ai.EventDeleteDraft
import com.dinoe.clockwise.ai.EventDraft
import com.dinoe.clockwise.ai.EventUpdateDraft
import com.dinoe.clockwise.ai.ProposalState
import com.dinoe.clockwise.data.AiChatSessionSummary
import com.dinoe.clockwise.data.Event
import com.dinoe.clockwise.data.EventScope
import com.dinoe.clockwise.data.Tag
import com.dinoe.clockwise.ui.design.Clockwise
import com.dinoe.clockwise.ui.design.ClockwiseBottomSheet
import com.dinoe.clockwise.ui.design.ClockwiseCard
import com.dinoe.clockwise.ui.design.ClockwiseScreenHeader
import com.dinoe.clockwise.ui.design.DangerButton
import com.dinoe.clockwise.ui.design.InkButton
import com.dinoe.clockwise.ui.design.QuietButton
import com.dinoe.clockwise.ui.design.Rule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** The side margin every block on this screen keeps. */
private val AiGutter = 16.dp

/** Separator for the parts of one docket line: a printed slash, not a middot. */
private const val PART_SEPARATOR = " / "

/** Conversation bubbles: fully rounded, the tail corner tightened toward the speaker. */
private val UserBubbleShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomEnd = 6.dp,
    bottomStart = 20.dp,
)

private val AssistantBubbleShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomEnd = 20.dp,
    bottomStart = 6.dp,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(
    onOpenSettings: () -> Unit,
    viewModel: AiChatViewModel = viewModel(),
) {
    val colors = Clockwise.colors
    val listState = rememberLazyListState()
    var showUsage by remember { mutableStateOf(false) }
    var showNewChat by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val timePickerStyle by viewModel.timePickerStyle.collectAsStateWithLifecycle()
    val active = viewModel.config?.active()
    val subtitle = if (active?.hasKey == true) {
        stringResource(
            R.string.ai_subtitle_format,
            active.preset.displayName,
            active.modelId,
        )
    } else {
        stringResource(R.string.ai_subtitle_unconfigured)
    }
    val messages = viewModel.items
    val pendingPreview = rememberChatThumbnail(
        cache = viewModel.thumbnails,
        base64 = viewModel.pendingImage?.previewBase64,
    )
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::attachImage) }

    LaunchedEffect(messages.size, viewModel.isBusy) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(showSessions) {
        if (showSessions) viewModel.refreshSessions()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = colors.paper,
        // The header owns the status bar inset and the tab rail owns the navigation bar inset.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            ClockwiseScreenHeader(
                title = stringResource(R.string.ai_title),
                overline = subtitle,
                actions = {
                    AiHeaderActions(
                        newChatEnabled = !viewModel.isRestoring && !viewModel.hasPendingProposal,
                        onSessions = { showSessions = true },
                        onNewChat = {
                            if (messages.isEmpty()) {
                                viewModel.clearConversation()
                            } else {
                                showNewChat = true
                            }
                        },
                        onUsage = { showUsage = true },
                        onSettings = onOpenSettings,
                    )
                },
            )
        },
        bottomBar = {
            AiInputBar(
                resetKey = viewModel.inputResetKey,
                isBusy = viewModel.isBusy,
                pendingProposal = viewModel.hasPendingProposal,
                enabled = active?.hasKey == true &&
                    !viewModel.hasPendingProposal &&
                    !viewModel.isBusy &&
                    !viewModel.isRestoring,
                hasPendingImage = viewModel.pendingImage != null,
                imagePreview = pendingPreview.bitmapOrNull,
                imageLoading = viewModel.isImageLoading || pendingPreview.isLoading,
                onAttach = {
                    if (active?.modelSupportsImages == false) {
                        viewModel.showImageUnsupported()
                    } else {
                        imagePicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    }
                },
                onRemoveImage = viewModel::removeImage,
                onSend = viewModel::send,
                onStop = viewModel::stop,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (viewModel.needsSetup && messages.isNotEmpty()) {
                AiSetupStrip(
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.padding(horizontal = AiGutter, vertical = 8.dp),
                )
            }
            if (viewModel.config == null || viewModel.isRestoring) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.ink, trackColor = colors.rule)
                }
            } else if (messages.isEmpty() && !viewModel.isBusy) {
                AiEmptyState(
                    needsSetup = viewModel.needsSetup,
                    onOpenSettings = onOpenSettings,
                    onExample = { text -> viewModel.send(text) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = AiGutter,
                        end = AiGutter,
                        top = 16.dp,
                        bottom = 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(messages, key = { it.id }) { item ->
                        when (item) {
                            is AiChatItem.User -> UserMessage(
                                text = item.text,
                                image = item.image,
                                thumbnails = viewModel.thumbnails,
                            )
                            is AiChatItem.Assistant -> AssistantMessage(item.text)
                            is AiChatItem.Notice -> NoticeMessage(item.text)
                            is AiChatItem.Error -> ErrorMessage(
                                text = item.text,
                                retryable = item.retryable,
                                onRetry = viewModel::retry,
                            )

                            is AiChatItem.Proposal -> ProposalCard(
                                item = item,
                                busy = viewModel.isBusy && item.state == ProposalState.PENDING,
                                allTags = allTags,
                                timePickerStyle = timePickerStyle,
                                onConfirm = viewModel::confirmProposal,
                                onCancel = viewModel::cancelProposal,
                                onUpdateAlarm = viewModel::updateAlarmDraft,
                                onUpdateAlarmStack = viewModel::updateAlarmStackDraft,
                                onUpdateEvent = viewModel::updateEventDraft,
                            )
                        }
                    }
                    if (viewModel.isBusy && !viewModel.hasPendingProposal) {
                        item(key = -1L) {
                            BusyRow(status = viewModel.statusText)
                        }
                    }
                }
            }
        }
    }

    if (showSessions) {
        SessionsDialog(
            sessions = sessions,
            activeSessionId = viewModel.activeSessionId,
            busy = viewModel.isBusy || viewModel.isRestoring || viewModel.hasPendingProposal,
            onSwitch = viewModel::switchSession,
            onRename = viewModel::renameSession,
            onDelete = viewModel::deleteSession,
            onNewChat = viewModel::clearConversation,
            onDismiss = { showSessions = false },
        )
    }

    if (showUsage) {
        UsageDialog(
            usage = viewModel.usage,
            requestCount = viewModel.requestCount,
            onDismiss = { showUsage = false },
        )
    }

    if (showNewChat) {
        AiConfirmDialog(
            title = stringResource(R.string.ai_new_chat_confirm_title),
            body = stringResource(R.string.ai_new_chat_confirm_text),
            confirmLabel = stringResource(R.string.ok),
            onConfirm = {
                viewModel.clearConversation()
                showNewChat = false
            },
            onDismiss = { showNewChat = false },
        )
    }
}

/**
 * Sessions stays on the header because it is navigation; the commands (new chat, usage, settings)
 * sit behind the overflow so the title keeps the line.
 */
@Composable
private fun AiHeaderActions(
    newChatEnabled: Boolean,
    onSessions: () -> Unit,
    onNewChat: () -> Unit,
    onUsage: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = Clockwise.colors
    var menuOpen by remember { mutableStateOf(false) }
    IconButton(onClick = onSessions, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = stringResource(R.string.ai_sessions_title),
            tint = colors.ink,
        )
    }
    Box {
        IconButton(
            onClick = { menuOpen = true },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.ai_ui_more_actions),
                tint = colors.ink,
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = MaterialTheme.shapes.small,
            containerColor = colors.sheet,
        ) {
            AiMenuItem(
                text = stringResource(R.string.ai_new_chat),
                enabled = newChatEnabled,
                onClick = {
                    menuOpen = false
                    onNewChat()
                },
            )
            AiMenuItem(
                text = stringResource(R.string.ai_usage_title),
                onClick = {
                    menuOpen = false
                    onUsage()
                },
            )
            AiMenuItem(
                text = stringResource(R.string.ai_chat_settings),
                onClick = {
                    menuOpen = false
                    onSettings()
                },
            )
        }
    }
}

@Composable
private fun AiMenuItem(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = type.body,
                color = if (enabled) colors.ink else colors.inkFaint,
            )
        },
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    )
}

/** The empty state is the hero: a large opening line, then the prompts as full-width cards. */
@Composable
private fun AiEmptyState(
    needsSetup: Boolean,
    onOpenSettings: () -> Unit,
    onExample: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val examples = listOf(
        stringResource(R.string.ai_example_1),
        stringResource(R.string.ai_example_2),
        stringResource(R.string.ai_example_3),
        stringResource(R.string.ai_example_4),
        stringResource(R.string.ai_example_5),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier.padding(
                start = AiGutter,
                end = AiGutter,
                top = 28.dp,
                bottom = 24.dp,
            ),
        ) {
            Text(
                text = stringResource(R.string.ai_empty_title),
                style = type.display.copy(
                    fontSize = 36.sp,
                    lineHeight = 42.sp,
                    letterSpacing = (-0.5).sp,
                ),
                color = colors.ink,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.ai_empty_body),
                style = type.body,
                color = colors.inkMuted,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ai_image_hint),
                style = type.bodySmall,
                color = colors.inkMuted,
            )
        }

        if (needsSetup) {
            AiSetupStrip(
                onOpenSettings = onOpenSettings,
                modifier = Modifier.padding(horizontal = AiGutter),
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text(
            text = stringResource(R.string.ai_ui_examples),
            style = type.label,
            color = colors.inkMuted,
            modifier = Modifier.padding(
                start = AiGutter,
                end = AiGutter,
                bottom = 10.dp,
            ),
        )
        Column(
            modifier = Modifier.padding(horizontal = AiGutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            examples.forEach { example ->
                ExampleCard(
                    text = example,
                    enabled = !needsSetup,
                    onClick = { onExample(example) },
                )
            }
        }

        Text(
            text = stringResource(R.string.ai_disclaimer),
            style = type.bodySmall,
            color = colors.inkMuted,
            modifier = Modifier.padding(
                start = AiGutter,
                end = AiGutter,
                top = 20.dp,
                bottom = 24.dp,
            ),
        )
    }
}

/** One example prompt: a full-width tappable card with a chevron. */
@Composable
private fun ExampleCard(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    ClockwiseCard(onClick = if (enabled) onClick else null) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = type.body,
                color = if (enabled) colors.ink else colors.inkMuted,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (enabled) colors.inkMuted else colors.inkFaint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Missing setup is a quiet card on the page, with the verb that fixes it. */
@Composable
private fun AiSetupStrip(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    ClockwiseCard(modifier = modifier, color = colors.surfaceContainer) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.ai_setup_title),
                style = type.title,
                color = colors.ink,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ai_setup_body),
                style = type.bodySmall,
                color = colors.inkMuted,
            )
            Spacer(modifier = Modifier.height(12.dp))
            QuietButton(
                text = stringResource(R.string.ai_setup_action),
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun UserMessage(text: String, image: String?, thumbnails: ChatThumbnailCache) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val thumbnail = rememberChatThumbnail(cache = thumbnails, base64 = image)
    val bitmap = thumbnail.bitmapOrNull
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Spacer(modifier = Modifier.weight(0.22f))
        Surface(
            modifier = Modifier.weight(0.78f, fill = false),
            color = colors.ink,
            contentColor = colors.paper,
            shape = UserBubbleShape,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (bitmap != null) {
                    val ratio = bitmap.width.toFloat() / bitmap.height
                    val imageWidth = if (ratio >= 1f) 280.dp else (280f * ratio).dp
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.ai_image_attached),
                        modifier = Modifier
                            .width(imageWidth)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Fit,
                    )
                } else if (thumbnail.isLoading) {
                    ThumbnailPlaceholder()
                }
                if (text.isNotBlank()) {
                    if (bitmap != null || thumbnail.isLoading) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Text(
                        text = text,
                        style = type.body,
                        color = colors.paper,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbnailPlaceholder() {
    val colors = Clockwise.colors
    Box(
        modifier = Modifier
            .width(168.dp)
            .height(120.dp)
            .clip(MaterialTheme.shapes.small)
            .background(colors.wash),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = colors.inkMuted,
        )
    }
}

/** The reply is a quiet bubble on the left; the text stays selectable for copying. */
@Composable
private fun AssistantMessage(text: String) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.weight(0.78f, fill = false),
            color = colors.surfaceLow,
            contentColor = colors.ink,
            shape = AssistantBubbleShape,
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = type.body,
                    color = colors.ink,
                )
            }
        }
        Spacer(modifier = Modifier.weight(0.22f))
    }
}

@Composable
private fun NoticeMessage(text: String) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = type.bodySmall,
        color = colors.inkMuted,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ErrorMessage(
    text: String,
    retryable: Boolean,
    onRetry: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.washSignal,
        contentColor = colors.ink,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = text,
                style = type.body,
                color = colors.ink,
            )
            if (retryable) {
                Spacer(modifier = Modifier.height(10.dp))
                QuietButton(
                    text = stringResource(R.string.ai_retry),
                    onClick = onRetry,
                )
            }
        }
    }
}

@Composable
private fun BusyRow(status: String?) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = colors.ink,
            trackColor = colors.rule,
        )
        Text(
            text = status ?: stringResource(R.string.ai_status_thinking),
            style = type.bodySmall,
            color = colors.inkMuted,
        )
    }
}

/**
 * A proposal is a card: title, state chip, one summary line, then groups of rows and the action
 * that files it.
 */
@Composable
private fun ProposalCard(
    item: AiChatItem.Proposal,
    busy: Boolean,
    allTags: List<Tag>,
    timePickerStyle: TimePickerStyle,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onUpdateAlarm: (Int, AlarmDraft) -> Unit,
    onUpdateAlarmStack: (Int, AlarmStackDraft) -> Unit,
    onUpdateEvent: (Int, EventDraft) -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    var editAlarmIndex by remember(item.id) { mutableStateOf<Int?>(null) }
    var editAlarmStackIndex by remember(item.id) { mutableStateOf<Int?>(null) }
    var editEventIndex by remember(item.id) { mutableStateOf<Int?>(null) }
    val editable = item.state == ProposalState.PENDING && !busy
    val createCount = item.alarms.size + item.alarmStacks.size + item.events.size
    val updateCount = item.alarmUpdates.size + item.eventUpdates.size
    val deleteCount = item.alarmDeletes.size + item.eventDeletes.size
    val summary = listOfNotNull(
        if (createCount > 0) {
            stringResource(R.string.ai_proposal_create_summary, createCount)
        } else {
            null
        },
        if (updateCount > 0) {
            stringResource(R.string.ai_proposal_update_summary, updateCount)
        } else {
            null
        },
        if (deleteCount > 0) {
            stringResource(R.string.ai_proposal_delete_summary, deleteCount)
        } else {
            null
        },
    ).joinToString(PART_SEPARATOR)

    ClockwiseCard(color = colors.surfaceContainer) {
        Column {
            Row(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 6.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.ai_proposal_title),
                    style = type.title,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                ProposalStateChip(state = item.state)
            }
            Text(
                text = summary.ifBlank { stringResource(R.string.ai_proposal_empty) },
                style = type.bodySmall,
                color = colors.inkMuted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            )

            if (item.alarms.isNotEmpty()) {
                DocketSection(
                    title = stringResource(R.string.ai_proposal_alarms, item.alarms.size),
                ) {
                    item.alarms.forEachIndexed { index, alarm ->
                        if (index > 0) Rule(modifier = Modifier.padding(horizontal = 16.dp))
                        AlarmDraftRow(
                            alarm = alarm,
                            onEdit = if (editable) ({ editAlarmIndex = index }) else null,
                        )
                    }
                }
            }

            if (item.alarmStacks.isNotEmpty()) {
                DocketSection(
                    title = stringResource(
                        R.string.ai_proposal_alarm_stacks,
                        item.alarmStacks.size,
                    ),
                ) {
                    item.alarmStacks.forEachIndexed { index, stack ->
                        if (index > 0) Rule(modifier = Modifier.padding(horizontal = 16.dp))
                        AlarmStackDraftRow(
                            stack = stack,
                            onEdit = if (editable) ({ editAlarmStackIndex = index }) else null,
                        )
                    }
                }
            }

            if (item.events.isNotEmpty()) {
                DocketSection(
                    title = stringResource(R.string.ai_proposal_events, item.events.size),
                ) {
                    item.events.forEachIndexed { index, event ->
                        if (index > 0) Rule(modifier = Modifier.padding(horizontal = 16.dp))
                        EventDraftRow(
                            event = event,
                            onEdit = if (editable) ({ editEventIndex = index }) else null,
                        )
                    }
                }
            }

            if (item.alarmUpdates.isNotEmpty()) {
                DocketSection(
                    title = stringResource(
                        R.string.ai_proposal_update_alarms,
                        item.alarmUpdates.size,
                    ),
                ) {
                    item.alarmUpdates.forEachIndexed { index, update ->
                        if (index > 0) Rule(modifier = Modifier.padding(horizontal = 16.dp))
                        AlarmUpdateRow(update)
                    }
                }
            }

            if (item.eventUpdates.isNotEmpty()) {
                DocketSection(
                    title = stringResource(
                        R.string.ai_proposal_update_events,
                        item.eventUpdates.size,
                    ),
                ) {
                    item.eventUpdates.forEachIndexed { index, update ->
                        if (index > 0) Rule(modifier = Modifier.padding(horizontal = 16.dp))
                        EventUpdateRow(update)
                    }
                }
            }

            if (item.alarmDeletes.isNotEmpty()) {
                DocketSection(
                    title = stringResource(
                        R.string.ai_proposal_delete_alarms,
                        item.alarmDeletes.size,
                    ),
                    destructive = true,
                ) {
                    item.alarmDeletes.forEachIndexed { index, delete ->
                        if (index > 0) Rule(modifier = Modifier.padding(horizontal = 16.dp))
                        AlarmDeleteRow(delete)
                    }
                }
            }

            if (item.eventDeletes.isNotEmpty()) {
                DocketSection(
                    title = stringResource(
                        R.string.ai_proposal_delete_events,
                        item.eventDeletes.size,
                    ),
                    destructive = true,
                ) {
                    item.eventDeletes.forEachIndexed { index, delete ->
                        if (index > 0) Rule(modifier = Modifier.padding(horizontal = 16.dp))
                        EventDeleteRow(delete)
                    }
                }
            }

            if (item.state == ProposalState.PENDING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuietButton(
                        text = stringResource(R.string.ai_proposal_cancel),
                        onClick = onCancel,
                        enabled = !busy,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    InkButton(
                        text = if (busy) {
                            stringResource(R.string.ai_status_creating)
                        } else if (updateCount > 0 || deleteCount > 0) {
                            stringResource(R.string.ai_proposal_apply)
                        } else {
                            stringResource(R.string.ai_proposal_confirm)
                        },
                        onClick = onConfirm,
                        enabled = !busy,
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    editAlarmIndex?.let { index ->
        item.alarms.getOrNull(index)?.let { draft ->
            AlarmDraftEditDialog(
                draft = draft,
                allTags = allTags,
                timePickerStyle = timePickerStyle,
                onDismiss = { editAlarmIndex = null },
                onSave = { updated ->
                    editAlarmIndex = null
                    onUpdateAlarm(index, updated)
                    onConfirm()
                },
            )
        }
    }
    editAlarmStackIndex?.let { index ->
        item.alarmStacks.getOrNull(index)?.let { draft ->
            AlarmStackDraftEditDialog(
                draft = draft,
                allTags = allTags,
                timePickerStyle = timePickerStyle,
                onDismiss = { editAlarmStackIndex = null },
                onSave = { updated ->
                    editAlarmStackIndex = null
                    onUpdateAlarmStack(index, updated)
                    onConfirm()
                },
            )
        }
    }
    editEventIndex?.let { index ->
        item.events.getOrNull(index)?.let { draft ->
            EventDraftEditDialog(
                draft = draft,
                allTags = allTags,
                timePickerStyle = timePickerStyle,
                onDismiss = { editEventIndex = null },
                onSave = { updated ->
                    editEventIndex = null
                    onUpdateEvent(index, updated)
                    onConfirm()
                },
            )
        }
    }
}

/** One group of the docket: a small label, then its rows. */
@Composable
private fun DocketSection(
    title: String,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = title,
        style = type.label,
        color = if (destructive) colors.signal else colors.inkMuted,
        modifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            bottom = 2.dp,
        ),
    )
    Column(content = content)
}

/** The proposal's state, a pill in Archivo — wash for open and done, the error wash for cancelled. */
@Composable
private fun ProposalStateChip(state: ProposalState) {
    val colors = Clockwise.colors
    val text = when (state) {
        ProposalState.APPLIED -> stringResource(R.string.ai_proposal_applied)
        ProposalState.CANCELLED -> stringResource(R.string.ai_proposal_cancelled)
        ProposalState.PENDING -> stringResource(R.string.ai_ui_proposal_pending)
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (state == ProposalState.CANCELLED) colors.washSignal else colors.wash,
        contentColor = colors.ink,
    ) {
        Text(
            text = text,
            style = Clockwise.type.label,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/** The skeleton every docket row shares: content on the left, one control on the right. */
@Composable
private fun DocketRowLayout(
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
        trailing?.invoke()
    }
}

@Composable
private fun DocketTitle(text: String, color: Color) {
    Text(text = text, style = Clockwise.type.body, color = color)
}

@Composable
private fun DocketDetail(text: String, color: Color, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text = text,
        style = Clockwise.type.bodySmall,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AlarmDraftRow(alarm: AlarmDraft, onEdit: (() -> Unit)? = null) {
    val colors = Clockwise.colors
    val context = LocalContext.current
    val time = formatTime(alarm.time)
    val whenText = if (alarm.date == null) {
        stringResource(R.string.ai_format_next_time, time)
    } else {
        stringResource(R.string.ai_format_dated_time, formatDate(alarm.date), time)
    }
    val name = alarm.name.ifBlank {
        stringResource(R.string.ai_proposal_alarm_name_fallback)
    }
    val details = listOfNotNull(
        if (!alarm.enabled) stringResource(R.string.ai_proposal_disabled) else null,
        if (alarm.oneTime) stringResource(R.string.alarm_one_time_badge) else null,
        if (alarm.ringtone != AlarmSounds.DEFAULT) {
            stringResource(
                R.string.ai_proposal_ringtone,
                AlarmSounds.name(context, alarm.ringtone),
            )
        } else {
            null
        },
    )
    DocketRowLayout(trailing = { DraftEditButton(onEdit) }) {
        DocketTitle(text = "$whenText  $name", color = colors.ink)
        if (details.isNotEmpty()) {
            DocketDetail(details.joinToString(PART_SEPARATOR), colors.inkMuted)
        }
        if (alarm.note.isNotBlank()) {
            DocketDetail(alarm.note, colors.inkMuted, maxLines = 2)
        }
        if (alarm.tags.isNotEmpty()) {
            DocketDetail(
                text = stringResource(
                    R.string.ai_proposal_tags,
                    alarm.tags.joinToString("、") { it.name },
                ),
                color = colors.inkMuted,
            )
        }
    }
}

@Composable
private fun AlarmStackDraftRow(stack: AlarmStackDraft, onEdit: (() -> Unit)? = null) {
    val colors = Clockwise.colors
    val context = LocalContext.current
    val clockFormatter = rememberClockFormatter()
    val details = listOfNotNull(
        stringResource(R.string.alarm_stack_count, stack.times.size),
        if (!stack.enabled) stringResource(R.string.ai_proposal_disabled) else null,
        if (stack.oneTime) stringResource(R.string.alarm_one_time_badge) else null,
        if (stack.ringtone != AlarmSounds.DEFAULT) {
            stringResource(
                R.string.ai_proposal_ringtone,
                AlarmSounds.name(context, stack.ringtone),
            )
        } else {
            null
        },
    )
    DocketRowLayout(trailing = { DraftEditButton(onEdit) }) {
        // The times are the row's number, so they are the one thing set in mono.
        Text(
            text = stack.times.joinToString(PART_SEPARATOR) { clockFormatter.format(it) },
            style = Clockwise.type.monoSmall,
            color = colors.ink,
        )
        DocketTitle(
            text = stack.name.ifBlank {
                stringResource(R.string.ai_proposal_alarm_name_fallback)
            },
            color = colors.ink,
        )
        DocketDetail(details.joinToString(PART_SEPARATOR), colors.inkMuted)
        if (stack.note.isNotBlank()) {
            DocketDetail(stack.note, colors.inkMuted, maxLines = 2)
        }
        if (stack.tags.isNotEmpty()) {
            DocketDetail(
                text = stringResource(
                    R.string.ai_proposal_tags,
                    stack.tags.joinToString("、") { it.name },
                ),
                color = colors.inkMuted,
            )
        }
    }
}

@Composable
private fun EventDraftRow(event: EventDraft, onEdit: (() -> Unit)? = null) {
    val colors = Clockwise.colors
    val locale = rememberLocale()
    val dateText = formatDate(event.date)
    val timeRange = formatEventTimeRange(
        allDay = event.allDay,
        start = event.start,
        end = event.end,
        endDayOffset = event.endDayOffset,
    )
    val weekdayText = event.repeatDays.sorted().joinToString(", ") {
        weekdayLabel(it, locale)
    }
    val repeatText = when (event.repeatType) {
        Event.REPEAT_DAILY -> stringResource(R.string.ai_proposal_repeat_daily)
        Event.REPEAT_WEEKLY -> stringResource(R.string.ai_proposal_repeat_weekly, weekdayText)
        else -> null
    }
    val untilDate = event.repeatUntil?.let { formatDate(it) }
    val untilText = untilDate?.let { stringResource(R.string.ai_proposal_until, it) }
    val occurrencesText = if (event.occurrenceCount > 1) {
        stringResource(R.string.ai_proposal_occurrences, event.occurrenceCount)
    } else {
        null
    }
    val alarmText = if (event.withAlarm) {
        stringResource(R.string.ai_proposal_alarm_at_start)
    } else {
        null
    }
    val reminderText = if (event.reminderMinutes > 0) {
        stringResource(R.string.ai_proposal_reminder_before, event.reminderMinutes)
    } else {
        null
    }
    val details = listOfNotNull(repeatText, untilText, occurrencesText, alarmText, reminderText)

    DocketRowLayout(trailing = { DraftEditButton(onEdit) }) {
        DocketTitle(text = event.title, color = colors.ink)
        DocketDetail("$dateText $timeRange", colors.inkMuted)
        if (details.isNotEmpty()) {
            DocketDetail(details.joinToString(PART_SEPARATOR), colors.inkMuted)
        }
        if (event.note.isNotBlank()) {
            DocketDetail(event.note, colors.inkMuted, maxLines = 2)
        }
        if (event.tags.isNotEmpty()) {
            DocketDetail(
                text = stringResource(
                    R.string.ai_proposal_tags,
                    event.tags.joinToString("、") { it.name },
                ),
                color = colors.inkMuted,
            )
        }
    }
}

@Composable
private fun DraftEditButton(onEdit: (() -> Unit)?) {
    if (onEdit == null) return
    val colors = Clockwise.colors
    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = stringResource(R.string.ai_proposal_edit),
            modifier = Modifier.size(18.dp),
            tint = colors.inkMuted,
        )
    }
}

@Composable
private fun AlarmUpdateRow(update: AlarmUpdateDraft) {
    val colors = Clockwise.colors
    val context = LocalContext.current
    val previousWhen = alarmWhenText(update.previousTime, update.previousFireDate)
    val nextWhen = alarmWhenText(update.time, update.fireDate)
    val previousName = update.previousName.ifBlank {
        stringResource(R.string.ai_proposal_alarm_name_fallback)
    }
    val nextName = update.name.ifBlank {
        stringResource(R.string.ai_proposal_alarm_name_fallback)
    }
    val notRinging = update.enabled &&
        update.fireDate?.atTime(update.time)?.isBefore(LocalDateTime.now()) == true
    val timeChanged = update.previousTime != update.time ||
        update.previousFireDate != update.fireDate
    DocketRowLayout {
        DocketTitle(
            text = if (timeChanged) {
                stringResource(R.string.ai_proposal_alarm_change, previousWhen, nextWhen)
            } else {
                nextWhen
            },
            color = colors.ink,
        )
        if (previousName != nextName) {
            DocketDetail(
                text = stringResource(
                    R.string.ai_proposal_alarm_change,
                    previousName,
                    nextName,
                ),
                color = colors.inkMuted,
            )
        } else {
            DocketDetail(nextName, colors.inkMuted)
        }
        if (update.enabled != update.previousEnabled) {
            DocketDetail(
                text = stringResource(
                    if (update.enabled) R.string.ai_proposal_enabled
                    else R.string.ai_proposal_disabled,
                ),
                color = colors.inkMuted,
            )
        }
        if (update.oneTime != update.previousOneTime) {
            DocketDetail(
                text = stringResource(
                    if (update.oneTime) R.string.ai_proposal_one_time_added
                    else R.string.ai_proposal_one_time_removed,
                ),
                color = colors.inkMuted,
            )
        }
        if (update.ringtone != update.previousRingtone) {
            DocketDetail(
                text = stringResource(
                    R.string.ai_proposal_ringtone_change,
                    AlarmSounds.name(context, update.previousRingtone),
                    AlarmSounds.name(context, update.ringtone),
                ),
                color = colors.inkMuted,
            )
        }
        if (notRinging) {
            DocketDetail(
                text = stringResource(R.string.ai_proposal_will_not_ring),
                color = colors.signal,
            )
        }
        if (update.tags.isNotEmpty()) {
            DocketDetail(
                text = stringResource(
                    R.string.ai_proposal_tags,
                    update.tags.joinToString("、") { it.name },
                ),
                color = colors.inkMuted,
            )
        }
    }
}

@Composable
private fun AlarmDeleteRow(delete: AlarmDeleteDraft) {
    val colors = Clockwise.colors
    DocketRowLayout {
        DocketTitle(
            text = "${alarmWhenText(delete.time, delete.fireDate)}  " +
                delete.name.ifBlank {
                    stringResource(R.string.ai_proposal_alarm_name_fallback)
                },
            color = colors.signal,
        )
        if (delete.isAuto) {
            DocketDetail(
                text = stringResource(R.string.ai_proposal_auto_alarm_delete_warning),
                color = colors.signal,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun EventUpdateRow(update: EventUpdateDraft) {
    val colors = Clockwise.colors
    val before = "${formatDate(update.previousDate)} " + formatEventTimeRange(
        allDay = update.previousAllDay,
        start = update.previousStart,
        end = update.previousEnd,
        endDayOffset = update.previousEndDayOffset,
    )
    val after = "${formatDate(update.date)} " + formatEventTimeRange(
        allDay = update.allDay,
        start = update.start,
        end = update.end,
        endDayOffset = update.endDayOffset,
    )
    val timeChanged = update.previousDate != update.date ||
        update.previousStart != update.start ||
        update.previousEnd != update.end ||
        update.previousEndDayOffset != update.endDayOffset ||
        update.previousAllDay != update.allDay
    val reminderChange = update.reminderMinutes
        ?.takeIf { it != update.previousReminderMinutes }
    DocketRowLayout {
        DocketTitle(
            text = if (update.title != update.previousTitle) {
                stringResource(
                    R.string.ai_proposal_alarm_change,
                    update.previousTitle,
                    update.title,
                )
            } else {
                update.title
            },
            color = colors.ink,
        )
        DocketDetail(
            text = if (timeChanged) {
                stringResource(R.string.ai_proposal_alarm_change, before, after)
            } else {
                after
            },
            color = colors.inkMuted,
        )
        DocketDetail(
            text = stringResource(
                R.string.ai_proposal_scope,
                scopeLabel(update.scope),
            ) + PART_SEPARATOR + stringResource(
                R.string.ai_proposal_affected_events,
                update.affectedCount,
            ),
            color = colors.inkMuted,
        )
        if (update.withAlarm) {
            DocketDetail(
                text = stringResource(R.string.ai_proposal_alarm_at_start),
                color = colors.inkMuted,
            )
        } else if (update.previousWithAlarm) {
            DocketDetail(
                text = stringResource(R.string.ai_proposal_event_alarm_removed),
                color = colors.signal,
            )
        }
        if (reminderChange != null) {
            DocketDetail(
                text = stringResource(
                    R.string.ai_proposal_reminder_change,
                    reminderLabel(update.previousReminderMinutes),
                    reminderLabel(reminderChange),
                ),
                color = colors.inkMuted,
            )
        }
        if (update.tags.isNotEmpty()) {
            DocketDetail(
                text = stringResource(
                    R.string.ai_proposal_tags,
                    update.tags.joinToString("、") { it.name },
                ),
                color = colors.inkMuted,
            )
        }
    }
}

@Composable
private fun EventDeleteRow(delete: EventDeleteDraft) {
    val colors = Clockwise.colors
    DocketRowLayout {
        DocketTitle(
            text = "${delete.title}  ${formatDate(delete.date)}",
            color = colors.signal,
        )
        DocketDetail(
            text = stringResource(
                R.string.ai_proposal_scope,
                scopeLabel(delete.scope),
            ) + PART_SEPARATOR + stringResource(
                R.string.ai_proposal_affected_events,
                delete.affectedCount,
            ),
            color = colors.inkMuted,
        )
        if (delete.linkedAlarms > 0) {
            DocketDetail(
                text = if (delete.keepAlarms) {
                    stringResource(R.string.ai_proposal_keep_alarms)
                } else {
                    stringResource(
                        R.string.ai_proposal_linked_alarms_deleted,
                        delete.linkedAlarms,
                    )
                },
                color = if (delete.keepAlarms) colors.signal else colors.inkMuted,
            )
        }
    }
}

@Composable
private fun alarmWhenText(time: LocalTime, fireDate: LocalDate?): String =
    if (fireDate == null) {
        stringResource(R.string.ai_format_next_time, formatTime(time))
    } else {
        stringResource(R.string.ai_format_dated_time, formatDate(fireDate), formatTime(time))
    }

@Composable
private fun scopeLabel(scope: EventScope): String = stringResource(
    when (scope) {
        EventScope.THIS_EVENT -> R.string.ai_proposal_scope_this
        EventScope.THIS_AND_SUBSEQUENT -> R.string.ai_proposal_scope_subsequent
        EventScope.ENTIRE_SERIES -> R.string.ai_proposal_scope_series
    },
)

@Composable
private fun reminderLabel(minutes: Int): String =
    if (minutes > 0) {
        stringResource(R.string.ai_proposal_reminder_before, minutes)
    } else {
        stringResource(R.string.ai_proposal_reminder_off)
    }

/**
 * The composer: a filled field on a raised bar with round attach and send keys. Busy turns the send
 * key into stop; a pending proposal locks the field.
 */
@Composable
private fun AiInputBar(
    resetKey: Int,
    isBusy: Boolean,
    pendingProposal: Boolean,
    enabled: Boolean,
    hasPendingImage: Boolean,
    imagePreview: ImageBitmap?,
    imageLoading: Boolean,
    onAttach: () -> Unit,
    onRemoveImage: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    var text by rememberSaveable(resetKey) { mutableStateOf("") }
    val canSend = (text.isNotBlank() || hasPendingImage) &&
        enabled &&
        !isBusy &&
        !imageLoading &&
        !pendingProposal
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceLow,
        contentColor = colors.ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AiGutter, vertical = 10.dp),
        ) {
            if (imagePreview != null || imageLoading) {
                AttachmentPreview(
                    image = imagePreview,
                    loading = imageLoading,
                    onRemove = onRemoveImage,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                FilledIconButton(
                    onClick = onAttach,
                    enabled = enabled && !imageLoading,
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.surfaceHigh,
                        contentColor = colors.ink,
                        disabledContainerColor = colors.surfaceHigh,
                        disabledContentColor = colors.inkFaint,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add_photo),
                        contentDescription = stringResource(R.string.ai_image_attach),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                TextField(
                    value = text,
                    onValueChange = { text = it.take(AiTokenBudget.MAX_USER_CHARS) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    placeholder = {
                        Text(
                            text = stringResource(
                                if (pendingProposal) {
                                    R.string.ai_input_pending
                                } else {
                                    R.string.ai_input_hint
                                },
                            ),
                            style = type.body,
                        )
                    },
                    textStyle = type.body,
                    maxLines = 5,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceContainer,
                        unfocusedContainerColor = colors.surfaceContainer,
                        disabledContainerColor = colors.surfaceContainer,
                        focusedTextColor = colors.ink,
                        unfocusedTextColor = colors.ink,
                        disabledTextColor = colors.inkMuted,
                        cursorColor = colors.ink,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedPlaceholderColor = colors.inkMuted,
                        unfocusedPlaceholderColor = colors.inkMuted,
                        disabledPlaceholderColor = colors.inkFaint,
                    ),
                )
                Spacer(modifier = Modifier.width(10.dp))
                FilledIconButton(
                    onClick = if (isBusy) {
                        onStop
                    } else {
                        {
                            val typed = text
                            text = ""
                            onSend(typed)
                        }
                    },
                    enabled = if (isBusy) !pendingProposal else canSend,
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.ink,
                        contentColor = colors.paper,
                        disabledContainerColor = colors.surfaceHigh,
                        disabledContentColor = colors.inkFaint,
                    ),
                ) {
                    if (isBusy && !pendingProposal) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.ai_stop),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.ai_send),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(
    image: ImageBitmap?,
    loading: Boolean,
    onRemove: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.small)
                .background(colors.wash),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.inkMuted,
                )
            } else if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = stringResource(R.string.ai_image_attached),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Text(
            text = stringResource(
                if (loading) R.string.ai_image_processing else R.string.ai_image_ready,
            ),
            style = type.bodySmall,
            color = colors.inkMuted,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, enabled = !loading, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.ai_image_remove),
                tint = colors.ink,
            )
        }
    }
}

/** Every dialog on this screen rises from the bottom edge as a sheet. */
@Composable
private fun AiDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ClockwiseBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

@Composable
private fun AiDialogTitle(text: String) {
    Text(text = text, style = Clockwise.type.title, color = Clockwise.colors.ink)
}

@Composable
private fun SessionsDialog(
    sessions: List<AiChatSessionSummary>,
    activeSessionId: Long?,
    busy: Boolean,
    onSwitch: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onNewChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    var editing by remember { mutableStateOf<AiChatSessionSummary?>(null) }
    var deleting by remember { mutableStateOf<AiChatSessionSummary?>(null) }

    AiDialog(onDismiss = onDismiss) {
        Column {
            AiDialogTitle(stringResource(R.string.ai_sessions_title))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ai_sessions_hint),
                style = type.bodySmall,
                color = colors.inkMuted,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.ai_sessions_empty),
                    style = type.body,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            active = session.id == activeSessionId,
                            enabled = !busy,
                            onOpen = {
                                onSwitch(session.id)
                                onDismiss()
                            },
                            onEdit = { editing = session },
                            onDelete = { deleting = session },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietButton(
                    text = stringResource(R.string.ai_new_chat),
                    onClick = {
                        onNewChat()
                        onDismiss()
                    },
                    enabled = !busy,
                )
                Spacer(modifier = Modifier.width(8.dp))
                QuietButton(
                    text = stringResource(R.string.alarm_permission_close),
                    onClick = onDismiss,
                )
            }
        }
    }

    editing?.let { session ->
        SessionTitleDialog(
            session = session,
            onConfirm = { title ->
                onRename(session.id, title)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    deleting?.let { session ->
        SessionDeleteDialog(
            session = session,
            onConfirm = {
                onDelete(session.id)
                deleting = null
                onDismiss()
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun SessionRow(
    session: AiChatSessionSummary,
    active: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val title = session.title.ifBlank {
        stringResource(R.string.ai_sessions_default_title)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = type.title,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (active) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = colors.wash,
                        contentColor = colors.ink,
                    ) {
                        Text(
                            text = stringResource(R.string.ai_sessions_active),
                            style = type.label,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            if (session.preview.isNotBlank()) {
                Text(
                    text = session.preview,
                    style = type.bodySmall,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(
                    R.string.ai_sessions_last_active,
                    formatSessionTime(session.updatedAt),
                ),
                style = type.monoMeta,
                color = colors.inkMuted,
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.ai_sessions_edit_title),
                modifier = Modifier.size(18.dp),
                tint = colors.inkMuted,
            )
        }
        IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.ai_sessions_delete),
                modifier = Modifier.size(18.dp),
                tint = colors.inkMuted,
            )
        }
    }
}

@Composable
private fun SessionTitleDialog(
    session: AiChatSessionSummary,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultTitle = stringResource(R.string.ai_sessions_default_title)
    var title by remember(session.id) {
        mutableStateOf(session.title.ifBlank { defaultTitle })
    }
    AiDialog(onDismiss = onDismiss) {
        Column {
            AiDialogTitle(stringResource(R.string.ai_sessions_edit_title))
            Spacer(modifier = Modifier.height(12.dp))
            SheetTextField(
                value = title,
                onValueChange = { title = it.take(60) },
                label = stringResource(R.string.ai_sessions_title_label),
                placeholder = defaultTitle,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
                Spacer(modifier = Modifier.width(8.dp))
                InkButton(
                    text = stringResource(R.string.save),
                    onClick = { onConfirm(title) },
                )
            }
        }
    }
}

@Composable
private fun SessionDeleteDialog(
    session: AiChatSessionSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val title = session.title.ifBlank {
        stringResource(R.string.ai_sessions_default_title)
    }
    AiDialog(onDismiss = onDismiss) {
        Column {
            AiDialogTitle(stringResource(R.string.ai_sessions_delete_title))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ai_sessions_delete_text, title),
                style = type.body,
                color = colors.inkMuted,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
                Spacer(modifier = Modifier.width(8.dp))
                DangerButton(
                    text = stringResource(R.string.delete),
                    onClick = onConfirm,
                )
            }
        }
    }
}

/** Confirmation slip for the plain yes/no questions on this screen. */
@Composable
private fun AiConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    AiDialog(onDismiss = onDismiss) {
        Column {
            AiDialogTitle(title)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = body, style = type.body, color = colors.inkMuted)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
                Spacer(modifier = Modifier.width(8.dp))
                InkButton(text = confirmLabel, onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun UsageDialog(
    usage: AiUsage,
    requestCount: Int,
    onDismiss: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    AiDialog(onDismiss = onDismiss) {
        Column {
            AiDialogTitle(stringResource(R.string.ai_usage_title))
            Spacer(modifier = Modifier.height(12.dp))
            if (requestCount == 0) {
                Text(
                    text = stringResource(R.string.ai_usage_empty),
                    style = type.body,
                    color = colors.inkMuted,
                )
            } else {
                UsageRow(
                    label = stringResource(R.string.ai_ui_usage_input),
                    value = tokensText(usage.promptTokens),
                )
                UsageRow(
                    label = stringResource(R.string.ai_ui_usage_output),
                    value = tokensText(usage.completionTokens),
                )
                if (usage.cacheReported && usage.promptTokens > 0) {
                    UsageRow(
                        label = stringResource(R.string.ai_ui_usage_cached),
                        value = tokensText(usage.cachedTokens),
                    )
                }
                val cacheRate = usage.cacheHitRate
                UsageRow(
                    label = stringResource(R.string.ai_ui_usage_cache_rate),
                    value = cacheRate?.let(::formatPercent)
                        ?: stringResource(R.string.ai_usage_cache_unknown),
                )
                if (cacheRate != null) {
                    LinearProgressIndicator(
                        progress = { cacheRate.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .height(4.dp),
                        color = colors.ink,
                        trackColor = colors.rule,
                    )
                }
                if (usage.reasoningTokens > 0) {
                    UsageRow(
                        label = stringResource(R.string.ai_ui_usage_reasoning),
                        value = tokensText(usage.reasoningTokens),
                    )
                }
                UsageRow(
                    label = stringResource(R.string.ai_ui_usage_requests),
                    value = requestCount.toString(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.ai_usage_cache_hint),
                    style = type.bodySmall,
                    color = colors.inkMuted,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.ai_usage_hint),
                style = type.bodySmall,
                color = colors.inkMuted,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                QuietButton(
                    text = stringResource(R.string.alarm_permission_close),
                    onClick = onDismiss,
                )
            }
        }
    }
}

/** One line of the usage table: the label speaks, the number is set in mono to align. */
@Composable
private fun UsageRow(label: String, value: String) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = type.body,
            color = colors.inkMuted,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = type.monoSmall,
            color = colors.ink,
        )
    }
}

@Composable
private fun tokensText(value: Long): String =
    stringResource(R.string.ai_ui_usage_tokens, formatTokens(value))

@Composable
private fun formatTime(time: LocalTime): String = rememberClockFormatter().format(time)

@Composable
private fun formatEventTimeRange(
    allDay: Boolean,
    start: LocalTime,
    end: LocalTime,
    endDayOffset: Int,
): String {
    if (allDay) return stringResource(R.string.event_all_day)
    if (!end.isAfter(start) && endDayOffset == 0) return formatTime(start)
    val startText = formatTime(start)
    val endText = formatTime(end)
    return when (endDayOffset) {
        0 -> "$startText–$endText"
        1 -> stringResource(R.string.event_time_range_next_day, startText, endText)
        else -> stringResource(
            R.string.event_time_range_days_later,
            startText,
            endDayOffset,
            endText,
        )
    }
}

@Composable
private fun formatDate(date: LocalDate): String =
    rememberDatePatternFormatter("MMMdEEEE").format(date)

private fun weekdayLabel(isoDay: Int, locale: Locale): String =
    DayOfWeek.of(isoDay).getDisplayName(TextStyle.SHORT, locale)

private fun formatTokens(value: Long): String =
    String.format(Locale.getDefault(), "%,d", value)

private fun formatPercent(rate: Double): String =
    String.format(Locale.getDefault(), "%.1f%%", rate * 100)

@Composable
private fun formatSessionTime(millis: Long): String =
    rememberDatePatternFormatter("MMMdHm")
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
