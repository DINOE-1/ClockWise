package com.dinoe.clockwise.data

/**
 * Outcome of asking AlarmManager to schedule one alarm. The writer and the UI both branch on
 * this, so it lives with the data model rather than with the Android scheduler implementation.
 */
sealed interface ScheduleResult {
    data class Scheduled(val exact: Boolean) : ScheduleResult

    data object Disabled : ScheduleResult

    data object ExactPermissionDenied : ScheduleResult

    data object LimitReached : ScheduleResult

    data object InvalidTime : ScheduleResult

    data class Failed(val message: String? = null) : ScheduleResult
}
