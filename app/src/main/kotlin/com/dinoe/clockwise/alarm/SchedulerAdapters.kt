package com.dinoe.clockwise.alarm

import android.content.Context
import com.dinoe.clockwise.data.Alarm
import com.dinoe.clockwise.data.AlarmSchedulerPort
import com.dinoe.clockwise.data.Event
import com.dinoe.clockwise.data.ReminderSchedulerPort
import com.dinoe.clockwise.data.ScheduleResult

/** Production adapter: delegates to the static [AlarmScheduler] with the application context. */
class SystemAlarmScheduler(context: Context) : AlarmSchedulerPort {
    private val appContext = context.applicationContext

    override fun schedule(alarm: Alarm, nowMillis: Long): ScheduleResult =
        AlarmScheduler.schedule(appContext, alarm, nowMillis)

    override fun scheduleSnooze(alarm: Alarm, minutes: Int, nowMillis: Long): ScheduleResult =
        AlarmScheduler.scheduleSnooze(appContext, alarm, minutes, nowMillis)

    override fun cancel(alarm: Alarm) {
        AlarmScheduler.cancel(appContext, alarm)
    }
}

/** Production adapter: delegates to the static [ReminderScheduler] with the application context. */
class SystemReminderScheduler(context: Context) : ReminderSchedulerPort {
    private val appContext = context.applicationContext

    override fun schedule(event: Event, nowMillis: Long): Boolean =
        ReminderScheduler.schedule(appContext, event, nowMillis)

    override fun cancel(eventId: Long) {
        ReminderScheduler.cancel(appContext, eventId)
    }
}
