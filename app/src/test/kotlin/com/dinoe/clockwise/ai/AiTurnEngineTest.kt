package com.dinoe.clockwise.ai

import com.dinoe.clockwise.data.Alarm
import com.dinoe.clockwise.data.AlarmDao
import com.dinoe.clockwise.data.AlarmTagCrossRef
import com.dinoe.clockwise.data.Event
import com.dinoe.clockwise.data.EventDao
import com.dinoe.clockwise.data.EventTagCrossRef
import com.dinoe.clockwise.data.Tag
import com.dinoe.clockwise.data.TagDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * JVM tests for the turn engine: the tool-call feedback invariant, the write-batch rules and the
 * proposal suspension contract. The model request and the ringtones arrive as fakes, so no
 * Android or network code runs.
 */
class AiTurnEngineTest {
    private val credentials = AiTurnCredentials(
        provider = com.dinoe.clockwise.ai.AiProviderConfig(
            preset = AiProviders.deepSeek,
            hasKey = true,
        ),
        apiKey = "key",
    )

    private class ScriptedCompleter(
        vararg responses: ChatCompletion,
        private val onRequest: () -> Unit = {},
    ) {
        private val queue = ArrayDeque(responses.toList())
        val requests = mutableListOf<List<ChatMessage>>()

        suspend fun complete(
            credentials: AiTurnCredentials,
            messages: List<ChatMessage>,
            maxTokens: Int,
        ): ChatCompletion {
            requests += messages
            onRequest()
            return queue.removeFirstOrNull() ?: error("no scripted response for request")
        }
    }

    private fun engine(
        completer: ScriptedCompleter,
        alarms: List<Alarm> = emptyList(),
        tags: List<Tag> = emptyList(),
        now: () -> java.time.ZonedDateTime = { FIXED_NOW },
    ): AiTurnEngine = AiTurnEngine(
        tagDao = FakeTagDao(tags),
        alarmDao = FakeAlarmDao(alarms),
        eventDao = FakeEventDao(),
        complete = completer::complete,
        ringtones = { emptyList() },
        now = now,
    )

    private fun turn(engine: AiTurnEngine, completer: ScriptedCompleter): Flow<TurnEvent> =
        engine.runTurn(
            credentials = credentials,
            userMessage = ChatMessage(role = ChatRole.USER, content = "hi"),
            completedTurns = emptyList(),
            actionLines = emptyList(),
        ).also { completer.requests.clear() }

    private fun toolEvents(events: List<TurnEvent>): List<TurnEvent.Finished> =
        events.filterIsInstance<TurnEvent.Finished>()

    private fun toolResultsOf(request: List<ChatMessage>): Map<String, Int> =
        request.filter { it.role == ChatRole.TOOL }
            .groupingBy { it.toolCallId ?: "" }
            .eachCount()

    private fun unansweredToolCallIds(request: List<ChatMessage>): List<String> {
        val answered = request.filter { it.role == ChatRole.TOOL }
            .mapNotNull { it.toolCallId }.toSet()
        return request.flatMap { it.toolCalls }
            .filter { it.id !in answered }
            .map { it.id }
    }

    private fun dynamicTailOf(request: List<ChatMessage>): String {
        val lastUser = request.last { it.role == ChatRole.USER }.content.orEmpty()
        val start = lastUser.indexOf("CURRENT CONTEXT")
        return if (start < 0) "" else lastUser.substring(start)
    }

    @Test
    fun everyToolCallGetsExactlyOneToolResultBeforeTheNextRequest() = runTest {
        val completer = ScriptedCompleter(
            ChatCompletion(
                content = "Let me check and set that up.",
                toolCalls = listOf(
                    ChatToolCall("a1", AiTools.LIST_ALARMS, "{}"),
                    ChatToolCall("a2", "bogus_tool", "{}"),
                    ChatToolCall(
                        "a3",
                        AiTools.CREATE_ALARMS,
                        """{"alarms":[{"time":"07:00","name":"Wake"}]}""",
                    ),
                    ChatToolCall(
                        "a4",
                        AiTools.CREATE_ALARMS,
                        """{"alarms":[{"time":"99:00"}]}""",
                    ),
                ),
                finishReason = "tool_calls",
                usage = AiUsage(),
            ),
            ChatCompletion(content = "done", toolCalls = emptyList(), finishReason = "stop", usage = AiUsage()),
        )
        val events = turn(engine(completer), completer).toList()

        // Every call of round 1 — read, unknown and both writes — is answered exactly once.
        val results = toolResultsOf(completer.requests[1])
        assertEquals(listOf("a1", "a2", "a3", "a4"), results.keys.sorted())
        assertTrue(results.values.all { it == 1 })
        // The rejected batch loops once more and then finishes.
        assertEquals(
            listOf(TurnEvent.Notice::class.java, TurnEvent.Finished::class.java),
            events.filterIsInstance<TurnEvent.Notice>()
                .plus(events.filterIsInstance<TurnEvent.Finished>())
                .map { it::class.java },
        )
    }

    @Test
    fun finalAnswerFinishesTheTurnAndAllowsANewOne() = runTest {
        val completer = ScriptedCompleter(
            ChatCompletion(content = "hello", toolCalls = emptyList(), finishReason = "stop", usage = AiUsage()),
            ChatCompletion(content = "again", toolCalls = emptyList(), finishReason = "stop", usage = AiUsage()),
        )
        val testEngine = engine(completer)
        val events = turn(testEngine, completer).toList()

        assertEquals(1, toolEvents(events).size)
        val finished = toolEvents(events).single()
        assertEquals("hello", finished.answer)

        // A finished turn leaves the engine free for the next one.
        turn(testEngine, completer).toList()
    }

    @Test
    fun resumeTurnWithoutAFailedTurnIsRejected() = runTest {
        val completer = ScriptedCompleter(
            ChatCompletion(content = "hello", toolCalls = emptyList(), finishReason = "stop", usage = AiUsage()),
        )
        val testEngine = engine(completer)
        try {
            testEngine.resumeTurn(credentials, emptyList(), emptyList()).toList()
            fail("resumeTurn must reject when no turn failed")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun proposalSuspendsTheTurnUntilDiscarded() = runTest {
        val completer = ScriptedCompleter(
            ChatCompletion(
                content = null,
                toolCalls = listOf(
                    ChatToolCall(
                        "p1",
                        AiTools.CREATE_ALARMS,
                        """{"alarms":[{"time":"07:00"},{"time":"08:30","name":"Run"}]}""",
                    ),
                ),
                finishReason = "tool_calls",
                usage = AiUsage(),
            ),
            ChatCompletion(content = "ok", toolCalls = emptyList(), finishReason = "stop", usage = AiUsage()),
        )
        val testEngine = engine(completer)
        val events = turn(testEngine, completer).toList()

        val ready = events.filterIsInstance<TurnEvent.ProposalReady>().single()
        assertEquals(2, ready.drafts.alarms.size)
        assertEquals(0, ready.drafts.events.size)

        // A suspended turn refuses a new one until the proposal flow settles it.
        try {
            turn(testEngine, completer).toList()
            fail("runTurn must refuse while a proposal is pending")
        } catch (_: IllegalStateException) {
        }

        testEngine.discardPendingTurn()
        val next = turn(testEngine, completer).toList()
        assertEquals(1, toolEvents(next).size)
    }

    @Test
    fun conflictingUpdateAndDeleteRejectsTheWholeBatch() = runTest {
        val completer = ScriptedCompleter(
            ChatCompletion(
                content = null,
                toolCalls = listOf(
                    ChatToolCall(
                        "u1",
                        AiTools.UPDATE_ALARMS,
                        """{"alarms":[{"id":1,"name":"Renamed"}]}""",
                    ),
                    ChatToolCall("d1", AiTools.DELETE_ALARMS, """{"alarmIds":[1]}"""),
                ),
                finishReason = "tool_calls",
                usage = AiUsage(),
            ),
            ChatCompletion(content = "ok", toolCalls = emptyList(), finishReason = "stop", usage = AiUsage()),
        )
        val alarms = listOf(Alarm(id = 1, time = 25_200, name = "Wake"))
        val events = turn(engine(completer, alarms = alarms), completer).toList()

        assertTrue(events.any { it is TurnEvent.Notice && it.notice == TurnNotice.CORRECTING_RETRY })
        val results = toolResultsOf(completer.requests[1])
        assertEquals(setOf("u1", "d1"), results.keys)
        assertTrue(
            completer.requests[1]
                .first { it.role == ChatRole.TOOL && it.toolCallId == "u1" }
                .content!!.contains("{\"ok\":false"),
        )
    }

    @Test
    fun oneInvalidItemRejectsTheWholeBatch() = runTest {
        val completer = ScriptedCompleter(
            ChatCompletion(
                content = null,
                toolCalls = listOf(
                    ChatToolCall(
                        "v1",
                        AiTools.CREATE_ALARMS,
                        """{"alarms":[{"time":"07:00"}]}""",
                    ),
                    ChatToolCall(
                        "b1",
                        AiTools.CREATE_ALARMS,
                        """{"alarms":[{"time":"99:00"}]}""",
                    ),
                ),
                finishReason = "tool_calls",
                usage = AiUsage(),
            ),
            ChatCompletion(content = "ok", toolCalls = emptyList(), finishReason = "stop", usage = AiUsage()),
        )
        turn(engine(completer), completer).toList()

        val results = toolResultsOf(completer.requests[1])
        assertEquals(setOf("v1", "b1"), results.keys)
        // The valid call is rejected too: a batch is all-or-nothing.
        assertTrue(
            completer.requests[1]
                .first { it.role == ChatRole.TOOL && it.toolCallId == "v1" }
                .content!!.contains("batch_rejected"),
        )
        assertTrue(
            completer.requests[1]
                .first { it.role == ChatRole.TOOL && it.toolCallId == "b1" }
                .content!!.contains("ok\":false"),
        )
    }

    @Test
    fun tooManyActionsInOneProposalIsRejected() = runTest {
        val items = (1..AiTokenBudget.MAX_BATCH_ITEMS + 1).joinToString(",") {
            """{"time":"07:00"}"""
        }
        val completer = ScriptedCompleter(
            ChatCompletion(
                content = null,
                toolCalls = listOf(
                    ChatToolCall("t1", AiTools.CREATE_ALARMS, """{"alarms":[$items]}"""),
                ),
                finishReason = "tool_calls",
                usage = AiUsage(),
            ),
            ChatCompletion(content = "ok", toolCalls = emptyList(), finishReason = "stop", usage = AiUsage()),
        )
        turn(engine(completer), completer).toList()

        assertTrue(
            completer.requests[1]
                .first { it.role == ChatRole.TOOL }
                .content!!.contains("too_many_items"),
        )
    }

    @Test
    fun roundsExhaustedFailsAndResumeReusesTheToolHistory() = runTest {
        val rejected = ChatCompletion(
            content = null,
            toolCalls = listOf(
                ChatToolCall(
                    "bad",
                    AiTools.CREATE_ALARMS,
                    """{"alarms":[{"time":"99:00"}]}""",
                ),
            ),
            finishReason = "tool_calls",
            usage = AiUsage(),
        )
        val finished = ChatCompletion(
            content = "ok",
            toolCalls = emptyList(),
            finishReason = "stop",
            usage = AiUsage(),
        )
        val completer = ScriptedCompleter(rejected, rejected, rejected, rejected, rejected, finished)
        val testEngine = engine(completer)
        val events = turn(testEngine, completer).toList()

        assertEquals(TurnFailure.RoundsExhausted, events.filterIsInstance<TurnEvent.Failed>().single().failure)
        assertEquals(AiTokenBudget.MAX_TOOL_ROUNDS, completer.requests.size)

        // The retry continues the same turn: every earlier tool result is still in the context.
        val resumeEvents = testEngine.resumeTurn(
            credentials = credentials,
            completedTurns = emptyList(),
            actionLines = emptyList(),
        ).toList()
        assertEquals(1, resumeEvents.filterIsInstance<TurnEvent.Finished>().size)
        val toolMessages = completer.requests.last().filter { it.role == ChatRole.TOOL }
        assertEquals(AiTokenBudget.MAX_TOOL_ROUNDS, toolMessages.size)
        assertTrue(toolMessages.all { it.toolCallId == "bad" })
    }

    @Test
    fun aFailedTurnCanBeReplacedByANewOne() = runTest {
        val rejected = ChatCompletion(
            content = null,
            toolCalls = listOf(
                ChatToolCall(
                    "bad",
                    AiTools.CREATE_ALARMS,
                    """{"alarms":[{"time":"99:00"}]}""",
                ),
            ),
            finishReason = "tool_calls",
            usage = AiUsage(),
        )
        val completer = ScriptedCompleter(
            rejected,
            rejected,
            rejected,
            rejected,
            rejected,
            ChatCompletion(content = "ok", toolCalls = emptyList(), finishReason = "stop", usage = AiUsage()),
        )
        val testEngine = engine(completer)
        val failed = turn(testEngine, completer).toList()
        assertEquals(TurnFailure.RoundsExhausted, failed.filterIsInstance<TurnEvent.Failed>().single().failure)

        val next = turn(testEngine, completer).toList()
        assertEquals(1, toolEvents(next).size)
    }

    @Test
    fun everyRoundOfATurnReusesTheFrozenDynamicTail() = runTest {
        var clock = FIXED_NOW
        val completer = ScriptedCompleter(
            ChatCompletion(
                content = null,
                toolCalls = listOf(ChatToolCall("r1", AiTools.LIST_ALARMS, "{}")),
                finishReason = "tool_calls",
                usage = AiUsage(),
            ),
            ChatCompletion(
                content = null,
                toolCalls = listOf(ChatToolCall("r2", AiTools.LIST_RINGTONES, "{}")),
                finishReason = "tool_calls",
                usage = AiUsage(),
            ),
            ChatCompletion(content = "done", toolCalls = emptyList(), finishReason = "stop", usage = AiUsage()),
            onRequest = { clock = clock.plusMinutes(1) },
        )
        val events = turn(engine(completer, now = { clock }), completer).toList()

        assertEquals(1, toolEvents(events).size)
        assertEquals(FIXED_NOW.plusMinutes(3), clock)
        val tails = completer.requests.map { dynamicTailOf(it) }
        assertEquals(3, tails.size)
        assertEquals(1, tails.distinct().size)
        assertTrue(tails.first().contains("Local date-time: 2026-01-15 10:00"))
    }

    @Test
    fun resumeTurnReusesTheFrozenTailOfTheFailedAttempt() = runTest {
        val read = ChatCompletion(
            content = null,
            toolCalls = listOf(ChatToolCall("r1", AiTools.LIST_ALARMS, "{}")),
            finishReason = "tool_calls",
            usage = AiUsage(),
        )
        val completer = ScriptedCompleter(
            read,
            read,
            read,
            read,
            read,
            ChatCompletion(content = "ok", toolCalls = emptyList(), finishReason = "stop", usage = AiUsage()),
        )
        var clock = FIXED_NOW
        val testEngine = engine(completer, now = { clock })
        val events = turn(testEngine, completer).toList()

        assertEquals(TurnFailure.RoundsExhausted, events.filterIsInstance<TurnEvent.Failed>().single().failure)
        val failedTails = completer.requests.map { dynamicTailOf(it) }
        assertEquals(AiTokenBudget.MAX_TOOL_ROUNDS, failedTails.size)
        assertEquals(1, failedTails.distinct().size)

        clock = clock.plusMinutes(1)
        val resumeEvents = testEngine.resumeTurn(credentials, emptyList(), emptyList()).toList()
        assertEquals(1, resumeEvents.filterIsInstance<TurnEvent.Finished>().size)
        assertEquals(failedTails.first(), dynamicTailOf(completer.requests.last()))
        assertEquals(
            failedTails.first(),
            resumeEvents.filterIsInstance<TurnEvent.Finished>().single().dynamicContext,
        )
    }

    @Test
    fun finishedAndProposalEventsCarryTheFrozenTail() = runTest {
        val write = ChatCompletion(
            content = null,
            toolCalls = listOf(
                ChatToolCall(
                    "p1",
                    AiTools.CREATE_ALARMS,
                    """{"alarms":[{"time":"07:00"}]}""",
                ),
            ),
            finishReason = "tool_calls",
            usage = AiUsage(),
        )
        val completer = ScriptedCompleter(
            write,
            ChatCompletion(content = "plain", toolCalls = emptyList(), finishReason = "stop", usage = AiUsage()),
        )
        val testEngine = engine(completer)
        val proposalEvents = turn(testEngine, completer).toList()
        val ready = proposalEvents.filterIsInstance<TurnEvent.ProposalReady>().single()
        assertEquals(dynamicTailOf(completer.requests.single()), ready.dynamicContext)

        testEngine.discardPendingTurn()
        val finishedEvents = turn(testEngine, completer).toList()
        val finished = toolEvents(finishedEvents).single()
        assertEquals("plain", finished.answer)
        assertEquals(dynamicTailOf(completer.requests.single()), finished.dynamicContext)
    }

    @Test
    fun discardPendingTurnThenRunTurnBuildsAFreshTail() = runTest {
        val completer = ScriptedCompleter(
            ChatCompletion(
                content = null,
                toolCalls = listOf(
                    ChatToolCall(
                        "p1",
                        AiTools.CREATE_ALARMS,
                        """{"alarms":[{"time":"07:00"}]}""",
                    ),
                ),
                finishReason = "tool_calls",
                usage = AiUsage(),
            ),
            ChatCompletion(content = "ok", toolCalls = emptyList(), finishReason = "stop", usage = AiUsage()),
        )
        var clock = FIXED_NOW
        val testEngine = engine(completer, now = { clock })
        val events = turn(testEngine, completer).toList()
        events.filterIsInstance<TurnEvent.ProposalReady>().single()
        val suspendedTail = dynamicTailOf(completer.requests.single())

        testEngine.discardPendingTurn()
        clock = clock.plusMinutes(2)
        turn(testEngine, completer).toList()

        val freshTail = dynamicTailOf(completer.requests.single())
        assertTrue(suspendedTail.contains("Local date-time: 2026-01-15 10:00"))
        assertTrue(freshTail.contains("Local date-time: 2026-01-15 10:02"))
        assertTrue(suspendedTail != freshTail)
    }

    private companion object {
        val FIXED_NOW = java.time.ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, java.time.ZoneId.of("UTC"))
    }
}

private class FakeTagDao(
    private val tags: List<Tag>,
    private val alarmRefs: List<AlarmTagCrossRef> = emptyList(),
    private val eventRefs: List<EventTagCrossRef> = emptyList(),
) : TagDao {
    override fun observeAll(): Flow<List<Tag>> = flowOf(tags)
    override suspend fun getAll(): List<Tag> = tags
    override suspend fun getByName(name: String): Tag? = tags.find { it.name == name }
    override suspend fun insert(tag: Tag): Long = TODO()
    override suspend fun insertAll(tags: List<Tag>): List<Long> = TODO()
    override suspend fun update(tag: Tag) = TODO()
    override suspend fun delete(tag: Tag) = TODO()
    override fun observeEventTagRefs(): Flow<List<EventTagCrossRef>> = flowOf(eventRefs)
    override fun observeAlarmTagRefs(): Flow<List<AlarmTagCrossRef>> = flowOf(alarmRefs)
    override suspend fun getEventTagRefs(): List<EventTagCrossRef> = eventRefs
    override suspend fun getAlarmTagRefs(): List<AlarmTagCrossRef> = alarmRefs
    override suspend fun getEventTagRefsFor(eventIds: List<Long>): List<EventTagCrossRef> =
        eventRefs.filter { it.eventId in eventIds }

    override suspend fun getAlarmTagRefsFor(alarmIds: List<Long>): List<AlarmTagCrossRef> =
        alarmRefs.filter { it.alarmId in alarmIds }

    override suspend fun clearEventTags(eventId: Long) = TODO()
    override suspend fun clearAlarmTags(alarmId: Long) = TODO()
    override suspend fun clearEventTagsByIds(eventIds: List<Long>) = TODO()
    override suspend fun clearAlarmTagsByIds(alarmIds: List<Long>) = TODO()
    override suspend fun insertEventTags(refs: List<EventTagCrossRef>) = TODO()
    override suspend fun insertAlarmTags(refs: List<AlarmTagCrossRef>) = TODO()
    override suspend fun deleteEventTagRefs(tagId: Long) = TODO()
    override suspend fun deleteAlarmTagRefs(tagId: Long) = TODO()
    override suspend fun deleteAllEventTags() = TODO()
    override suspend fun deleteAllAlarmTags() = TODO()
    override suspend fun deleteAllTags() = TODO()
}

private class FakeAlarmDao(private val alarms: List<Alarm>) : AlarmDao {
    override fun observeAll(): Flow<List<Alarm>> = flowOf(alarms)
    override suspend fun getAll(): List<Alarm> = alarms
    override suspend fun insert(alarm: Alarm): Long = TODO()
    override suspend fun insertAll(alarms: List<Alarm>): List<Long> = TODO()
    override suspend fun update(alarm: Alarm) = TODO()
    override suspend fun updateAll(alarms: List<Alarm>) = TODO()
    override suspend fun delete(alarm: Alarm) = TODO()
    override suspend fun getById(id: Long): Alarm? = alarms.find { it.id == id }
    override suspend fun getEnabled(): List<Alarm> = alarms.filter { it.enabled }
    override suspend fun countEnabled(): Int = alarms.count { it.enabled }
    override suspend fun count(): Int = alarms.size
    override suspend fun getOrderedPage(limit: Int): List<Alarm> = alarms.take(limit)
    override suspend fun getByIds(ids: List<Long>): List<Alarm> = alarms.filter { it.id in ids }
    override suspend fun setSnoozedUntil(id: Long, until: Long) = TODO()
    override suspend fun clearSnooze(id: Long) = TODO()
    override suspend fun setStackRingtone(stackId: Long, ringtone: String) = TODO()
    override suspend fun disableIfNotSnoozed(id: Long): Int = TODO()
    override suspend fun getByEventId(eventId: Long): Alarm? =
        alarms.find { it.eventId == eventId }

    override suspend fun getByEventIds(eventIds: List<Long>): List<Alarm> =
        alarms.filter { it.eventId in eventIds }

    override suspend fun syncEventAlarms(
        eventIds: List<Long>,
        time: Int,
        name: String,
        note: String,
    ) = TODO()

    override suspend fun detachFromEvents(ids: List<Long>) = TODO()
    override suspend fun deleteByIds(ids: List<Long>) = TODO()
    override suspend fun getLinkedEventIds(): List<Long> =
        alarms.mapNotNull { it.eventId }.distinct()

    override suspend fun getLinkedEventIdsFor(eventIds: List<Long>): List<Long> =
        alarms.mapNotNull { it.eventId }.distinct().filter { it in eventIds }

    override suspend fun getOrphanedEventAlarms(): List<Alarm> = TODO()
    override suspend fun deleteOrphanedEventAlarms(): Int = TODO()
    override suspend fun deleteAllAlarms() = TODO()
}

private class FakeEventDao : EventDao {
    override fun observeAll(): Flow<List<Event>> = flowOf(emptyList())
    override suspend fun getAll(): List<Event> = emptyList()
    override suspend fun getById(id: Long): Event? = null
    override suspend fun getBetween(from: Long, to: Long): List<Event> = emptyList()
    override fun observeBetween(from: Long, to: Long): Flow<List<Event>> = flowOf(emptyList())
    override suspend fun getBetweenPage(from: Long, to: Long, limit: Int): List<Event> = emptyList()
    override suspend fun countBetween(from: Long, to: Long): Int = 0
    override suspend fun getByIds(ids: List<Long>): List<Event> = emptyList()
    override suspend fun getBySeriesIds(seriesIds: List<Long>): List<Event> = emptyList()
    override suspend fun getBySeries(seriesId: Long): List<Event> = emptyList()
    override suspend fun insert(event: Event): Long = TODO()
    override suspend fun insertAll(events: List<Event>): List<Long> = TODO()
    override suspend fun update(event: Event) = TODO()
    override suspend fun updateAll(events: List<Event>) = TODO()
    override suspend fun updateEventFields(
        ids: List<Long>,
        title: String,
        note: String,
        start: Int,
        end: Int,
        reminderMinutes: Int,
        allDay: Boolean,
        dateShift: Long,
    ) = TODO()

    override suspend fun deleteByIds(ids: List<Long>) = TODO()
    override suspend fun delete(event: Event) = TODO()
    override suspend fun deleteAll(events: List<Event>) = TODO()
    override suspend fun deleteAllEvents() = TODO()
}
