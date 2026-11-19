package com.dinoe.clockwise.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.dinoe.clockwise.ClockwiseApp
import com.dinoe.clockwise.R
import com.dinoe.clockwise.alarm.AlarmScheduler
import com.dinoe.clockwise.alarm.AlarmSounds
import com.dinoe.clockwise.alarm.ReminderScheduler
import com.dinoe.clockwise.ai.AiChatItem
import com.dinoe.clockwise.ai.AiChatSessionStore
import com.dinoe.clockwise.ai.AiConfig
import com.dinoe.clockwise.ai.AiDraftEditor
import com.dinoe.clockwise.ai.AiErrorKind
import com.dinoe.clockwise.ai.AiImageAttachment
import com.dinoe.clockwise.ai.AiImageProcessor
import com.dinoe.clockwise.ai.AiProviderConfig
import com.dinoe.clockwise.ai.AiSessionState
import com.dinoe.clockwise.ai.AiTokenBudget
import com.dinoe.clockwise.ai.AiTurnCredentials
import com.dinoe.clockwise.ai.AiTurnEngine
import com.dinoe.clockwise.ai.AiUsage
import com.dinoe.clockwise.ai.AlarmDeleteDraft
import com.dinoe.clockwise.ai.AlarmDraft
import com.dinoe.clockwise.ai.AlarmStackDraft
import com.dinoe.clockwise.ai.AlarmUpdateDraft
import com.dinoe.clockwise.ai.ChatImage
import com.dinoe.clockwise.ai.ChatMessage
import com.dinoe.clockwise.ai.ChatPersistenceSnapshot
import com.dinoe.clockwise.ai.ChatRole
import com.dinoe.clockwise.ai.EventDeleteDraft
import com.dinoe.clockwise.ai.EventDraft
import com.dinoe.clockwise.ai.EventUpdateDraft
import com.dinoe.clockwise.ai.PreparedDrafts
import com.dinoe.clockwise.ai.ProposalState
import com.dinoe.clockwise.ai.RestoredChatSession
import com.dinoe.clockwise.ai.SessionOpenResult
import com.dinoe.clockwise.ai.TurnEvent
import com.dinoe.clockwise.ai.TurnFailure
import com.dinoe.clockwise.ai.TurnNotice
import com.dinoe.clockwise.ai.TurnReadTool
import com.dinoe.clockwise.ai.TurnStatus
import com.dinoe.clockwise.data.AI_CHAT_SESSION_DEFAULT_TITLE
import com.dinoe.clockwise.data.AiChatSessionSummary
import com.dinoe.clockwise.data.Tag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiChatViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ClockwiseApp
    private val repository = app.aiRepository
    private val writer = app.scheduleWriter
    private val tagDao = app.database.tagDao()
    private val alarmDao = app.database.alarmDao()
    private val eventDao = app.database.eventDao()
    private val sessionStore = AiChatSessionStore(app.database.chatSessionDao())
    private val imageProcessor = AiImageProcessor(app)
    private val preferences = ThemePreferences(app)
    private val engine = AiTurnEngine(
        tagDao = tagDao,
        alarmDao = alarmDao,
        eventDao = eventDao,
        complete = { credentials, messages, maxTokens ->
            repository.complete(
                credentials,
                messages,
                maxTokens = maxTokens,
                promptCacheKey = chatSession.promptCacheKey,
            )
        },
        ringtones = { AlarmSounds.available(app) },
    )

    val sessions: StateFlow<List<AiChatSessionSummary>> = sessionStore.summaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTags: StateFlow<List<Tag>> = tagDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val timePickerStyle: StateFlow<TimePickerStyle> = preferences.timePickerStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimePickerStyle.WHEEL)

    val items = mutableStateListOf<AiChatItem>()
    var isBusy by mutableStateOf(false)
        private set
    var statusText by mutableStateOf<String?>(null)
        private set
    var config by mutableStateOf<AiConfig?>(null)
        private set
    var pendingProposalId by mutableStateOf<Long?>(null)
        private set
    var usage by mutableStateOf(AiUsage())
        private set
    var requestCount by mutableStateOf(0)
        private set
    var isRestoring by mutableStateOf(true)
        private set
    var activeSessionId by mutableStateOf<Long?>(null)
        private set
    var pendingImage by mutableStateOf<AiImageAttachment?>(null)
        private set
    var isImageLoading by mutableStateOf(false)
        private set

    /** Bumped when the open conversation changes so the input bar drops its local draft. */
    var inputResetKey by mutableIntStateOf(0)
        private set

    /** Decoded chat thumbnails shared with the screen; entries follow the open session. */
    val thumbnails = ChatThumbnailCache()

    private val chatSession = AiSessionState()
    private var currentUserText = ""
    private var pendingWrite: PendingWrite? = null
    private var turnJob: Job? = null
    private var imageJob: Job? = null
    private var lastErrorId: Long? = null
    private var nextItemId = 1L
    private var sessionTitle = AI_CHAT_SESSION_DEFAULT_TITLE
    private var sessionCreatedAt = System.currentTimeMillis()
    private var saveFailureNotified = false

    val activeProvider: AiProviderConfig? get() = config?.active()
    val needsSetup: Boolean get() = config?.active()?.hasKey == false
    val hasPendingProposal: Boolean get() = pendingProposalId != null

    init {
        viewModelScope.launch {
            repository.config.collect { value -> config = value }
        }
        viewModelScope.launch { restoreLatestSession() }
    }

    fun attachImage(uri: Uri) {
        if (isBusy || isRestoring || isImageLoading || pendingProposalId != null) return
        imageJob?.cancel()
        thumbnails.evict(pendingImage?.previewBase64)
        imageJob = viewModelScope.launch {
            isImageLoading = true
            try {
                pendingImage = imageProcessor.load(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                addNotice(app.getString(R.string.ai_image_load_failed))
            } finally {
                isImageLoading = false
                imageJob = null
            }
        }
    }

    fun removeImage() {
        if (isImageLoading) return
        thumbnails.evict(pendingImage?.previewBase64)
        pendingImage = null
    }

    fun showImageUnsupported() {
        addNotice(app.getString(R.string.ai_image_unsupported))
    }

    fun send(typed: String) {
        if (isBusy || isRestoring || isImageLoading || pendingProposalId != null) return
        val provider = activeProvider ?: return
        val text = typed.take(AiTokenBudget.MAX_USER_CHARS).trim()
        val image = pendingImage
        if (text.isEmpty() && image == null) return
        if (!provider.hasKey) {
            addError(app.getString(R.string.ai_error_missing_key), retryable = false)
            return
        }
        val content = text.ifEmpty { app.getString(R.string.ai_image_only_prompt) }
        pendingImage = null
        lastErrorId = null
        items += AiChatItem.User(nextItemId(), content, image?.previewBase64)
        currentUserText = content
        val userMessage = ChatMessage(
            role = ChatRole.USER,
            content = content,
            images = image?.let {
                listOf(ChatImage(base64 = it.requestBase64, mimeType = it.mimeType))
            }.orEmpty(),
        )
        viewModelScope.launch { persistSafely() }
        startTurn(provider) { credentials ->
            engine.runTurn(
                credentials = credentials,
                userMessage = userMessage,
                completedTurns = chatSession.completedTurns,
                actionLines = chatSession.actionLines,
            )
        }
    }

    fun stop() {
        if (turnJob == null) return
        turnJob?.cancel()
        turnJob = null
        val stopped = app.getString(R.string.ai_stopped)
        addNotice(stopped)
        // Do not create a synthetic assistant turn here: a stopped request has no reasoning
        // content to echo back, and a tools-bearing reasoning model would reject that history.
        engine.discardPendingTurn()
        currentUserText = ""
        isBusy = false
        statusText = null
    }

    fun retry() {
        val provider = activeProvider ?: return
        if (isBusy || pendingWrite != null || lastErrorId == null) return
        lastErrorId?.let { id -> items.removeAll { it.id == id } }
        lastErrorId = null
        startTurn(provider) { credentials ->
            engine.resumeTurn(
                credentials = credentials,
                completedTurns = chatSession.completedTurns,
                actionLines = chatSession.actionLines,
            )
        }
    }

    private fun startTurn(
        provider: AiProviderConfig,
        turn: (AiTurnCredentials) -> Flow<TurnEvent>,
    ) {
        if (isBusy) return
        isBusy = true
        statusText = app.getString(R.string.ai_status_thinking)
        turnJob = viewModelScope.launch {
            try {
                // One provider configuration and one decrypted credential per turn; every tool
                // round reuses them instead of re-reading the DataStore.
                turn(repository.resolveCredentials(provider)).collect { handleTurnEvent(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                // Engine turn-state misuse; unreachable through the gated entry points above.
                addError(app.getString(R.string.ai_error_unknown), retryable = false)
            } catch (e: Exception) {
                addError(
                    app.getString(R.string.ai_error_unknown) + detailSuffix(e.message),
                    retryable = true,
                )
            } finally {
                isBusy = false
                statusText = null
                turnJob = null
            }
        }
    }

    /** Translates engine events into transcript items and status; strings stay in this layer. */
    private suspend fun handleTurnEvent(event: TurnEvent) {
        when (event) {
            is TurnEvent.Status -> statusText = statusText(event.status)

            is TurnEvent.AssistantText ->
                items += AiChatItem.Assistant(nextItemId(), event.text)

            is TurnEvent.Notice -> {
                statusText = null
                when (event.notice) {
                    TurnNotice.HISTORY_TRIMMED ->
                        addNotice(app.getString(R.string.ai_history_trimmed))

                    TurnNotice.CORRECTING_RETRY ->
                        addNotice(app.getString(R.string.ai_correcting_notice))
                }
            }

            is TurnEvent.ReadResult -> addNotice(readNoticeText(event))

            is TurnEvent.UsageDelta -> recordUsage(event.usage)

            is TurnEvent.ProposalReady -> {
                statusText = null
                applyDrafts(event.drafts, event.reasoningContent, event.dynamicContext)
            }

            is TurnEvent.Finished -> {
                val answer = event.answer.ifBlank { app.getString(R.string.ai_empty_reply) }
                items += AiChatItem.Assistant(nextItemId(), answer)
                chatSession.completeTurn(
                    currentUserText,
                    answer,
                    event.reasoningContent,
                    event.dynamicContext,
                )
                event.readSummary?.let { summary -> chatSession.addAction("last read: $summary") }
                persistSafely()
            }

            is TurnEvent.Failed -> handleTurnFailure(event.failure)
        }
    }

    private fun applyDrafts(
        drafts: PreparedDrafts,
        reasoningContent: String?,
        dynamicContext: String?,
    ) {
        val proposal = AiChatItem.Proposal(
            id = nextItemId(),
            alarms = drafts.alarms,
            alarmStacks = drafts.alarmStacks,
            events = drafts.events,
            alarmUpdates = drafts.alarmUpdates,
            alarmDeletes = drafts.alarmDeletes,
            eventUpdates = drafts.eventUpdates,
            eventDeletes = drafts.eventDeletes,
            state = ProposalState.PENDING,
        )
        items += proposal
        pendingWrite = PendingWrite(
            proposalId = proposal.id,
            userText = currentUserText,
            alarms = drafts.alarms,
            alarmStacks = drafts.alarmStacks,
            events = drafts.events,
            alarmUpdates = drafts.alarmUpdates,
            alarmDeletes = drafts.alarmDeletes,
            eventUpdates = drafts.eventUpdates,
            eventDeletes = drafts.eventDeletes,
            reasoningContent = reasoningContent,
            dynamicContext = dynamicContext,
        )
        pendingProposalId = proposal.id
    }

    private fun statusText(status: TurnStatus): String = when (status) {
        TurnStatus.THINKING -> app.getString(R.string.ai_status_thinking)
        TurnStatus.READING_ALARMS -> app.getString(R.string.ai_status_reading_alarms)
        TurnStatus.READING_EVENTS -> app.getString(R.string.ai_status_reading_events)
        TurnStatus.READING_RINGTONES -> app.getString(R.string.ai_status_reading_ringtones)
        TurnStatus.CORRECTING -> app.getString(R.string.ai_status_correcting)
    }

    private fun readNoticeText(event: TurnEvent.ReadResult): String = when (event.tool) {
        TurnReadTool.ALARMS ->
            if (event.shown < event.total) {
                app.getString(R.string.ai_read_alarms_truncated, event.shown, event.total)
            } else {
                app.getString(R.string.ai_read_alarms, event.shown)
            }

        TurnReadTool.EVENTS ->
            if (event.shown < event.total) {
                app.getString(R.string.ai_read_events_truncated, event.shown, event.total)
            } else {
                app.getString(R.string.ai_read_events, event.shown)
            }

        TurnReadTool.RINGTONES ->
            if (event.shown < event.total) {
                app.getString(R.string.ai_read_ringtones_truncated, event.shown, event.total)
            } else {
                app.getString(R.string.ai_read_ringtones, event.shown)
            }
    }

    private fun handleTurnFailure(failure: TurnFailure) {
        when (failure) {
            is TurnFailure.Api -> {
                val message = when (failure.kind) {
                    AiErrorKind.MISSING_KEY -> app.getString(R.string.ai_error_missing_key)
                    AiErrorKind.UNAUTHORIZED -> app.getString(R.string.ai_error_unauthorized)
                    AiErrorKind.INVALID_REQUEST -> app.getString(R.string.ai_error_invalid_request)
                    AiErrorKind.INSUFFICIENT_CREDIT -> app.getString(R.string.ai_error_credit)
                    AiErrorKind.MODEL_NOT_FOUND -> app.getString(R.string.ai_error_model_not_found)
                    AiErrorKind.RATE_LIMITED -> app.getString(R.string.ai_error_rate_limited)
                    AiErrorKind.SERVER_ERROR -> app.getString(R.string.ai_error_server)
                    AiErrorKind.NETWORK -> app.getString(R.string.ai_error_network)
                    AiErrorKind.INVALID_RESPONSE -> app.getString(R.string.ai_error_invalid_response)
                    AiErrorKind.TRUNCATED -> app.getString(R.string.ai_error_truncated)
                    AiErrorKind.UNKNOWN -> app.getString(R.string.ai_error_unknown)
                }
                val retryable = failure.kind == AiErrorKind.NETWORK ||
                    failure.kind == AiErrorKind.RATE_LIMITED ||
                    failure.kind == AiErrorKind.SERVER_ERROR
                addError(message + detailSuffix(failure.detail), retryable)
            }

            TurnFailure.ContextOverflow ->
                addError(app.getString(R.string.ai_error_too_long), retryable = false)

            TurnFailure.RoundsExhausted ->
                addError(app.getString(R.string.ai_error_tool_rounds), retryable = true)

            is TurnFailure.Unknown ->
                addError(
                    app.getString(R.string.ai_error_unknown) + detailSuffix(failure.detail),
                    retryable = true,
                )
        }
    }

    fun confirmProposal() {
        val pending = pendingWrite ?: return
        if (isBusy) return
        isBusy = true
        statusText = app.getString(R.string.ai_status_creating)
        viewModelScope.launch {
            try {
                val result = executePending(pending)
                val summary = summarize(result)
                items += AiChatItem.Assistant(nextItemId(), summary)
                markProposal(pending.proposalId, ProposalState.APPLIED)
                chatSession.addAction(result.actionLine)
                chatSession.completeTurn(
                    pending.userText,
                    summary,
                    pending.reasoningContent,
                    pending.dynamicContext,
                )
                engine.discardPendingTurn()
                currentUserText = ""
                pendingWrite = null
                pendingProposalId = null
                persistSafely()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addError(
                    app.getString(R.string.ai_error_unknown) + detailSuffix(e.message),
                    retryable = false,
                )
            } finally {
                isBusy = false
                statusText = null
            }
        }
    }

    fun cancelProposal() {
        val pending = pendingWrite ?: return
        if (isBusy) return
        val summary = app.getString(R.string.ai_cancelled_summary)
        items += AiChatItem.Assistant(nextItemId(), summary)
        markProposal(pending.proposalId, ProposalState.CANCELLED)
        chatSession.addAction(
            "cancelled proposal: ${pending.alarms.size} alarms, " +
                "${pending.alarmStacks.size} stacks, ${pending.events.size} events",
        )
        chatSession.completeTurn(
            pending.userText,
            summary,
            pending.reasoningContent,
            pending.dynamicContext,
        )
        engine.discardPendingTurn()
        currentUserText = ""
        pendingWrite = null
        pendingProposalId = null
        viewModelScope.launch { persistSafely() }
    }

    fun updateAlarmDraft(index: Int, draft: AlarmDraft) {
        val pending = pendingWrite ?: return
        if (isBusy || index !in pending.alarms.indices) return
        val alarms = pending.alarms.toMutableList().apply {
            this[index] = AiDraftEditor.normalizeAlarm(draft)
        }
        pendingWrite = pending.copy(alarms = alarms)
        updateProposalItem(pending.proposalId) { it.copy(alarms = alarms) }
    }

    fun updateAlarmStackDraft(index: Int, draft: AlarmStackDraft) {
        val pending = pendingWrite ?: return
        if (isBusy || index !in pending.alarmStacks.indices) return
        val alarmStacks = pending.alarmStacks.toMutableList().apply {
            this[index] = AiDraftEditor.normalizeStack(draft)
        }
        pendingWrite = pending.copy(alarmStacks = alarmStacks)
        updateProposalItem(pending.proposalId) { it.copy(alarmStacks = alarmStacks) }
    }

    fun updateEventDraft(index: Int, draft: EventDraft) {
        val pending = pendingWrite ?: return
        if (isBusy || index !in pending.events.indices) return
        val events = pending.events.toMutableList().apply {
            this[index] = AiDraftEditor.normalizeEvent(draft)
        }
        pendingWrite = pending.copy(events = events)
        updateProposalItem(pending.proposalId) { it.copy(events = events) }
    }

    fun clearConversation() {
        if (isRestoring) return
        if (isBusy && pendingWrite != null) return
        startNewDraft()
        isRestoring = false
    }

    fun refreshSessions() {
        viewModelScope.launch {
            runCatching { sessionStore.touchAndClean(activeSessionId) }
        }
    }

    fun switchSession(id: Long) {
        if (isBusy || isRestoring || id == activeSessionId) return
        viewModelScope.launch {
            isRestoring = true
            try {
                val result = sessionStore.open(id)
                if (result is SessionOpenResult.Opened) {
                    applyRestoredSession(result.session)
                }
            } finally {
                isRestoring = false
            }
        }
    }

    fun deleteSession(id: Long) {
        if (isBusy || isRestoring) return
        viewModelScope.launch {
            isRestoring = true
            try {
                val wasActive = id == activeSessionId
                val restored = sessionStore.deleteAndReopen(id, wasActive)
                when {
                    restored != null -> applyRestoredSession(restored)
                    wasActive -> startNewDraft()
                }
            } finally {
                isRestoring = false
            }
        }
    }

    fun renameSession(id: Long, rawTitle: String) {
        val cleaned = rawTitle.trim().ifBlank {
            AI_CHAT_SESSION_DEFAULT_TITLE
        }.take(60)
        viewModelScope.launch {
            runCatching {
                sessionStore.rename(id, cleaned)
                if (id == activeSessionId) sessionTitle = cleaned
            }
        }
    }

    private fun startNewDraft() {
        turnJob?.cancel()
        turnJob = null
        imageJob?.cancel()
        imageJob = null
        inputResetKey++
        pendingImage = null
        thumbnails.clear()
        isImageLoading = false
        activeSessionId = null
        sessionTitle = AI_CHAT_SESSION_DEFAULT_TITLE
        sessionCreatedAt = System.currentTimeMillis()
        items.clear()
        chatSession.clear()
        engine.discardPendingTurn()
        currentUserText = ""
        pendingWrite = null
        pendingProposalId = null
        isBusy = false
        statusText = null
        lastErrorId = null
        usage = AiUsage()
        requestCount = 0
        saveFailureNotified = false
    }

    private suspend fun restoreLatestSession() {
        try {
            val restored = sessionStore.openLatest()
            if (restored != null) {
                applyRestoredSession(restored)
            } else {
                startNewDraft()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            startNewDraft()
        } finally {
            isRestoring = false
        }
    }

    /**
     * Publishes an already decoded session. Runs as one Main-thread update: the payload was
     * decoded off Main by [AiChatSessionStore], so no suspension happens between the state writes.
     */
    private fun applyRestoredSession(restored: RestoredChatSession) {
        turnJob?.cancel()
        turnJob = null
        imageJob?.cancel()
        imageJob = null
        inputResetKey++
        pendingImage = null
        isImageLoading = false
        activeSessionId = restored.id
        sessionTitle = restored.title
        sessionCreatedAt = restored.createdAt
        chatSession.restore(
            restored.turns,
            restored.actions,
            restored.usage,
            restored.requestCount,
            restored.promptCacheKey,
        )
        usage = chatSession.usage
        requestCount = chatSession.requestCount
        val restoredItems = restored.messages.mapNotNull { message ->
            when (message.role) {
                "user" -> AiChatItem.User(nextItemId(), message.text, message.image)
                "assistant" -> AiChatItem.Assistant(nextItemId(), message.text)
                else -> null
            }
        }
        items.clear()
        items.addAll(restoredItems)
        thumbnails.retainOnly(restoredItems.mapNotNull { (it as? AiChatItem.User)?.image })
        if (restored.corrupt) {
            addNotice(app.getString(R.string.ai_session_corrupt))
        }
        if (restored.endsWithUserMessage) {
            addNotice(app.getString(R.string.ai_session_interrupted))
        }
        engine.discardPendingTurn()
        currentUserText = ""
        pendingWrite = null
        pendingProposalId = null
        isBusy = false
        statusText = null
        lastErrorId = null
        saveFailureNotified = false
    }

    private suspend fun persistSafely() {
        try {
            persistSession()
            saveFailureNotified = false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (!saveFailureNotified) {
                addNotice(app.getString(R.string.ai_session_save_failed))
                saveFailureNotified = true
            }
        }
    }

    private suspend fun persistSession() {
        val sessionId = sessionStore.persist {
            // Snapshot only; conversion, preview and encoding happen inside the store off Main.
            ChatPersistenceSnapshot(
                items = items.toList(),
                turns = chatSession.completedTurns,
                actions = chatSession.actionLines,
                usage = chatSession.usage,
                requestCount = chatSession.requestCount,
                promptCacheKey = chatSession.promptCacheKey,
                sessionId = activeSessionId,
                title = sessionTitle,
            )
        }
        if (sessionId != null) activeSessionId = sessionId
    }

    private suspend fun executePending(pending: PendingWrite): ExecutionResult =
        withContext(Dispatchers.IO) {
            // Resolve every referenced row before the first write. A stale id must abort the whole
            // proposal instead of producing a partial result built by runCatching.
            val alarmUpdates = pending.alarmUpdates.map { draft ->
                val alarm = alarmDao.getById(draft.alarmId)
                    ?: error("alarm ${draft.alarmId} no longer exists")
                draft to alarm
            }
            val alarmDeletes = pending.alarmDeletes.map { draft ->
                val alarm = alarmDao.getById(draft.alarmId)
                    ?: error("alarm ${draft.alarmId} no longer exists")
                draft to alarm
            }
            val eventUpdates = pending.eventUpdates.map { draft ->
                val event = eventDao.getById(draft.eventId)
                    ?: error("event ${draft.eventId} no longer exists")
                draft to event
            }
            val eventDeletes = pending.eventDeletes.map { draft ->
                val event = eventDao.getById(draft.eventId)
                    ?: error("event ${draft.eventId} no longer exists")
                draft to event
            }

            val result = try {
                // One outer transaction makes every row change all-or-nothing. ScheduleWriter's
                // nested withTransaction calls join this transaction; alarm scheduling side
                // effects are restored below if the outer transaction fails.
                app.database.withTransaction {
                    var createdPlainAlarms = 0
                    var createdEventAlarms = 0
                    var createdEvents = 0
                    var updatedAlarms = 0
                    var updatedEvents = 0
                    var deletedAlarms = 0
                    var deletedEvents = 0
                    var scheduledAlarms = 0
                    var unscheduledAlarms = 0
                    val notes = mutableListOf<String>()

                    pending.alarms.forEach { draft ->
                        val creation = writer.addAlarm(
                            time = draft.time.toSecondOfDay(),
                            fireDate = draft.date?.toEpochDay(),
                            name = draft.name,
                            note = draft.note,
                            enabled = draft.enabled,
                            oneTime = draft.oneTime,
                            tags = draft.tags.map { Tag(id = it.id, name = it.name) },
                            ringtone = draft.ringtone,
                        )
                        createdPlainAlarms++
                        if (creation.alarm.enabled) {
                            if (creation.scheduled) scheduledAlarms++ else unscheduledAlarms++
                        }
                        if (notes.size < 5) {
                            notes += if (draft.date != null) {
                                "created ${draft.date} ${draft.time} ${draft.name}".trim()
                            } else {
                                "created ${draft.time} next ${draft.name}".trim()
                            }
                        }
                    }

                    pending.alarmStacks.forEach { draft ->
                        val creations = writer.addAlarmStack(
                            times = draft.times.map { it.toSecondOfDay() },
                            name = draft.name,
                            note = draft.note,
                            enabled = draft.enabled,
                            oneTime = draft.oneTime,
                            tags = draft.tags.map { Tag(id = it.id, name = it.name) },
                            ringtone = draft.ringtone,
                        )
                        createdPlainAlarms += creations.size
                        creations.forEach { creation ->
                            if (creation.alarm.enabled) {
                                if (creation.scheduled) {
                                    scheduledAlarms++
                                } else {
                                    unscheduledAlarms++
                                }
                            }
                        }
                        if (notes.size < 5) {
                            notes += "created alarm stack of ${creations.size} ${draft.name}".trim()
                        }
                    }

                    pending.events.forEach { draft ->
                        val creation = writer.addEvent(
                            title = draft.title,
                            note = draft.note,
                            date = draft.date,
                            start = draft.start,
                            end = draft.end,
                            withAlarm = draft.withAlarm,
                            tags = draft.tags.map { Tag(id = it.id, name = it.name) },
                            repeat = draft.recurrenceRule(),
                            reminderMinutes = draft.reminderMinutes,
                            endDayOffset = draft.endDayOffset,
                            allDay = draft.allDay,
                        )
                        createdEvents += creation.events.size
                        createdEventAlarms += creation.alarms.size
                        scheduledAlarms += creation.alarms.count { it.scheduled }
                        unscheduledAlarms += creation.alarms.count { !it.scheduled }
                    }

                    alarmUpdates.forEach { (draft, alarm) ->
                        val creation = writer.updateAlarm(
                            alarm = alarm,
                            time = draft.time.toSecondOfDay(),
                            fireDate = draft.fireDate?.toEpochDay(),
                            name = draft.name,
                            note = draft.note,
                            enabled = draft.enabled,
                            oneTime = draft.oneTime,
                            tags = draft.tags.map { Tag(id = it.id, name = it.name) },
                            ringtone = draft.ringtone,
                        )
                        updatedAlarms++
                        if (creation.alarm.enabled) {
                            if (creation.scheduled) scheduledAlarms++ else unscheduledAlarms++
                        }
                        if (notes.size < 5) {
                            notes += "updated alarm ${draft.time} ${draft.name}".trim()
                        }
                    }

                    alarmDeletes.forEach { (draft, alarm) ->
                        writer.deleteAlarm(alarm)
                        deletedAlarms++
                        if (notes.size < 5) {
                            notes += "deleted alarm ${draft.time} ${draft.name}".trim()
                        }
                    }

                    eventUpdates.forEach { (draft, event) ->
                        val mutation = writer.updateEventScoped(
                            event = event,
                            scope = draft.scope,
                            title = draft.title,
                            note = draft.note,
                            date = draft.date,
                            start = draft.start,
                            end = draft.end,
                            withAlarm = draft.withAlarm,
                            tags = draft.tags.map { Tag(id = it.id, name = it.name) },
                            reminderMinutes = draft.reminderMinutes,
                            endDayOffset = draft.endDayOffset,
                            allDay = draft.allDay,
                        )
                        updatedEvents += mutation.affectedEvents
                        deletedAlarms += mutation.deletedAlarms
                        scheduledAlarms += mutation.scheduledAlarms
                        unscheduledAlarms += mutation.unscheduledAlarms
                        if (notes.size < 5) notes += "updated event ${draft.title}".trim()
                    }

                    eventDeletes.forEach { (draft, event) ->
                        val mutation = writer.deleteEventScoped(
                            event = event,
                            scope = draft.scope,
                            deleteAlarms = !draft.keepAlarms,
                        )
                        deletedEvents += mutation.affectedEvents
                        deletedAlarms += mutation.deletedAlarms
                        if (notes.size < 5) notes += "deleted event ${draft.title}".trim()
                    }

                    val totalCreatedAlarms = createdPlainAlarms + createdEventAlarms
                    val actionLine = buildString {
                        append("created ")
                        append(totalCreatedAlarms)
                        append(" alarms, ")
                        append(createdEvents)
                        append(" events; updated ")
                        append(updatedAlarms)
                        append(" alarms, ")
                        append(updatedEvents)
                        append(" events; deleted ")
                        append(deletedAlarms)
                        append(" alarms, ")
                        append(deletedEvents)
                        append(" events")
                        if (notes.isNotEmpty()) {
                            append(": ")
                            append(notes.joinToString("; "))
                        }
                    }
                    ExecutionResult(
                        createdAlarms = totalCreatedAlarms,
                        createdEvents = createdEvents,
                        updatedAlarms = updatedAlarms,
                        updatedEvents = updatedEvents,
                        deletedAlarms = deletedAlarms,
                        deletedEvents = deletedEvents,
                        unscheduledAlarms = unscheduledAlarms,
                        failed = 0,
                        actionLine = actionLine,
                    )
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { restoreAlarmsAfterFailedProposal() }
                throw e
            } catch (e: Exception) {
                restoreAlarmsAfterFailedProposal()
                throw e
            }
            result
        }

    /**
     * A failed proposal rolls the database back, but AlarmManager calls made inside the
     * transaction are not transactional. Cancel and rebuild every system alarm from the restored
     * rows so a stale PendingIntent cannot ring with pre-rollback data.
     */
    private suspend fun restoreAlarmsAfterFailedProposal() {
        runCatching {
            val alarms = alarmDao.getAll()
            alarms.forEach { alarm -> AlarmScheduler.cancelPending(app, alarm) }
            AlarmScheduler.rescheduleAllEnabled(app)
            ReminderScheduler.rescheduleAllUpcoming(app)
        }
    }

    private fun summarize(result: ExecutionResult): String {
        val parts = mutableListOf<String>()
        if (result.createdAlarms > 0) {
            parts += app.getString(R.string.ai_summary_created_alarms, result.createdAlarms)
        }
        if (result.createdEvents > 0) {
            parts += app.getString(R.string.ai_summary_created_events, result.createdEvents)
        }
        if (result.updatedAlarms > 0) {
            parts += app.getString(R.string.ai_summary_updated_alarms, result.updatedAlarms)
        }
        if (result.updatedEvents > 0) {
            parts += app.getString(R.string.ai_summary_updated_events, result.updatedEvents)
        }
        if (result.deletedAlarms > 0) {
            parts += app.getString(R.string.ai_summary_deleted_alarms, result.deletedAlarms)
        }
        if (result.deletedEvents > 0) {
            parts += app.getString(R.string.ai_summary_deleted_events, result.deletedEvents)
        }
        if (result.failed > 0) {
            parts += app.getString(R.string.ai_summary_failed, result.failed)
        }
        if (result.unscheduledAlarms > 0) {
            parts += app.getString(R.string.ai_summary_unscheduled, result.unscheduledAlarms)
        }
        if (parts.isEmpty()) return app.getString(R.string.ai_created_nothing)
        return app.getString(R.string.ai_summary_done, parts.joinToString("；"))
    }

    private fun detailSuffix(detail: String?): String {
        val cleaned = detail?.trim()?.take(240)
        return if (cleaned.isNullOrEmpty()) {
            ""
        } else {
            "\n" + app.getString(R.string.ai_error_detail, cleaned)
        }
    }

    private fun markProposal(id: Long, state: ProposalState) {
        updateProposalItem(id) { it.copy(state = state) }
    }

    private fun updateProposalItem(
        id: Long,
        transform: (AiChatItem.Proposal) -> AiChatItem.Proposal,
    ) {
        val index = items.indexOfFirst { it is AiChatItem.Proposal && it.id == id }
        if (index < 0) return
        items[index] = transform(items[index] as AiChatItem.Proposal)
    }

    private fun recordUsage(value: AiUsage) {
        chatSession.recordUsage(value)
        usage = chatSession.usage
        requestCount = chatSession.requestCount
    }

    private fun addNotice(text: String) {
        items += AiChatItem.Notice(nextItemId(), text)
    }

    private fun addError(text: String, retryable: Boolean) {
        val id = nextItemId()
        items += AiChatItem.Error(id, text, retryable)
        lastErrorId = id
    }

    private fun nextItemId(): Long = nextItemId++

    override fun onCleared() {
        thumbnails.clear()
    }

    private data class PendingWrite(
        val proposalId: Long,
        val userText: String,
        val alarms: List<AlarmDraft>,
        val alarmStacks: List<AlarmStackDraft>,
        val events: List<EventDraft>,
        val alarmUpdates: List<AlarmUpdateDraft>,
        val alarmDeletes: List<AlarmDeleteDraft>,
        val eventUpdates: List<EventUpdateDraft>,
        val eventDeletes: List<EventDeleteDraft>,
        val reasoningContent: String?,
        val dynamicContext: String?,
    )

    private data class ExecutionResult(
        val createdAlarms: Int,
        val createdEvents: Int,
        val updatedAlarms: Int,
        val updatedEvents: Int,
        val deletedAlarms: Int,
        val deletedEvents: Int,
        val unscheduledAlarms: Int,
        val failed: Int,
        val actionLine: String,
    )
}
