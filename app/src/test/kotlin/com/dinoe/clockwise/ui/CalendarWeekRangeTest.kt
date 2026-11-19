package com.dinoe.clockwise.ui

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarWeekRangeTest {
    private fun range(date: LocalDate) = weekRange(date.toEpochDay())

    @Test
    fun weekStartsOnMonday() {
        val thursday = LocalDate.of(2026, 11, 19)

        val week = range(thursday)

        assertEquals(LocalDate.of(2026, 11, 16), LocalDate.ofEpochDay(week.first))
        assertEquals(DayOfWeek.MONDAY, LocalDate.ofEpochDay(week.first).dayOfWeek)
        assertEquals(7, (week.last - week.first + 1).toInt())
        assertTrue(thursday.toEpochDay() in week)
    }

    @Test
    fun sundayClosesTheWeekThatStartedSixDaysEarlier() {
        val sunday = LocalDate.of(2026, 11, 22)

        val week = range(sunday)

        assertEquals(LocalDate.of(2026, 11, 16).toEpochDay(), week.first)
        assertEquals(sunday.toEpochDay(), week.last)
    }

    @Test
    fun mondayStartsItsOwnWeek() {
        val monday = LocalDate.of(2026, 11, 16)

        assertEquals(monday.toEpochDay(), range(monday).first)
    }

    @Test
    fun everyDayOfTheWeekSharesOneRange() {
        val monday = LocalDate.of(2026, 11, 16)
        val expected = range(monday)

        (0L..6L).forEach { offset ->
            assertEquals(expected, range(monday.plusDays(offset)))
        }
    }

    @Test
    fun weekCrossesMonthAndYearBoundaries() {
        val newYear = LocalDate.of(2027, 1, 1)

        val week = range(newYear)

        assertEquals(LocalDate.of(2026, 12, 28).toEpochDay(), week.first)
        assertEquals(LocalDate.of(2027, 1, 3).toEpochDay(), week.last)
    }
}
