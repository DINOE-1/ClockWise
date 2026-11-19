package com.dinoe.clockwise.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ClockTimeFormatTest {
    @Test
    fun singleDigitHourIsPaddedInA24HourClock() {
        val formatter = DateTimeFormatter.ofPattern("H:mm", Locale.US)
        assertEquals("09:05", formatter.formatPaddedHour(LocalTime.of(9, 5)))
    }

    @Test
    fun twoDigitHourKeepsItsWidth() {
        val formatter = DateTimeFormatter.ofPattern("H:mm", Locale.US)
        assertEquals("10:05", formatter.formatPaddedHour(LocalTime.of(10, 5)))
    }

    @Test
    fun hourIsPaddedAheadOfTheDayPeriod() {
        val formatter = DateTimeFormatter.ofPattern("a h:mm", Locale.TRADITIONAL_CHINESE)
        assertTrue(formatter.formatPaddedHour(LocalTime.of(9, 5)).endsWith("09:05"))
    }

    @Test
    fun hourIsPaddedWhenTheDayPeriodFollows() {
        val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        assertEquals("09:05 PM", formatter.formatPaddedHour(LocalTime.of(21, 5)))
    }
}
