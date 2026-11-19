package com.dinoe.clockwise.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dinoe.clockwise.R
import com.dinoe.clockwise.ai.AiConfig
import com.dinoe.clockwise.ai.AiModelInfo
import com.dinoe.clockwise.ai.AiProviderConfig
import com.dinoe.clockwise.ai.AiProviderId
import com.dinoe.clockwise.ai.AiReasoningEffort
import com.dinoe.clockwise.ai.AiProviders
import com.dinoe.clockwise.ui.design.Clockwise
import com.dinoe.clockwise.ui.design.ClockwiseBottomSheet
import com.dinoe.clockwise.ui.design.ClockwiseCard
import com.dinoe.clockwise.ui.design.ClockwiseScreenHeader
import com.dinoe.clockwise.ui.design.InkButton
import com.dinoe.clockwise.ui.design.QuietButton
import com.dinoe.clockwise.ui.design.SectionLabel
import com.dinoe.clockwise.ui.design.TimetableRow
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The side margin every block on this screen keeps. */
private val SettingsGutter = 20.dp

/** The sheet measure: content inset inside the sheet's own surface. */
private val SheetInset = 16.dp

@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    viewModel: AiSettingsViewModel = viewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    val detailId = detailKey?.let { AiProviderId.fromPrefKey(it) }
    val snackbarHostState = remember { SnackbarHostState() }
    val message = viewModel.message

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current)
        viewModel.clearMessage()
    }

    BackHandler {
        if (detailId != null) {
            detailKey = null
        } else {
            onBack()
        }
    }

    if (detailId == null) {
        AiSettingsList(
            config = config,
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            onProviderClick = { providerId ->
                viewModel.resetTestState()
                detailKey = providerId.prefKey
            },
        )
    } else {
        AiProviderDetail(
            providerId = detailId,
            provider = config?.provider(detailId),
            isActive = config?.activeProviderId == detailId,
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            onBack = { detailKey = null },
        )
    }
}

@Composable
private fun AiSettingsList(
    config: AiConfig?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onProviderClick: (AiProviderId) -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val active = config?.active()
    val summary = when {
        active == null -> null
        active.hasKey -> stringResource(
            R.string.ai_settings_summary_format,
            active.preset.displayName,
            active.modelId,
        )

        else -> stringResource(R.string.ai_settings_entry_unconfigured)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.paper,
        // The header owns the status bar inset; the screen scrolls under it on paper.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            ClockwiseScreenHeader(
                title = stringResource(R.string.ai_settings_title),
                overline = summary,
                leading = { BackAction(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.ai_settings_intro),
                style = type.bodySmall,
                color = colors.inkMuted,
                modifier = Modifier.padding(
                    start = SettingsGutter,
                    end = SettingsGutter,
                    top = 4.dp,
                ),
            )
            SectionLabel(text = stringResource(R.string.ai_edit_providers))
            if (config == null) {
                LoadingNotice()
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = SettingsGutter),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AiProviders.all.forEach { preset ->
                        ProviderRow(
                            provider = config.provider(preset.id),
                            active = config.activeProviderId == preset.id,
                            onClick = { onProviderClick(preset.id) },
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.ai_settings_privacy),
                style = type.bodySmall,
                color = colors.inkMuted,
                modifier = Modifier.padding(
                    start = SettingsGutter,
                    end = SettingsGutter,
                    top = 16.dp,
                ),
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BackAction(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.ai_settings_back),
            tint = Clockwise.colors.ink,
        )
    }
}

/** One service: a circular check when it is active, its name, and the mono record of key and model. */
@Composable
private fun ProviderRow(
    provider: AiProviderConfig,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    TimetableRow(
        gutterWidth = 28.dp,
        selected = active,
        onClick = onClick,
        gutter = {
            if (active) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = colors.ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) {
                    ActiveBadge()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    ) {
        Text(text = provider.preset.displayName, style = type.title, color = colors.ink)
        Text(
            text = keyStatusText(provider),
            style = type.bodySmall,
            color = if (provider.hasKey && !provider.keyError) colors.inkMuted else colors.signal,
        )
        Text(
            text = provider.modelId,
            style = type.monoMeta,
            color = colors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The active state as a small wash pill, never a bare mono stamp. */
@Composable
private fun ActiveBadge() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = Clockwise.colors.wash,
        contentColor = Clockwise.colors.ink,
    ) {
        Text(
            text = stringResource(R.string.ai_settings_active),
            style = Clockwise.type.label,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AiProviderDetail(
    providerId: AiProviderId,
    provider: AiProviderConfig?,
    isActive: Boolean,
    viewModel: AiSettingsViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    var keyInput by remember(providerId) { mutableStateOf("") }
    var showKey by remember(providerId) { mutableStateOf(false) }
    var showClearKey by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showManualModel by remember { mutableStateOf(false) }

    LaunchedEffect(providerId) {
        keyInput = ""
        showKey = false
        viewModel.resetTestState()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = colors.paper,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            ClockwiseScreenHeader(
                title = AiProviders.preset(providerId).displayName,
                overline = provider?.preset?.baseUrl,
                actions = { BackAction(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (provider == null) {
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                LoadingNotice()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.ai_settings_intro),
                style = type.bodySmall,
                color = colors.inkMuted,
                modifier = Modifier.padding(
                    start = SettingsGutter,
                    end = SettingsGutter,
                    top = 4.dp,
                ),
            )

            SectionLabel(text = stringResource(R.string.ai_settings_api_key_label))
            ClockwiseCard(modifier = Modifier.padding(horizontal = SettingsGutter)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SheetTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = stringResource(R.string.ai_settings_api_key_label),
                        placeholder = stringResource(
                            R.string.ai_settings_api_key_hint,
                            provider.preset.keyHint,
                        ),
                        visualTransformation = if (showKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(
                                onClick = { showKey = !showKey },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = stringResource(
                                        if (showKey) {
                                            R.string.ai_settings_hide_key
                                        } else {
                                            R.string.ai_settings_show_key
                                        },
                                    ),
                                    tint = colors.inkMuted,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = keyStatusText(provider),
                        style = type.bodySmall,
                        color = if (provider.hasKey && !provider.keyError) {
                            colors.inkMuted
                        } else {
                            colors.signal
                        },
                    )
                    if (provider.hasKey) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.ai_edit_key_keep),
                            style = type.bodySmall,
                            color = colors.inkMuted,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InkButton(
                            text = stringResource(R.string.ai_settings_api_key_save),
                            onClick = {
                                viewModel.saveKey(providerId, keyInput)
                                keyInput = ""
                            },
                            enabled = keyInput.isNotBlank(),
                        )
                        if (provider.hasKey) {
                            QuietButton(
                                text = stringResource(R.string.ai_settings_api_key_clear),
                                onClick = { showClearKey = true },
                            )
                        }
                    }
                }
            }

            SectionLabel(
                text = stringResource(R.string.ai_settings_model),
                meta = if (provider.modelsFetchedAt > 0) {
                    stringResource(
                        R.string.ai_settings_last_model_refresh,
                        formatTimestamp(provider.modelsFetchedAt),
                    )
                } else {
                    null
                },
            )
            ClockwiseCard(modifier = Modifier.padding(horizontal = SettingsGutter)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    SettingsRow(
                        label = stringResource(R.string.ai_settings_model_current),
                        onClick = { showModelPicker = true },
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colors.inkMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = provider.modelId,
                                style = type.monoSmall,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            contextLabel(provider)?.let { label ->
                                Text(text = label, style = type.monoMeta, color = colors.inkMuted)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.ai_settings_reasoning_label),
                        style = type.label,
                        color = colors.inkMuted,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AiChipSelect(
                        options = AiReasoningEffort.entries.map { effort ->
                            effort to reasoningLabel(effort)
                        },
                        selected = listOf(provider.reasoningEffort),
                        onToggle = { effort -> viewModel.setReasoningEffort(providerId, effort) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.ai_settings_reasoning_helper),
                        style = type.bodySmall,
                        color = colors.inkMuted,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.ai_settings_cache_helper),
                        style = type.bodySmall,
                        color = colors.inkMuted,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuietButton(
                            text = stringResource(
                                if (viewModel.refreshingProvider == providerId) {
                                    R.string.ai_settings_model_refreshing
                                } else {
                                    R.string.ai_settings_model_refresh
                                },
                            ),
                            onClick = {
                                viewModel.refreshModels(
                                    providerId,
                                    keyInput.trim().takeIf { it.isNotEmpty() },
                                )
                            },
                            enabled = viewModel.refreshingProvider == null,
                        )
                        if (viewModel.refreshingProvider == providerId) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = colors.ink,
                                trackColor = colors.rule,
                            )
                        }
                    }
                }
            }

            SectionLabel(text = stringResource(R.string.ai_settings_connection))
            ClockwiseCard(modifier = Modifier.padding(horizontal = SettingsGutter)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.ai_settings_test_body),
                        style = type.bodySmall,
                        color = colors.inkMuted,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuietButton(
                            text = stringResource(R.string.ai_settings_test),
                            onClick = {
                                viewModel.testConnection(
                                    providerId = providerId,
                                    modelId = provider.modelId,
                                    keyOverride = keyInput.trim().takeIf { it.isNotEmpty() },
                                    reasoningEffort = provider.reasoningEffort,
                                )
                            },
                            enabled = (provider.hasKey || keyInput.isNotBlank()) &&
                                viewModel.testState !is AiTestState.Testing,
                        )
                        when (val state = viewModel.testState) {
                            is AiTestState.Testing -> if (state.providerId == providerId) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.ink,
                                    trackColor = colors.rule,
                                )
                                Text(
                                    text = stringResource(R.string.ai_settings_testing),
                                    style = type.bodySmall,
                                    color = colors.inkMuted,
                                )
                            }

                            is AiTestState.Success -> if (state.providerId == providerId) {
                                Text(
                                    text = stringResource(
                                        R.string.ai_settings_test_success,
                                        state.latencyMillis,
                                    ),
                                    style = type.monoSmall,
                                    color = colors.ink,
                                )
                            }

                            is AiTestState.Failure -> if (state.providerId == providerId) {
                                Text(
                                    text = stringResource(
                                        R.string.ai_settings_test_failed,
                                        state.message,
                                    ),
                                    style = type.bodySmall,
                                    color = colors.signal,
                                )
                            }

                            AiTestState.Idle -> Unit
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    if (isActive) {
                        ActiveBadge()
                    } else {
                        InkButton(
                            text = stringResource(R.string.ai_settings_set_active),
                            onClick = { viewModel.setActive(providerId) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsTextRow(
                        label = stringResource(R.string.ai_settings_endpoint),
                        value = provider.preset.baseUrl,
                        maxLines = 2,
                    )
                }
            }

            Text(
                text = stringResource(R.string.ai_settings_privacy),
                style = type.bodySmall,
                color = colors.inkMuted,
                modifier = Modifier.padding(
                    start = SettingsGutter,
                    end = SettingsGutter,
                    top = 16.dp,
                ),
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showClearKey) {
        ConfirmSheet(
            title = stringResource(R.string.ai_settings_clear_key_title),
            body = stringResource(R.string.ai_settings_clear_key_text),
            confirmLabel = stringResource(R.string.ai_settings_api_key_clear),
            onConfirm = {
                viewModel.clearKey(providerId)
                showClearKey = false
            },
            onDismiss = { showClearKey = false },
        )
    }

    if (showModelPicker && provider != null) {
        ModelPickerDialog(
            provider = provider,
            refreshing = viewModel.refreshingProvider == providerId,
            onRefresh = {
                viewModel.refreshModels(
                    providerId,
                    keyInput.trim().takeIf { it.isNotEmpty() },
                )
            },
            onSelect = { model ->
                viewModel.setModel(providerId, model.id)
                showModelPicker = false
            },
            onManual = {
                showModelPicker = false
                showManualModel = true
            },
            onDismiss = { showModelPicker = false },
        )
    }

    if (showManualModel && provider != null) {
        ManualModelDialog(
            initial = provider.modelId,
            onConfirm = { modelId ->
                viewModel.setModel(providerId, modelId)
                showManualModel = false
            },
            onDismiss = { showManualModel = false },
        )
    }
}

/** A settings entry inside its card: what it is on the left, its value set in mono on the right. */
@Composable
private fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    value: @Composable () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = type.body,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        value()
        trailing?.invoke()
    }
}

@Composable
private fun SettingsTextRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    SettingsRow(label = label, modifier = modifier) {
        Text(
            text = value,
            style = type.monoMeta,
            color = colors.inkMuted,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    ClockwiseBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SheetInset, end = SheetInset, top = 4.dp, bottom = SheetInset),
        ) {
            Text(text = title, style = type.title, color = colors.ink)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = body, style = type.body, color = colors.inkMuted)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
                InkButton(text = confirmLabel, onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun ModelPickerDialog(
    provider: AiProviderConfig,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSelect: (AiModelInfo) -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    var query by remember { mutableStateOf("") }
    val filtered = remember(provider.models, query) {
        if (query.isBlank()) {
            provider.models
        } else {
            provider.models.filter {
                it.id.contains(query, ignoreCase = true) ||
                    it.name.contains(query, ignoreCase = true)
            }
        }
    }
    ClockwiseBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = SheetInset,
                        end = 4.dp,
                        top = 4.dp,
                        bottom = 8.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.ai_edit_model_count,
                            filtered.size,
                        ),
                        style = type.monoMeta,
                        color = colors.inkMuted,
                    )
                    Text(
                        text = stringResource(R.string.ai_settings_model_picker_title),
                        style = type.title,
                        color = colors.ink,
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !refreshing,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.ink,
                            trackColor = colors.rule,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(
                                R.string.ai_settings_model_refresh,
                            ),
                            tint = colors.inkMuted,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = SheetInset),
            ) {
                SheetTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.ai_settings_model_search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.ai_settings_model_empty),
                    style = type.bodySmall,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(horizontal = SheetInset, vertical = 8.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                ) {
                    items(filtered, key = { it.id }) { model ->
                        ModelRow(
                            model = model,
                            selected = model.id == provider.modelId,
                            onClick = { onSelect(model) },
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = SheetInset, vertical = 10.dp),
            ) {
                QuietButton(
                    text = stringResource(R.string.ai_settings_model_manual),
                    onClick = onManual,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = SheetInset, end = SheetInset, bottom = SheetInset),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietButton(
                    text = stringResource(R.string.alarm_permission_close),
                    onClick = onDismiss,
                )
            }
        }
    }
}

/** A model: its name, its mono id, and a circular check when it is the one in use. */
@Composable
private fun ModelRow(
    model: AiModelInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val activeLabel = stringResource(R.string.ai_settings_active)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = SheetInset, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = model.name,
                style = type.body,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = model.id,
                style = type.monoMeta,
                color = colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            model.contextLength?.let { length ->
                Text(
                    text = stringResource(
                        R.string.ai_settings_model_context,
                        formatContext(length),
                    ),
                    style = type.monoMeta,
                    color = colors.inkMuted,
                )
            }
        }
        if (selected) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = activeLabel,
                tint = colors.ink,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ManualModelDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    var value by remember { mutableStateOf(initial) }
    ClockwiseBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = SheetInset, end = SheetInset, top = 4.dp, bottom = SheetInset),
        ) {
            Text(
                text = stringResource(R.string.ai_settings_model_manual),
                style = type.title,
                color = colors.ink,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SheetTextField(
                value = value,
                onValueChange = { value = it },
                label = stringResource(R.string.ai_settings_model_manual_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
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
                    text = stringResource(R.string.ai_settings_manual_apply),
                    onClick = { onConfirm(value.trim()) },
                    enabled = value.isNotBlank(),
                )
            }
        }
    }
}

/** Loading is a quiet line on the page, not a spinner in the middle of nothing. */
@Composable
private fun LoadingNotice(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.ai_settings_loading),
            style = Clockwise.type.bodySmall,
            color = Clockwise.colors.inkMuted,
        )
    }
}

@Composable
private fun reasoningLabel(effort: AiReasoningEffort): String = stringResource(
    when (effort) {
        AiReasoningEffort.OFF -> R.string.ai_reasoning_off
        AiReasoningEffort.LOW -> R.string.ai_reasoning_low
        AiReasoningEffort.MEDIUM -> R.string.ai_reasoning_medium
        AiReasoningEffort.HIGH -> R.string.ai_reasoning_high
    },
)

@Composable
private fun keyStatusText(provider: AiProviderConfig): String = when {
    provider.keyError -> stringResource(R.string.ai_settings_key_error)
    provider.hasKey -> stringResource(
        R.string.ai_settings_key_set,
        provider.keyLast4.orEmpty(),
    )

    else -> stringResource(R.string.ai_settings_key_missing)
}

@Composable
private fun contextLabel(provider: AiProviderConfig): String? {
    val model = provider.models.firstOrNull { it.id == provider.modelId } ?: return null
    val length = model.contextLength ?: return null
    return stringResource(R.string.ai_settings_model_context, formatContext(length))
}

private fun formatContext(value: Long): String = when {
    value >= 1_000_000 -> {
        val millions = value / 1_000_000.0
        if (millions == millions.toLong().toDouble()) {
            "${millions.toLong()}M"
        } else {
            String.format(Locale.US, "%.1fM", millions)
        }
    }

    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}

@Composable
private fun formatTimestamp(millis: Long): String =
    rememberDatePatternFormatter("yMMMdHm")
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
