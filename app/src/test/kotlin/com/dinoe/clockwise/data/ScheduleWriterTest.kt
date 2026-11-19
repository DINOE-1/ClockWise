package com.dinoe.clockwise.data

import com.dinoe.clockwise.data.Event.Companion.REPEAT_DAILY
import com.dinoe.clockwise.data.Event.Companion.SECONDS_PER_DAY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * JVM tests for the writer's transactional invariants: alarm capacity, the disable-on-failure
 * finalize rule, series scope semantics and the post-commit scheduler effect ordering. All
 * Android dependencies are in-memory fakes.
 */
class ScheduleWriterTest {
    private val today = LocalDate.of(2026, 1, 15)

    private class RecordingAlarmScheduler(
        private val results: List<ScheduleResult> =
            listOf(ScheduleResult.Scheduled(exact = true)),
        private val snoozeResults: List<ScheduleResult> =
            listOf(ScheduleResult.Scheduled(exact = true)),
    ) : AlarmSchedulerPort {
        private val scheduleResults = ArrayDeque(results)
        private val snoozeResultQueue = ArrayDeque(snoozeResults)
        val scheduled = mutableListOf<Alarm>()
        val snoozed = mutableListOf<Alarm>()
        val cancelled = mutableListOf<Alarm>()

        override fun schedule(alarm: Alarm, nowMillis: Long): ScheduleResult =
            (scheduleResults.removeFirstOrNull() ?: scheduleResults.lastOrNull()
                ?: ScheduleResult.Scheduled(exact = true))
                .also { scheduled += alarm }

        override fun scheduleSnooze(alarm: Alarm, minutes: Int, nowMillis: Long): ScheduleResult =
            (snoozeResultQueue.removeFirstOrNull() ?: snoozeResultQueue.lastOrNull()
                ?: ScheduleResult.Scheduled(exact = true))
                .also { snoozed += alarm }

        override fun cancel(alarm: Alarm) {
            cancelled += alarm
        }
    }

    private class RecordingReminderScheduler : ReminderSchedulerPort {
        val scheduled = mutableListOf<Event>()
        val cancelled = mutableListOf<Long>()

        override fun schedule(event: Event, nowMillis: Long): Boolean {
            scheduled += event
            return true
        }

        override fun cancel(eventId: Long) {
            cancelled += eventId
        }
    }

    private val inlineTransactions = object : TransactionRunner {
        override suspend fun <R> withTransaction(block: suspend () -> R): R = block()
    }

    private fun writer(
        db: FakeDb,
        alarmResults: List<ScheduleResult> = listOf(ScheduleResult.Scheduled(exact = true)),
        snoozeResults: List<ScheduleResult> = listOf(ScheduleResult.Scheduled(exact = true)),
    ): ScheduleWriter = ScheduleWriter(
        eventDao = FakeEventDao(db),
        alarmDao = FakeAlarmDao(db),
        tagDao = FakeTagDao(db),
        transactions = inlineTransactions,
        alarms = RecordingAlarmScheduler(alarmResults, snoozeResults).also { alarmRecorder = it },
        reminders = RecordingReminderScheduler().also { reminderRecorder = it },
    )

    private lateinit var alarmRecorder: RecordingAlarmScheduler
    private lateinit var reminderRecorder: RecordingReminderScheduler

    @Test
    fun addAlarmPersistsAndSchedules() = runTest {
        val db = FakeDb()
        val creation = writer(db).addAlarm(time = 25_200, name = "Wake")

        assertEquals(1, db.alarms.size)
        assertEquals(25_200, db.alarms.single().time)
        assertEquals(creation.alarm.id, db.alarms.single().id)
        assertEquals(1, alarmRecorder.scheduled.size)
        assertTrue(creation.scheduled)
    }

    @Test
    fun finalizeDisablesTheRowWhenSchedulingFails() = runTest {
        val db = FakeDb()
        val creation = writer(
            db,
            alarmResults = listOf(ScheduleResult.Failed("no exact alarm permission")),
        ).addAlarm(time = 25_200, name = "Wake")

        // Do not leave an enabled row that has no corresponding system alarm.
        assertFalse(creation.alarm.enabled)
        assertFalse(db.alarms.single().enabled)
        assertEquals(1, alarmRecorder.scheduled.size)
        assertEquals(1, alarmRecorder.cancelled.size)
    }

    @Test
    fun exceedingTheActiveAlarmLimitThrowsInsideTheWriter() = runTest {
        val db = FakeDb()
        repeat(MAX_ACTIVE_ALARMS) { index -> db.insertAlarm(Alarm(time = index, enabled = true)) }
        val w = writer(db)

        var thrown: ScheduleLimitException? = null
        try {
            w.addAlarm(time = 25_200, name = "One too many")
        } catch (e: ScheduleLimitException) {
            thrown = e
        }
        assertEquals(ScheduleLimit.ACTIVE_ALARMS, thrown?.kind)
        assertEquals(MAX_ACTIVE_ALARMS, thrown?.maximum)
        assertEquals(MAX_ACTIVE_ALARMS, db.alarms.size)

        // Disabled alarms do not count against the capacity.
        w.addAlarm(time = 25_200, name = "Off", enabled = false)
        assertEquals(MAX_ACTIVE_ALARMS + 1, db.alarms.size)
    }

    @Test
    fun addAlarmStackRequiresTwoDistinctTimesAndStoresThemSorted() = runTest {
        val db = FakeDb()
        val creations = writer(db).addAlarmStack(
            times = listOf(25_200, 21_600),
            name = "Wind down",
        )

        // The two times persist in ascending order and share one stack id.
        assertEquals(listOf(21_600, 25_200), creations.map { it.alarm.time })
        assertEquals(creations[0].alarm.stackId, creations[1].alarm.stackId)
    }

    @Test
    fun deleteAlarmsClearsTagRefsAndCancelsEachSchedule() = runTest {
        val db = FakeDb()
        val keptId = db.insertAlarm(Alarm(time = 21_600, name = "Kept"))
        val firstId = db.insertAlarm(Alarm(time = 25_200, name = "First"))
        val secondId = db.insertAlarm(Alarm(time = 26_400, name = "Second"))
        db.alarmTags += AlarmTagCrossRef(alarmId = firstId, tagId = 1L)
        db.alarmTags += AlarmTagCrossRef(alarmId = secondId, tagId = 2L)
        db.alarmTags += AlarmTagCrossRef(alarmId = keptId, tagId = 3L)
        val w = writer(db)
        val first = db.alarms.first { it.id == firstId }
        val second = db.alarms.first { it.id == secondId }

        w.deleteAlarms(listOf(first, second))

        assertEquals(listOf(keptId), db.alarms.map { it.id })
        assertEquals(listOf(3L), db.alarmTags.map { it.tagId })
        assertEquals(setOf(firstId, secondId), alarmRecorder.cancelled.map { it.id }.toSet())
    }

    @Test
    fun addAlarmStackRejectsASingleTime() = runTest {
        try {
            writer(FakeDb()).addAlarmStack(times = listOf(25_200), name = "Too few")
        } catch (expected: IllegalArgumentException) {
            return@runTest
        }
        throw AssertionError("a single time must be rejected")
    }

    @Test
    fun updateEventScopedThisAndSubsequentShiftsOnlyFollowingOccurrences() = runTest {
        val db = FakeDb()
        seedSeries(db)
        val event2 = db.events.first { it.occurrenceIndex == 1 }
        val w = writer(db)

        val mutation = w.updateEventScoped(
            event = event2,
            scope = EventScope.THIS_AND_SUBSEQUENT,
            title = "Moved",
            note = "",
            date = today.plusDays(7),
            start = java.time.LocalTime.of(8, 0),
            end = java.time.LocalTime.of(8, 0),
            withAlarm = true,
            tags = emptyList(),
            reminderMinutes = null,
        )

        assertEquals(2, mutation.affectedEvents)
        // Occurrence 1 keeps its old date and title.
        assertEquals(today.toEpochDay(), db.events.first { it.occurrenceIndex == 0 }.date)
        assertEquals("Daily", db.events.first { it.occurrenceIndex == 0 }.title)
        // Occurrences 2 and 3 move by seven days and take the new fields.
        val moved = db.events.filter { it.occurrenceIndex >= 1 }.sortedBy { it.occurrenceIndex }
        assertEquals(
            listOf(today.plusDays(7).toEpochDay(), today.plusDays(8).toEpochDay()),
            moved.map { it.date },
        )
        assertEquals(listOf("Moved", "Moved"), moved.map { it.title })

        // The linked alarm of occurrence 2 is synced in place, occurrence 3 gets a new alarm.
        val synced = db.alarms.single { it.eventId == 2L }
        assertEquals(28_800, synced.time)
        assertEquals("Moved", synced.name)
        assertEquals(7L, synced.fireDate!! - today.toEpochDay())
        assertEquals(listOf(2L, 3L), alarmRecorder.scheduled.map { it.eventId })
        assertEquals(listOf(2L, 3L), reminderRecorder.cancelled)
        assertTrue(reminderRecorder.scheduled.isEmpty())
    }

    @Test
    fun deleteEventScopedKeepsAndDetachesAlarmsWhenAsked() = runTest {
        val db = FakeDb()
        db.insertAlarm(
            Alarm(time = 25_200, eventId = 1L, fireDate = today.toEpochDay(), name = "Daily"),
        )
        db.insertEvent(
            Event(
                title = "Daily",
                note = "",
                date = today.toEpochDay(),
                start = 25_200,
                end = 25_200,
            ),
        )
        val w = writer(db)

        w.deleteEventScoped(
            event = db.events.single(),
            scope = EventScope.THIS_EVENT,
            deleteAlarms = false,
        )
        assertTrue(db.events.isEmpty())
        // The alarm survives as a standalone alarm instead of becoming an orphan.
        assertEquals(1, db.alarms.size)
        assertNull(db.alarms.single().eventId)
        assertEquals(1, reminderRecorder.cancelled.size)
    }

    @Test
    fun deleteEventScopedWithAlarmsRemovesThemToo() = runTest {
        val db = FakeDb()
        seedSeries(db)
        val w = writer(db)

        val mutation = w.deleteEventScoped(
            event = db.events.first { it.occurrenceIndex == 0 },
            scope = EventScope.ENTIRE_SERIES,
            deleteAlarms = true,
        )

        assertEquals(3, mutation.affectedEvents)
        assertEquals(1, mutation.deletedAlarms) // only occurrence 2 has a linked alarm
        assertTrue(db.events.isEmpty())
        assertTrue(db.alarms.isEmpty())
        assertEquals(1, alarmRecorder.cancelled.size)
        assertEquals(2L, alarmRecorder.cancelled.single().eventId)
    }

    @Test
    fun addEventCreatesOccurrencesAlarmsAndReminders() = runTest {
        val db = FakeDb()
        val creation = writer(db).addEvent(
            title = "Standup",
            note = "",
            date = today,
            start = java.time.LocalTime.of(9, 0),
            end = java.time.LocalTime.of(9, 15),
            withAlarm = true,
            tags = emptyList(),
            repeat = RecurrenceRule(
                type = REPEAT_DAILY,
                untilEpochDay = today.plusDays(2).toEpochDay(),
            ),
            reminderMinutes = 5,
        )

        assertEquals(3, creation.events.size)
        assertEquals(3, db.events.size)
        assertEquals(3, creation.alarms.size)
        assertEquals(3, alarmRecorder.scheduled.size)
        assertEquals(3, reminderRecorder.scheduled.size)
        // Source row carries the series id; the following occurrences reference it.
        assertEquals(creation.events[0].id, creation.events[1].seriesId)
    }

    /** A three-occurrence daily series with a linked alarm on occurrence 2. */
    private fun seedSeries(db: FakeDb) {
        val seriesId = 10L
        (0..2).forEach { index ->
            db.insertEvent(
                Event(
                    title = "Daily",
                    note = "",
                    date = today.toEpochDay() + index,
                    start = 25_200,
                    end = 25_200,
                    seriesId = seriesId,
                    occurrenceIndex = index,
                ),
            )
        }
        db.insertAlarm(
            Alarm(
                time = 25_200,
                eventId = 2L,
                fireDate = today.toEpochDay() + 1,
                name = "Daily",
            ),
        )
    }

    @Test
    fun finalizeAllCountsMixedResultsAndDisablesFailures() = runTest {
        val db = FakeDb()
        db.insertAlarm(Alarm(time = 25_200, name = "one", enabled = true))
        db.insertAlarm(Alarm(time = 25_200, name = "two", enabled = true))
        db.insertAlarm(Alarm(time = 25_200, name = "three", enabled = true))
        db.insertAlarm(Alarm(time = 25_200, name = "off", enabled = false))

        val summary = writer(
            db,
            alarmResults = listOf(
                ScheduleResult.Scheduled(exact = true),
                ScheduleResult.Failed("permission"),
                ScheduleResult.Scheduled(exact = false),
            ),
        ).finalizeAll(db.alarms.toList())

        assertEquals(2, summary.scheduled)
        assertEquals(1, summary.unscheduled)
        // The failed row is disabled; the already-disabled row was never scheduled.
        assertFalse(db.alarms.first { it.name == "two" }.enabled)
        assertFalse(db.alarms.first { it.name == "off" }.enabled)
        assertEquals(3, alarmRecorder.scheduled.size)
    }

    @Test
    fun persistSnoozePersistsAndSchedulesTheSnoozeRing() = runTest {
        val db = FakeDb()
        val id = db.insertAlarm(Alarm(time = 25_200, name = "Wake", enabled = true))
        val now = 1_000_000L

        val result = writer(db).persistSnooze(alarmId = id, minutes = 10, nowMillis = now)

        assertTrue(result is ScheduleResult.Scheduled)
        val row = db.alarms.single()
        assertEquals(now + 10 * 60_000L, row.snoozedUntil)
        assertTrue(row.enabled)
        assertEquals(1, alarmRecorder.snoozed.size)
    }

    @Test
    fun persistSnoozeFailureRollsTheRowBack() = runTest {
        val db = FakeDb()
        val id = db.insertAlarm(Alarm(time = 25_200, name = "Wake", enabled = true))
        val now = 1_000_000L

        writer(
            db,
            snoozeResults = listOf(ScheduleResult.Failed("no permission")),
        ).persistSnooze(alarmId = id, minutes = 10, nowMillis = now)

        // Do not leave an enabled row whose snooze has no system alarm behind it.
        val row = db.alarms.single()
        assertFalse(row.enabled)
        assertNull(row.snoozedUntil)
    }

    @Test
    fun persistSnoozeReturnsNullForAMissingRow() = runTest {
        val result = writer(FakeDb()).persistSnooze(alarmId = 42L, minutes = 10, nowMillis = 0L)
        assertNull(result)
        assertTrue(alarmRecorder.snoozed.isEmpty())
    }
}

/** In-memory stand-in for the three tables the writer touches. */
internal class FakeDb {
    val alarms = mutableListOf<Alarm>()
    val events = mutableListOf<Event>()
    val alarmTags = mutableListOf<AlarmTagCrossRef>()
    val eventTags = mutableListOf<EventTagCrossRef>()
    private var nextAlarmId = 1L
    private var nextEventId = 1L

    fun insertAlarm(alarm: Alarm): Long {
        val id = nextAlarmId++
        alarms += alarm.copy(id = id)
        return id
    }

    fun insertEvent(event: Event): Long {
        val id = nextEventId++
        events += event.copy(id = id)
        return id
    }
}

internal class FakeEventDao(private val db: FakeDb) : EventDao {
    override fun observeAll(): Flow<List<Event>> = flowOf(db.events)
    override suspend fun getAll(): List<Event> = db.events.toList()
    override suspend fun getById(id: Long): Event? = db.events.find { it.id == id }
    override suspend fun getBetween(from: Long, to: Long): List<Event> =
        db.events.filter { it.occursOn(from) || it.occursOn(to) }

    override fun observeBetween(from: Long, to: Long): Flow<List<Event>> = flowOf(emptyList())
    override suspend fun getBetweenPage(from: Long, to: Long, limit: Int): List<Event> =
        getBetween(from, to).take(limit)

    override suspend fun countBetween(from: Long, to: Long): Int = getBetween(from, to).size
    override suspend fun getByIds(ids: List<Long>): List<Event> =
        db.events.filter { it.id in ids }

    override suspend fun getBySeriesIds(seriesIds: List<Long>): List<Event> =
        db.events.filter { it.seriesId in seriesIds }

    override suspend fun getBySeries(seriesId: Long): List<Event> =
        db.events.filter { it.seriesId == seriesId }.sortedBy { it.occurrenceIndex }

    override suspend fun insert(event: Event): Long = db.insertEvent(event)
    override suspend fun insertAll(events: List<Event>): List<Long> = events.map { db.insertEvent(it) }
    override suspend fun update(event: Event) {
        db.events.replaceAll { if (it.id == event.id) event else it }
    }

    override suspend fun updateAll(events: List<Event>) = events.forEach { update(it) }

    override suspend fun updateEventFields(
        ids: List<Long>,
        title: String,
        note: String,
        start: Int,
        end: Int,
        reminderMinutes: Int,
        allDay: Boolean,
        dateShift: Long,
    ) {
        db.events.replaceAll { event ->
            if (event.id in ids) {
                event.copy(
                    title = title,
                    note = note,
                    start = start,
                    end = end,
                    reminderMinutes = reminderMinutes,
                    allDay = allDay,
                    date = event.date + dateShift,
                )
            } else {
                event
            }
        }
    }

    override suspend fun deleteByIds(ids: List<Long>) {
        db.events.removeAll { it.id in ids }
    }

    override suspend fun delete(event: Event) {
        db.events.removeAll { it.id == event.id }
    }

    override suspend fun deleteAll(events: List<Event>) = deleteByIds(events.map { it.id })
    override suspend fun deleteAllEvents() = db.events.clear()
}

internal class FakeAlarmDao(private val db: FakeDb) : AlarmDao {
    override fun observeAll(): Flow<List<Alarm>> = flowOf(db.alarms)
    override suspend fun getAll(): List<Alarm> = db.alarms.toList()
    override suspend fun insert(alarm: Alarm): Long = db.insertAlarm(alarm)
    override suspend fun insertAll(alarms: List<Alarm>): List<Long> = alarms.map { db.insertAlarm(it) }
    override suspend fun update(alarm: Alarm) {
        db.alarms.replaceAll { if (it.id == alarm.id) alarm else it }
    }

    override suspend fun updateAll(alarms: List<Alarm>) = alarms.forEach { update(it) }
    override suspend fun delete(alarm: Alarm) {
        db.alarms.removeAll { it.id == alarm.id }
    }

    override suspend fun getById(id: Long): Alarm? = db.alarms.find { it.id == id }
    override suspend fun getEnabled(): List<Alarm> = db.alarms.filter { it.enabled }
    override suspend fun countEnabled(): Int = db.alarms.count { it.enabled }
    override suspend fun count(): Int = db.alarms.size
    override suspend fun getOrderedPage(limit: Int): List<Alarm> =
        db.alarms.sortedWith(compareBy({ it.time }, { it.fireDate ?: Long.MAX_VALUE }, { it.id })).take(limit)

    override suspend fun getByIds(ids: List<Long>): List<Alarm> =
        db.alarms.filter { it.id in ids }

    override suspend fun setSnoozedUntil(id: Long, until: Long) {
        replace(id) { it.copy(snoozedUntil = until) }
    }

    override suspend fun clearSnooze(id: Long) {
        replace(id) { it.copy(snoozedUntil = null) }
    }

    override suspend fun setStackRingtone(stackId: Long, ringtone: String) {
        db.alarms.replaceAll { if (it.stackId == stackId) it.copy(ringtone = ringtone) else it }
    }

    override suspend fun disableIfNotSnoozed(id: Long): Int {
        val alarm = db.alarms.find { it.id == id }
        if (alarm != null && alarm.snoozedUntil == null) {
            db.alarms.replaceAll { if (it.id == id) it.copy(enabled = false, snoozedUntil = null) else it }
            return 1
        }
        return 0
    }

    override suspend fun getByEventId(eventId: Long): Alarm? = db.alarms.find { it.eventId == eventId }
    override suspend fun getByEventIds(eventIds: List<Long>): List<Alarm> =
        db.alarms.filter { it.eventId in eventIds }

    override suspend fun syncEventAlarms(
        eventIds: List<Long>,
        time: Int,
        name: String,
        note: String,
    ) {
        db.alarms.replaceAll { alarm ->
            if (alarm.eventId in eventIds) {
                alarm.copy(
                    time = time,
                    name = name,
                    note = note,
                    enabled = true,
                    snoozedUntil = null,
                    // Matches the SQL: fireDate is re-read from the events table.
                    fireDate = db.events.find { it.id == alarm.eventId }?.date,
                )
            } else {
                alarm
            }
        }
    }

    override suspend fun detachFromEvents(ids: List<Long>) {
        db.alarms.replaceAll { if (it.id in ids) it.copy(eventId = null) else it }
    }

    override suspend fun deleteByIds(ids: List<Long>) {
        db.alarms.removeAll { it.id in ids }
    }

    override suspend fun getLinkedEventIds(): List<Long> =
        db.alarms.mapNotNull { it.eventId }.distinct()

    override suspend fun getLinkedEventIdsFor(eventIds: List<Long>): List<Long> =
        db.alarms.mapNotNull { it.eventId }.distinct().filter { it in eventIds }

    override suspend fun getOrphanedEventAlarms(): List<Alarm> =
        db.alarms.filter { alarm -> alarm.eventId != null && db.events.none { it.id == alarm.eventId } }

    override suspend fun deleteOrphanedEventAlarms(): Int {
        val orphaned = getOrphanedEventAlarms()
        db.alarms.removeAll { it.id in orphaned.map { alarm -> alarm.id } }
        return orphaned.size
    }

    override suspend fun deleteAllAlarms() = db.alarms.clear()

    private fun replace(id: Long, transform: (Alarm) -> Alarm) {
        db.alarms.replaceAll { if (it.id == id) transform(it) else it }
    }
}

internal class FakeTagDao(private val db: FakeDb) : TagDao {
    override fun observeAll(): Flow<List<Tag>> = flowOf(emptyList())
    override suspend fun getAll(): List<Tag> = emptyList()
    override suspend fun getByName(name: String): Tag? = null
    override suspend fun insert(tag: Tag): Long = TODO()
    override suspend fun insertAll(tags: List<Tag>): List<Long> = TODO()
    override suspend fun update(tag: Tag) = TODO()
    override suspend fun delete(tag: Tag) = TODO()
    override fun observeEventTagRefs(): Flow<List<EventTagCrossRef>> = flowOf(db.eventTags)
    override fun observeAlarmTagRefs(): Flow<List<AlarmTagCrossRef>> = flowOf(db.alarmTags)
    override suspend fun getEventTagRefs(): List<EventTagCrossRef> = db.eventTags.toList()
    override suspend fun getAlarmTagRefs(): List<AlarmTagCrossRef> = db.alarmTags.toList()
    override suspend fun getEventTagRefsFor(eventIds: List<Long>): List<EventTagCrossRef> =
        db.eventTags.filter { it.eventId in eventIds }

    override suspend fun getAlarmTagRefsFor(alarmIds: List<Long>): List<AlarmTagCrossRef> =
        db.alarmTags.filter { it.alarmId in alarmIds }

    override suspend fun clearEventTags(eventId: Long) {
        db.eventTags.removeAll { it.eventId == eventId }
    }

    override suspend fun clearAlarmTags(alarmId: Long) {
        db.alarmTags.removeAll { it.alarmId == alarmId }
    }

    override suspend fun clearEventTagsByIds(eventIds: List<Long>) {
        db.eventTags.removeAll { it.eventId in eventIds }
    }

    override suspend fun clearAlarmTagsByIds(alarmIds: List<Long>) {
        db.alarmTags.removeAll { it.alarmId in alarmIds }
    }

    override suspend fun insertEventTags(refs: List<EventTagCrossRef>) {
        db.eventTags += refs
    }

    override suspend fun insertAlarmTags(refs: List<AlarmTagCrossRef>) {
        db.alarmTags += refs
    }

    override suspend fun deleteEventTagRefs(tagId: Long) = TODO()
    override suspend fun deleteAlarmTagRefs(tagId: Long) = TODO()
    override suspend fun deleteAllEventTags() = TODO()
    override suspend fun deleteAllAlarmTags() = TODO()
    override suspend fun deleteAllTags() = TODO()
}
