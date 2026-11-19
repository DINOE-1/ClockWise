package com.dinoe.clockwise.ui

import com.dinoe.clockwise.data.Alarm
import com.dinoe.clockwise.data.Event
import com.dinoe.clockwise.data.FakeDb
import com.dinoe.clockwise.data.FakeAlarmDao
import com.dinoe.clockwise.data.FakeEventDao
import com.dinoe.clockwise.data.FakeTagDao
import com.dinoe.clockwise.data.MAX_ACTIVE_ALARMS
import com.dinoe.clockwise.data.ScheduleLimit
import com.dinoe.clockwise.data.ScheduleResult
import com.dinoe.clockwise.data.ScheduleWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** JVM tests for the alarm ViewModel's write discipline, using the writer and fake DAOs. */
class AlarmViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(db: FakeDb): AlarmViewModel {
        val scheduler = object : com.dinoe.clockwise.data.AlarmSchedulerPort {
            override fun schedule(alarm: Alarm, nowMillis: Long) = ScheduleResult.Scheduled(exact = true)
            override fun scheduleSnooze(alarm: Alarm, minutes: Int, nowMillis: Long) =
                ScheduleResult.Scheduled(exact = true)

            override fun cancel(alarm: Alarm) {}
        }
        val deps = AlarmDeps(
            alarmDao = FakeAlarmDao(db),
            eventDao = FakeEventDao(db),
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
            alarmExpandStyle = MutableStateFlow(AlarmStackExpandStyle.DRAWER),
            timePickerStyle = MutableStateFlow(TimePickerStyle.WHEEL),
            ioDispatcher = mainDispatcher,
        )
        return AlarmViewModel(deps)
    }

    @Test
    fun addAlarmWritesThroughTheScheduleWriter() {
        val db = FakeDb()
        val viewModel = viewModel(db)

        viewModel.addAlarm(
            time = 25_200,
            name = "Wake",
            note = "",
            oneTime = false,
            ringtone = Alarm.DEFAULT_RINGTONE,
            tags = emptyList(),
        )

        assertEquals(1, db.alarms.size)
        assertEquals("Wake", db.alarms.single().name)
        assertNull(viewModel.writeError)
    }

    @Test
    fun theActiveAlarmLimitSurfacesAsATypedError() {
        val db = FakeDb()
        repeat(MAX_ACTIVE_ALARMS) { index ->
            db.insertAlarm(Alarm(time = index * 200, name = "Filled $index", enabled = true))
        }
        val viewModel = viewModel(db)

        viewModel.addAlarm(
            time = 25_200,
            name = "One too many",
            note = "",
            oneTime = false,
            ringtone = Alarm.DEFAULT_RINGTONE,
            tags = emptyList(),
        )

        assertEquals(
            WriteError.LimitReached(ScheduleLimit.ACTIVE_ALARMS, MAX_ACTIVE_ALARMS),
            viewModel.writeError,
        )
        assertEquals(MAX_ACTIVE_ALARMS, db.alarms.size)
    }

    @Test
    fun deleteAlarmsWritesThroughTheScheduleWriter() {
        val db = FakeDb()
        val viewModel = viewModel(db)
        val firstId = db.insertAlarm(Alarm(time = 25_200, name = "First"))
        val secondId = db.insertAlarm(Alarm(time = 26_400, name = "Second"))

        viewModel.deleteAlarms(
            listOf(
                db.alarms.first { it.id == firstId },
                db.alarms.first { it.id == secondId },
            ),
        )

        assertEquals(0, db.alarms.size)
        assertNull(viewModel.writeError)
    }

    @Test
    fun anInvalidTimeSurfacesAsAGenericFailure() {
        val db = FakeDb()
        val viewModel = viewModel(db)

        viewModel.addAlarm(
            time = -1,
            name = "Bad",
            note = "",
            oneTime = false,
            ringtone = Alarm.DEFAULT_RINGTONE,
            tags = emptyList(),
        )

        assertEquals(WriteError.Failed, viewModel.writeError)
        assertEquals(0, db.alarms.size)
    }
}
