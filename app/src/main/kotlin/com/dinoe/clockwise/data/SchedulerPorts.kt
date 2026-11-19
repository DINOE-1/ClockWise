package com.dinoe.clockwise.data

import androidx.room.withTransaction

/**
 * Ports [ScheduleWriter] uses to reach AlarmManager without depending on the alarm package.
 * Production adapters wrap the static scheduler objects; tests replace them with recording
 * fakes, so the post-commit effect ordering is verifiable on the JVM.
 */
interface AlarmSchedulerPort {
    fun schedule(alarm: Alarm, nowMillis: Long = System.currentTimeMillis()): ScheduleResult

    /** Schedules the one-off snooze ring for an alarm that is currently firing. */
    fun scheduleSnooze(
        alarm: Alarm,
        minutes: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): ScheduleResult

    fun cancel(alarm: Alarm)
}

interface ReminderSchedulerPort {
    fun schedule(event: Event, nowMillis: Long = System.currentTimeMillis()): Boolean

    fun cancel(eventId: Long)
}

/**
 * Runs [block] inside one database transaction. Production uses Room's [withTransaction];
 * tests run the block inline, which keeps the writer's transactional invariants testable on
 * the JVM.
 */
interface TransactionRunner {
    suspend fun <R> withTransaction(block: suspend () -> R): R
}

class RoomTransactionRunner(private val database: AppDatabase) : TransactionRunner {
    override suspend fun <R> withTransaction(block: suspend () -> R): R =
        database.withTransaction { block() }
}
