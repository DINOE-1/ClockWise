package com.dinoe.clockwise.ui

import com.dinoe.clockwise.data.ScheduleLimit
import com.dinoe.clockwise.data.ScheduleLimitException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WriteReporterTest {
    @Test
    fun aSuccessfulWriteReportsNothing() = runTest {
        val reporter = WriteReporter(this, StandardTestDispatcher(testScheduler))
        var ran = false

        reporter.launch { ran = true }
        advanceUntilIdle()

        assertTrue(ran)
        assertNull(reporter.error)
    }

    @Test
    fun aScheduleLimitBecomesATypedLimitReached() = runTest {
        val reporter = WriteReporter(this, StandardTestDispatcher(testScheduler))

        reporter.launch {
            throw ScheduleLimitException(ScheduleLimit.ACTIVE_ALARMS, requested = 401, maximum = 400)
        }
        advanceUntilIdle()

        assertEquals(WriteError.LimitReached(ScheduleLimit.ACTIVE_ALARMS, 400), reporter.error)

        // The report is one-shot: consuming clears it until the next failure.
        reporter.consume()
        assertNull(reporter.error)
    }

    @Test
    fun anyOtherFailureBecomesFailed() = runTest {
        val reporter = WriteReporter(this, StandardTestDispatcher(testScheduler))

        reporter.launch { throw IllegalStateException("boom") }
        advanceUntilIdle()

        assertEquals(WriteError.Failed, reporter.error)
    }

    @Test
    fun cancellationIsNotReportedAsAFailure() = runTest {
        val reporter = WriteReporter(this, StandardTestDispatcher(testScheduler))

        reporter.launch { throw CancellationException("cancelled") }
        advanceUntilIdle()

        assertNull(reporter.error)
    }
}
