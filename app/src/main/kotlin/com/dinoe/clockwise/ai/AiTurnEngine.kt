package com.dinoe.clockwise.ai

import com.dinoe.clockwise.data.Alarm
import com.dinoe.clockwise.data.AlarmTagCrossRef
import com.dinoe.clockwise.data.AlarmDao
import com.dinoe.clockwise.data.Event
import com.dinoe.clockwise.data.EventDao
import com.dinoe.clockwise.data.EventTagCrossRef
import com.dinoe.clockwise.data.Tag
import com.dinoe.clockwise.data.TagDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class TurnStatus {
    THINKING,
    READING_ALARMS,
    READING_EVENTS,
    READING_RINGTONES,
    CORRECTING,
}

enum class TurnNotice {
    HISTORY_TRIMMED,
    CORRECTING_RETRY,
}

enum class TurnReadTool {
    ALARMS,
    EVENTS,
    RINGTONES,
}

/**
 * Events of one turn, in the order the engine produces them. The collector translates these into
 * UI state and strings; the engine itself never touches Android resources.
 */
sealed interface TurnEvent {
    data class Status(val status: TurnStatus) : TurnEvent

    data class AssistantText(val text: String) : TurnEvent

    data class Notice(val notice: TurnNotice) : TurnEvent

    /** A read tool answered with [shown] of [total] rows. */
    data class ReadResult(val tool: TurnReadTool, val shown: Int, val total: Int) : TurnEvent

    /** Usage of one model request; the collector aggregates across rounds. */
    data class UsageDelta(val usage: AiUsage) : TurnEvent

    data class ProposalReady(
        val drafts: PreparedDrafts,
        val reasoningContent: String?,
        val dynamicContext: String?,
    ) : TurnEvent

    data class Finished(
        val answer: String,
        val reasoningContent: String?,
        val readSummary: String?,
        val dynamicContext: String?,
    ) : TurnEvent

    data class Failed(val failure: TurnFailure) : TurnEvent
}

sealed interface TurnFailure {
    data class Api(val kind: AiErrorKind, val detail: String?) : TurnFailure

    data object ContextOverflow : TurnFailure

    data object RoundsExhausted : TurnFailure

    data class Unknown(val detail: String?) : TurnFailure
}

/** The validated write batch a [TurnEvent.ProposalReady] event carries. */
data class PreparedDrafts(
    val alarms: List<AlarmDraft>,
    val alarmStacks: List<AlarmStackDraft>,
    val events: List<EventDraft>,
    val alarmUpdates: List<AlarmUpdateDraft>,
    val alarmDeletes: List<AlarmDeleteDraft>,
    val eventUpdates: List<EventUpdateDraft>,
    val eventDeletes: List<EventDeleteDraft>,
)

/**
 * Runs one chat turn: the model/tool-call loop, read tools, write-call validation and the
 * "every tool call gets a TOOL result" wire invariant. Pure Kotlin at the edges — the model
 * request, the ringtones list and the clock arrive as injected functions, and Room's DAO
 * interfaces are injected directly — so the whole loop is testable on the JVM.
 *
 * Turn state ([currentTurn] and the frozen dynamic tail) lives here. The dynamic tail is built
 * once per turn and reused across rounds and by [resumeTurn], so every request of a turn carries
 * the same CURRENT CONTEXT block; [runTurn] and [discardPendingTurn] clear it. A turn that ends
 * with [TurnEvent.ProposalReady] keeps its unanswered tool calls suspended; the collector must
 * call [discardPendingTurn] once the proposal is applied, cancelled or stopped. [runTurn]
 * refuses to start while such a turn is pending, so a stale history can never be sent to the
 * provider.
 */
class AiTurnEngine(
    private val tagDao: TagDao,
    private val alarmDao: AlarmDao,
    private val eventDao: EventDao,
    private val complete: suspend (
        credentials: AiTurnCredentials,
        messages: List<ChatMessage>,
        maxTokens: Int,
    ) -> ChatCompletion,
    private val ringtones: () -> List<Pair<String, String>>,
    private val now: () -> ZonedDateTime = ZonedDateTime::now,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val contextBuilder: AiContextBuilder = AiContextBuilder(),
) {
    private var currentTurn = mutableListOf<ChatMessage>()
    private var trimNoticeShown = false
    private var readSummary: String? = null
    private var frozenDynamicContext: String? = null
    private var phase = TurnPhase.IDLE

    private enum class TurnPhase {
        /** No turn in progress. */
        IDLE,

        /** A proposal is pending; the turn's tool calls are unanswered. */
        SUSPENDED,

        /** The last turn failed; [resumeTurn] may continue it, [runTurn] may replace it. */
        FAILED,
    }

    /**
     * Starts a new turn from [userMessage]. Fails while a proposal is still pending —
     * confirm/cancel/stop must have called [discardPendingTurn] first. A failed turn is
     * replaced silently, matching "send a new message after an error".
     */
    fun runTurn(
        credentials: AiTurnCredentials,
        userMessage: ChatMessage,
        completedTurns: List<CompletedTurn>,
        actionLines: List<String>,
    ): Flow<TurnEvent> = flow {
        check(phase != TurnPhase.SUSPENDED) {
            "a proposal is still pending; call discardPendingTurn() before starting a new turn"
        }
        phase = TurnPhase.IDLE
        trimNoticeShown = false
        readSummary = null
        frozenDynamicContext = null
        currentTurn.clear()
        currentTurn += userMessage
        emitLoop(credentials, completedTurns, actionLines)
    }

    /**
     * Continues the turn kept after [TurnEvent.Failed]: the accumulated messages (the user
     * message, or the tool rounds up to the failure) are reused, so the model sees its own
     * earlier tool results instead of starting over.
     */
    fun resumeTurn(
        credentials: AiTurnCredentials,
        completedTurns: List<CompletedTurn>,
        actionLines: List<String>,
    ): Flow<TurnEvent> = flow {
        check(phase == TurnPhase.FAILED && currentTurn.isNotEmpty()) { "no failed turn to resume" }
        phase = TurnPhase.IDLE
        emitLoop(credentials, completedTurns, actionLines)
    }

    /** Drops a suspended or failed turn. Required before the next [runTurn]. */
    fun discardPendingTurn() {
        phase = TurnPhase.IDLE
        currentTurn.clear()
        frozenDynamicContext = null
    }

    private suspend fun FlowCollector<TurnEvent>.emitLoop(
        credentials: AiTurnCredentials,
        completedTurns: List<CompletedTurn>,
        actionLines: List<String>,
    ) {
        try {
            runLoop(credentials, completedTurns, actionLines)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiApiException) {
            phase = TurnPhase.FAILED
            emit(TurnEvent.Failed(TurnFailure.Api(e.kind, e.detail)))
        } catch (e: AiContextOverflowException) {
            phase = TurnPhase.FAILED
            emit(TurnEvent.Failed(TurnFailure.ContextOverflow))
        } catch (e: Exception) {
            phase = TurnPhase.FAILED
            emit(TurnEvent.Failed(TurnFailure.Unknown(e.message)))
        }
    }

    private suspend fun FlowCollector<TurnEvent>.runLoop(
        credentials: AiTurnCredentials,
        completedTurns: List<CompletedTurn>,
        actionLines: List<String>,
    ) {
        val provider = credentials.provider
        var rounds = 0
        while (rounds < AiTokenBudget.MAX_TOOL_ROUNDS) {
            rounds++
            val tags = tagDao.getAll()
            val turnMessages = currentTurn.toList()
            val modelContextLength = provider.models
                .firstOrNull { it.id == provider.modelId }
                ?.contextLength
            // Prompt assembly, history trimming and token estimation are pure CPU work over
            // immutable snapshots, so they stay off the collector's dispatcher. The dynamic tail
            // is frozen on its first build so every request of the turn stays byte-identical.
            val built = withContext(defaultDispatcher) {
                val dynamic = frozenDynamicContext ?: AiPrompt.buildDynamicContext(
                    now = now(),
                    tags = tags.map { it.name },
                    actions = actionLines,
                ).also { frozenDynamicContext = it }
                contextBuilder.build(
                    completedTurns = completedTurns,
                    currentTurn = turnMessages,
                    dynamicContext = dynamic,
                    modelContextLength = modelContextLength,
                )
            }
            if (built.trimmed && !trimNoticeShown) {
                emit(TurnEvent.Notice(TurnNotice.HISTORY_TRIMMED))
                trimNoticeShown = true
            }

            emit(TurnEvent.Status(TurnStatus.THINKING))
            // Do not ask a small-context model for 4096 output tokens; that combination can
            // exceed its context even when the input budget is respected.
            val maxOutputTokens = modelContextLength?.let { context ->
                (context - built.estimatedInputTokens - 256L)
                    .coerceIn(256L, 4_096L)
                    .toInt()
            } ?: 4_096
            val completion = complete(credentials, built.messages, maxOutputTokens)
            emit(TurnEvent.UsageDelta(completion.usage))

            val assistantText = completion.content?.trim().orEmpty()
            if (completion.toolCalls.isEmpty()) {
                currentTurn.clear()
                val summary = readSummary
                readSummary = null
                phase = TurnPhase.IDLE
                emit(
                    TurnEvent.Finished(
                        answer = assistantText,
                        reasoningContent = completion.reasoningContent,
                        readSummary = summary,
                        dynamicContext = frozenDynamicContext,
                    ),
                )
                return
            }

            currentTurn += ChatMessage(
                role = ChatRole.ASSISTANT,
                content = completion.content,
                toolCalls = completion.toolCalls,
                reasoningContent = completion.reasoningContent?.let {
                    AiTokenBudget.truncateChars(it, AiTokenBudget.MAX_TURN_REASONING_CHARS)
                },
            )
            if (assistantText.isNotEmpty()) {
                emit(TurnEvent.AssistantText(assistantText))
            }

            val readCalls = completion.toolCalls.filter { it.name in AiTools.readTools }
            val writeCalls = completion.toolCalls.filter { it.name in AiTools.writeTools }
            val unknownCalls = completion.toolCalls.filter {
                it.name !in AiTools.readTools && it.name !in AiTools.writeTools
            }

            // Tool payloads already carried by this turn count against the aggregate budget, so a
            // later read is trimmed instead of pushing the request over its input budget.
            var toolResultTokens = contextBuilder.estimateToolResultTokens(currentTurn)
            for (call in readCalls) {
                emit(
                    TurnEvent.Status(
                        when (call.name) {
                            AiTools.LIST_ALARMS -> TurnStatus.READING_ALARMS
                            AiTools.LIST_EVENTS -> TurnStatus.READING_EVENTS
                            AiTools.LIST_RINGTONES -> TurnStatus.READING_RINGTONES
                            else -> TurnStatus.THINKING
                        },
                    ),
                )
                val result = executeReadTool(
                    call = call,
                    tags = tags,
                    maxTokens = AiTokenBudget.toolResultAllowance(toolResultTokens),
                )
                toolResultTokens += AiTokenBudget.estimateTokens(result.body)
                currentTurn += ChatMessage(
                    role = ChatRole.TOOL,
                    content = result.body,
                    toolCallId = call.id,
                )
                result.summary?.let { summary -> readSummary = summary }
                result.read?.let { read ->
                    emit(TurnEvent.ReadResult(read.tool, read.shown, read.total))
                }
            }
            val unknownResults = withContext(defaultDispatcher) {
                unknownCalls.map { call ->
                    ToolCallResult(
                        callId = call.id,
                        content = errorToolResult("unknown_tool", "Unknown tool ${call.name}"),
                    )
                }
            }
            unknownResults.forEach { result ->
                toolResultTokens += AiTokenBudget.estimateTokens(result.content)
                currentTurn += ChatMessage(
                    role = ChatRole.TOOL,
                    content = result.content,
                    toolCallId = result.callId,
                )
            }

            if (writeCalls.isNotEmpty()) {
                emit(TurnEvent.Status(TurnStatus.CORRECTING))
                // Row loading, argument parsing, validation and rejection payloads are CPU and
                // JSON work; only the resulting state changes below touch the collector.
                val outcome = withContext(defaultDispatcher) {
                    prepareWriteCalls(writeCalls, tags)
                }
                when (outcome) {
                    is WriteOutcome.Rejected -> {
                        outcome.results.forEach { result ->
                            currentTurn += ChatMessage(
                                role = ChatRole.TOOL,
                                content = result.content,
                                toolCallId = result.callId,
                            )
                        }
                        emit(TurnEvent.Notice(TurnNotice.CORRECTING_RETRY))
                        continue
                    }

                    is WriteOutcome.Ready -> {
                        emit(
                            TurnEvent.ProposalReady(
                                drafts = outcome.drafts,
                                reasoningContent = completion.reasoningContent?.let {
                                    AiTokenBudget.truncateChars(
                                        it,
                                        AiTokenBudget.COMPLETED_REASONING_CHARS,
                                    )
                                },
                                dynamicContext = frozenDynamicContext,
                            ),
                        )
                        // The turn stays suspended here on purpose: the assistant message above
                        // carries unanswered tool calls, and the proposal flow (confirm/cancel)
                        // settles it via discardPendingTurn().
                        phase = TurnPhase.SUSPENDED
                        return
                    }
                }
            }
        }
        phase = TurnPhase.FAILED
        emit(TurnEvent.Failed(TurnFailure.RoundsExhausted))
    }

    /**
     * Loads one page of the requested rows and builds the tool payload off the collector's
     * dispatcher. Ordering, range filtering and the row cap happen in SQL, tag references are
     * fetched only for the returned ids, and the payload is trimmed to the remaining token
     * budget before the caller appends it.
     */
    private suspend fun executeReadTool(
        call: ChatToolCall,
        tags: List<Tag>,
        maxTokens: Int,
    ): ReadToolResult =
        withContext(defaultDispatcher) {
            when (call.name) {
                AiTools.LIST_ALARMS -> readAlarms(tags, maxTokens)
                AiTools.LIST_EVENTS -> readEvents(call.arguments, tags, maxTokens)
                AiTools.LIST_RINGTONES -> readRingtones(maxTokens)
                else -> ReadToolResult(
                    body = errorToolResult("unknown_tool", "Unknown tool ${call.name}"),
                    read = null,
                    summary = null,
                )
            }
        }

    private suspend fun readAlarms(tags: List<Tag>, maxTokens: Int): ReadToolResult {
        val total = alarmDao.count()
        val page = alarmDao.getOrderedPage(READ_RESULT_LIMIT)
        val tagsById = tags.associateBy { it.id }
        val tagNames = alarmTagRefsById(page.map { it.id }, tagsById)
            .mapValues { (_, refs) -> refs.map { ref -> ref.name } }
        val shown = AiToolPayload.fittingPrefix(page, maxTokens) { candidate ->
            AiTokenBudget.estimateTokens(alarmsPayload(total, candidate, tagNames))
        }
        return ReadToolResult(
            body = alarmsPayload(total, shown, tagNames),
            read = ReadSummaryInfo(TurnReadTool.ALARMS, shown.size, total),
            summary = "alarms ($total)",
        )
    }

    private fun readRingtones(maxTokens: Int): ReadToolResult {
        val all = ringtones()
        val shown = AiToolPayload.fittingPrefix(all, maxTokens) { candidate ->
            AiTokenBudget.estimateTokens(ringtonesPayload(all.size, candidate))
        }
        return ReadToolResult(
            body = ringtonesPayload(all.size, shown),
            read = ReadSummaryInfo(TurnReadTool.RINGTONES, shown.size, all.size),
            summary = "ringtones (${all.size})",
        )
    }

    private suspend fun readEvents(
        arguments: String,
        tags: List<Tag>,
        maxTokens: Int,
    ): ReadToolResult {
        val range = try {
            parseEventRange(arguments)
        } catch (e: Exception) {
            return ReadToolResult(
                body = errorToolResult("invalid_range", e.message ?: "invalid date range"),
                read = null,
                summary = null,
            )
        }
        val from = range.first.toEpochDay()
        val to = range.second.toEpochDay()
        val total = eventDao.countBetween(from, to)
        val page = eventDao.getBetweenPage(from, to, READ_RESULT_LIMIT)
        val tagsById = tags.associateBy { it.id }
        val pageIds = page.map { it.id }
        val alarmEventIds = AiIdChunks.of(pageIds)
            .flatMap { alarmDao.getLinkedEventIdsFor(it) }
            .toSet()
        val tagNames = eventTagRefsById(pageIds, tagsById)
            .mapValues { (_, refs) -> refs.map { ref -> ref.name } }
        val shown = AiToolPayload.fittingPrefix(page, maxTokens) { candidate ->
            AiTokenBudget.estimateTokens(
                eventsPayload(total, candidate, alarmEventIds, tagNames),
            )
        }
        return ReadToolResult(
            body = eventsPayload(total, shown, alarmEventIds, tagNames),
            read = ReadSummaryInfo(TurnReadTool.EVENTS, shown.size, total),
            summary = "events ${range.first}..${range.second} ($total)",
        )
    }

    private fun alarmsPayload(
        total: Int,
        shown: List<Alarm>,
        tagNames: Map<Long, List<String>>,
    ): String = buildJsonObject {
        put("total", total)
        put("truncated", total > shown.size)
        putJsonArray("alarms") {
            shown.forEach { alarm ->
                add(
                    buildJsonObject {
                        put("id", alarm.id)
                        put("time", formatSeconds(alarm.time))
                        alarm.fireDate?.let { fireDate ->
                            put("date", LocalDate.ofEpochDay(fireDate).toString())
                        }
                        put("enabled", alarm.enabled)
                        put("oneTime", alarm.oneTime)
                        put("name", AiTokenBudget.truncateChars(alarm.name, 80))
                        if (alarm.note.isNotBlank()) {
                            put("note", AiTokenBudget.truncateChars(alarm.note, 120))
                        }
                        put("fromEvent", alarm.eventId != null)
                        alarm.eventId?.let { put("eventId", it) }
                        putJsonArray("tags") {
                            tagNames[alarm.id].orEmpty().forEach { add(it) }
                        }
                    },
                )
            }
        }
    }.toString()

    private fun ringtonesPayload(
        total: Int,
        shown: List<Pair<String, String>>,
    ): String = buildJsonObject {
        put("total", total)
        put("truncated", total > shown.size)
        putJsonArray("ringtones") {
            shown.forEach { (title, uri) ->
                add(
                    buildJsonObject {
                        put("title", AiTokenBudget.truncateChars(title, 80))
                        put("uri", uri)
                    },
                )
            }
        }
    }.toString()

    private fun eventsPayload(
        total: Int,
        shown: List<Event>,
        alarmEventIds: Set<Long>,
        tagNames: Map<Long, List<String>>,
    ): String = buildJsonObject {
        put("total", total)
        put("truncated", total > shown.size)
        putJsonArray("events") {
            shown.forEach { event ->
                add(
                    buildJsonObject {
                        put("id", event.id)
                        put("date", LocalDate.ofEpochDay(event.date).toString())
                        if (event.allDay) {
                            put("allDay", true)
                        } else {
                            put("start", formatSeconds(event.start))
                            if (event.hasEndTime) {
                                put("end", formatSeconds(event.endTimeOfDay))
                                if (event.endDayOffset > 0) {
                                    put(
                                        "endDate",
                                        LocalDate.ofEpochDay(event.endDateEpochDay).toString(),
                                    )
                                }
                            }
                        }
                        put("title", AiTokenBudget.truncateChars(event.title, 80))
                        if (event.note.isNotBlank()) {
                            put("note", AiTokenBudget.truncateChars(event.note, 120))
                        }
                        put("repeat", repeatLabel(event.repeatType))
                        if (event.repeatType == Event.REPEAT_WEEKLY) {
                            putJsonArray("repeatDays") {
                                event.repeatDays.split(',')
                                    .mapNotNull { it.trim().toIntOrNull() }
                                    .forEach { add(it) }
                            }
                        }
                        put("hasAlarm", event.id in alarmEventIds)
                        put("reminderMinutes", event.reminderMinutes)
                        putJsonArray("tags") {
                            tagNames[event.id].orEmpty().forEach { add(it) }
                        }
                    },
                )
            }
        }
    }.toString()

    private fun parseEventRange(arguments: String): Pair<LocalDate, LocalDate> {
        val root = runCatching {
            Json.parseToJsonElement(arguments).jsonObject
        }.getOrNull() ?: JsonObject(emptyMap())
        val from = root.dateArg("from") ?: now().toLocalDate()
        val to = root.dateArg("to") ?: from.plusDays(7)
        if (to.isBefore(from)) throw IllegalArgumentException("to must not be before from")
        if (ChronoUnit.DAYS.between(from, to) > MAX_READ_RANGE_DAYS) {
            throw IllegalArgumentException("date range must be 366 days or fewer")
        }
        return from to to
    }

    private fun JsonObject.dateArg(key: String): LocalDate? {
        val raw = (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return try {
            LocalDate.parse(raw)
        } catch (_: Exception) {
            throw IllegalArgumentException("$key must be YYYY-MM-DD")
        }
    }

    /**
     * Parses the write tool calls and validates the whole batch. Runs on [defaultDispatcher],
     * so it only reads database rows and returns immutable drafts.
     */
    private suspend fun prepareWriteCalls(
        calls: List<ChatToolCall>,
        tags: List<Tag>,
    ): WriteOutcome {
        val tagsById = tags.associateBy { it.id }
        val rows = loadWriteRows(AiToolReferences.collect(calls), tagsById)
        val parser = AiToolParser(
            now = now().toLocalDateTime(),
            availableTags = tags.associate { it.name to it.id },
            alarmsById = rows.alarmsById,
            eventsById = rows.eventsById,
            alarmTagsById = rows.alarmTagsById,
            eventTagsById = rows.eventTagsById,
            availableRingtones = ringtones().map { it.second }.toSet(),
        )
        val alarms = mutableListOf<AlarmDraft>()
        val alarmStacks = mutableListOf<AlarmStackDraft>()
        val events = mutableListOf<EventDraft>()
        val alarmUpdates = mutableListOf<AlarmUpdateDraft>()
        val alarmDeletes = mutableListOf<AlarmDeleteDraft>()
        val eventUpdates = mutableListOf<EventUpdateDraft>()
        val eventDeletes = mutableListOf<EventDeleteDraft>()
        val parseErrors = mutableMapOf<String, AiToolParseException>()

        for (call in calls) {
            try {
                when (call.name) {
                    AiTools.CREATE_ALARMS -> alarms += parser.parseAlarms(call.arguments)
                    AiTools.CREATE_ALARM_STACKS ->
                        alarmStacks += parser.parseAlarmStacks(call.arguments)

                    AiTools.CREATE_EVENTS -> events += parser.parseEvents(call.arguments)
                    AiTools.UPDATE_ALARMS ->
                        alarmUpdates += parser.parseAlarmUpdates(call.arguments)

                    AiTools.DELETE_ALARMS ->
                        alarmDeletes += parser.parseAlarmDeletes(call.arguments)

                    AiTools.UPDATE_EVENTS ->
                        eventUpdates += parser.parseEventUpdates(call.arguments)

                    AiTools.DELETE_EVENTS ->
                        eventDeletes += parser.parseEventDeletes(call.arguments)
                }
            } catch (e: AiToolParseException) {
                parseErrors[call.id] = e
            }
        }

        if (parseErrors.isNotEmpty()) {
            return WriteOutcome.Rejected(
                calls.map { call ->
                    val error = parseErrors[call.id]
                    ToolCallResult(
                        callId = call.id,
                        content = errorToolResult(
                            code = error?.code ?: "batch_rejected",
                            message = error?.let { "${it.code}: ${it.message}" }
                                ?: "batch_rejected: another item in this batch was invalid",
                        ),
                    )
                },
            )
        }

        val totalActions = alarms.size + events.size +
            alarmStacks.sumOf { it.times.size } +
            alarmUpdates.size + alarmDeletes.size +
            eventUpdates.size + eventDeletes.size
        if (totalActions == 0) {
            return rejectWriteCalls(calls, "empty", "there are no changes to apply")
        }
        if (totalActions > AiTokenBudget.MAX_BATCH_ITEMS) {
            return rejectWriteCalls(
                calls,
                "too_many_items",
                "at most ${AiTokenBudget.MAX_BATCH_ITEMS} actions are allowed per proposal",
            )
        }

        val affectedOccurrences = events.sumOf { it.occurrenceCount } +
            eventUpdates.sumOf { it.affectedCount } +
            eventDeletes.sumOf { it.affectedCount }
        if (affectedOccurrences > AiTokenBudget.MAX_TOTAL_AFFECTED) {
            return rejectWriteCalls(
                calls,
                "too_many_occurrences",
                "this proposal would affect $affectedOccurrences event occurrences; " +
                    "keep the total under ${AiTokenBudget.MAX_TOTAL_AFFECTED}",
            )
        }

        val updateAlarmIds = alarmUpdates.map { it.alarmId }
        val deleteAlarmIds = alarmDeletes.map { it.alarmId }
        val updateEventIds = eventUpdates.map { it.eventId }
        val deleteEventIds = eventDeletes.map { it.eventId }
        val conflict = updateAlarmIds.any { it in deleteAlarmIds } ||
            updateEventIds.any { it in deleteEventIds } ||
            updateAlarmIds.size != updateAlarmIds.distinct().size ||
            deleteAlarmIds.size != deleteAlarmIds.distinct().size ||
            updateEventIds.size != updateEventIds.distinct().size ||
            deleteEventIds.size != deleteEventIds.distinct().size
        if (conflict) {
            return rejectWriteCalls(
                calls,
                "conflicting_actions",
                "the same alarm or event cannot be changed and deleted, or changed twice, " +
                    "in one proposal",
            )
        }

        return WriteOutcome.Ready(
            PreparedDrafts(
                alarms = alarms,
                alarmStacks = alarmStacks,
                events = events,
                alarmUpdates = alarmUpdates,
                alarmDeletes = alarmDeletes,
                eventUpdates = eventUpdates,
                eventDeletes = eventDeletes,
            ),
        )
    }

    private fun rejectWriteCalls(
        calls: List<ChatToolCall>,
        code: String,
        message: String,
    ): WriteOutcome.Rejected = WriteOutcome.Rejected(
        calls.map { call ->
            ToolCallResult(callId = call.id, content = errorToolResult(code, message))
        },
    )

    /**
     * Loads only the rows a write batch can reference: the ids parsed out of the calls, the whole
     * series of each referenced event (scope expansion needs the siblings) and the alarms linked
     * to those events. Every `IN (...)` lookup is chunked because SQLite bounds statement
     * parameters.
     */
    private suspend fun loadWriteRows(
        references: ReferencedIds,
        tagsById: Map<Long, Tag>,
    ): WriteRows {
        val referencedEvents = AiIdChunks.of(references.eventIds)
            .flatMap { eventDao.getByIds(it) }
        val seriesIds = referencedEvents.map { it.seriesId }.filter { it != 0L }
        val seriesEvents = AiIdChunks.of(seriesIds)
            .flatMap { eventDao.getBySeriesIds(it) }
        val eventsById = (seriesEvents + referencedEvents).associateBy { it.id }
        val eventIds = eventsById.keys.toList()
        val alarmsById = (
            AiIdChunks.of(references.alarmIds).flatMap { alarmDao.getByIds(it) } +
                AiIdChunks.of(eventIds).flatMap { alarmDao.getByEventIds(it) }
            ).associateBy { it.id }
        return WriteRows(
            alarmsById = alarmsById,
            eventsById = eventsById,
            alarmTagsById = alarmTagRefsById(alarmsById.keys.toList(), tagsById),
            eventTagsById = eventTagRefsById(eventIds, tagsById),
        )
    }

    private suspend fun alarmTagRefsById(
        ids: List<Long>,
        tagsById: Map<Long, Tag>,
    ): Map<Long, List<TagRef>> = AiIdChunks.of(ids)
        .flatMap { tagDao.getAlarmTagRefsFor(it) }
        .groupBy { it.alarmId }
        .mapValues { (_, refs) -> refs.toAlarmTagRefs(tagsById) }

    private suspend fun eventTagRefsById(
        ids: List<Long>,
        tagsById: Map<Long, Tag>,
    ): Map<Long, List<TagRef>> = AiIdChunks.of(ids)
        .flatMap { tagDao.getEventTagRefsFor(it) }
        .groupBy { it.eventId }
        .mapValues { (_, refs) -> refs.toEventTagRefs(tagsById) }

    private fun List<AlarmTagCrossRef>.toAlarmTagRefs(
        tagsById: Map<Long, Tag>,
    ): List<TagRef> = mapNotNull { tagsById[it.tagId] }.map { TagRef(id = it.id, name = it.name) }

    private fun List<EventTagCrossRef>.toEventTagRefs(
        tagsById: Map<Long, Tag>,
    ): List<TagRef> = mapNotNull { tagsById[it.tagId] }.map { TagRef(id = it.id, name = it.name) }

    private fun errorToolResult(code: String, message: String): String = buildJsonObject {
        put("ok", false)
        put("error", code)
        put("message", AiTokenBudget.truncateChars(message, 300))
    }.toString()

    private fun formatSeconds(seconds: Int): String =
        LocalTime.ofSecondOfDay(seconds.toLong()).format(TIME_FORMATTER)

    private fun repeatLabel(repeatType: Int): String = when (repeatType) {
        Event.REPEAT_DAILY -> "daily"
        Event.REPEAT_WEEKLY -> "weekly"
        else -> "none"
    }

    private data class ReadSummaryInfo(
        val tool: TurnReadTool,
        val shown: Int,
        val total: Int,
    )

    private data class ReadToolResult(
        val body: String,
        val read: ReadSummaryInfo?,
        val summary: String?,
    )

    /** One tool result payload, built off Main and appended to the turn. */
    private data class ToolCallResult(
        val callId: String,
        val content: String,
    )

    private sealed interface WriteOutcome {
        data class Rejected(val results: List<ToolCallResult>) : WriteOutcome

        data class Ready(val drafts: PreparedDrafts) : WriteOutcome
    }

    /** The subset of rows a write batch can reference, loaded before parsing. */
    private data class WriteRows(
        val alarmsById: Map<Long, Alarm>,
        val eventsById: Map<Long, Event>,
        val alarmTagsById: Map<Long, List<TagRef>>,
        val eventTagsById: Map<Long, List<TagRef>>,
    )

    private companion object {
        const val READ_RESULT_LIMIT = 30
        const val MAX_READ_RANGE_DAYS = 366L
        val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    }
}
