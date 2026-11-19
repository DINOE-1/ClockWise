package com.dinoe.clockwise.ui

import com.dinoe.clockwise.data.Event
import com.dinoe.clockwise.data.FakeDb
import com.dinoe.clockwise.data.FakeAlarmDao
import com.dinoe.clockwise.data.FakeEventDao
import com.dinoe.clockwise.data.FakeTagDao
import com.dinoe.clockwise.data.RecurrenceRule
import com.dinoe.clockwise.data.ScheduleLimit
import com.dinoe.clockwise.data.ScheduleWriter
import com.dinoe.clockwise.data.Tag
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** JVM tests for the ViewModel's write discipline, using the schedule writer and fake DAOs. */
class CalendarViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.of(2026, 1, 15)

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        db: FakeDb,
        alarmResults: List<com.dinoe.clockwise.data.ScheduleResult> =
            listOf(com.dinoe.clockwise.data.ScheduleResult.Scheduled(exact = true)),
    ): CalendarViewModel {
        val scheduler = object : com.dinoe.clockwise.data.AlarmSchedulerPort {
            override fun schedule(
                alarm: com.dinoe.clockwise.data.Alarm,
                nowMillis: Long,
            ) = alarmResults.last()

            override fun scheduleSnooze(
                alarm: com.dinoe.clockwise.data.Alarm,
                minutes: Int,
                nowMillis: Long,
            ) = alarmResults.last()

            override fun cancel(alarm: com.dinoe.clockwise.data.Alarm) {}
        }
        val deps = CalendarDeps(
            eventDao = FakeEventDao(db),
            alarmDao = FakeAlarmDao(db),
            tagDao = FakeTagDao(db),
            writer = ScheduleWriter(
                eventDao = FakeEventDao(db),
                alarmDao = FakeAlarmDao(db),
                tagDao = FakeTagDao(db),
                transactions = object : com.dinoe.clockwise.data.TransactionRunner {
                    override suspend fun <R> withTransaction(block: suspend () -> R): R = block()
                },
                alarms = scheduler,
                reminders = object : com.dinoe.clockwise.data.ReminderSchedulerPort {
                    override fun schedule(event: Event, nowMillis: Long) = true
                    override fun cancel(eventId: Long) {}
                },
            ),
            snapInterval = MutableStateFlow(15),
            timePickerStyle = MutableStateFlow(TimePickerStyle.WHEEL),
            ioDispatcher = mainDispatcher,
        )
        return CalendarViewModel(deps)
    }

    @Test
    fun addEventWritesThroughTheScheduleWriter() {
        val db = FakeDb()
        val viewModel = viewModel(db)

        viewModel.addEvent(
            title = "Tea",
            note = "",
            date = today,
            start = LocalTime.of(9, 0),
            end = LocalTime.of(10, 0),
            withAlarm = false,
            tags = emptyList(),
        )

        assertEquals(1, db.events.size)
        assertEquals("Tea", db.events.single().title)
        assertNull(viewModel.writeError)
    }

    @Test
    fun anOccurrenceLimitSurfacesAsATypedError() {
        val db = FakeDb()
        val viewModel = viewModel(db)

        viewModel.addEvent(
            title = "Daily",
            note = "",
            date = today,
            start = LocalTime.of(9, 0),
            end = LocalTime.of(9, 30),
            withAlarm = false,
            tags = emptyList(),
            repeat = RecurrenceRule(
                type = Event.REPEAT_DAILY,
                untilEpochDay = today.plusDays(4_000).toEpochDay(),
            ),
        )

        assertEquals(
            WriteError.LimitReached(ScheduleLimit.OCCURRENCES, 180),
            viewModel.writeError,
        )
        assertEquals(0, db.events.size)
        viewModel.consumeError()
        assertNull(viewModel.writeError)
    }

    @Test
    fun anInvalidDraftSurfacesAsAGenericFailure() {
        val db = FakeDb()
        val viewModel = viewModel(db)

        viewModel.addEvent(
            title = "   ",
            note = "",
            date = today,
            start = LocalTime.of(9, 0),
            end = LocalTime.of(10, 0),
            withAlarm = false,
            tags = emptyList(),
        )

        assertEquals(WriteError.Failed, viewModel.writeError)
        assertEquals(0, db.events.size)
    }
}
