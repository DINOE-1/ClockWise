package com.dinoe.clockwise.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.dinoe.clockwise.R
import com.dinoe.clockwise.data.ScheduleLimit
import com.dinoe.clockwise.data.ScheduleLimitException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A failed write, typed at the reporting layer. The schedule writer's exceptions stop here; the
 * UI maps this to a message, so ViewModels never resolve strings.
 */
sealed interface WriteError {
    data class LimitReached(val kind: ScheduleLimit, val maximum: Int) : WriteError
    data object Failed : WriteError
}

/**
 * Runs one write per [launch] on [ioDispatcher] and surfaces the first failure as a one-shot
 * [error] that the screen shows and then [consume]s. Shared by every screen that writes through
 * the schedule writer, so the launch/report/consume discipline exists once.
 */
class WriteReporter(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    var error by mutableStateOf<WriteError?>(null)
        private set

    fun consume() {
        error = null
    }

    fun launch(block: suspend () -> Unit) {
        scope.launch {
            try {
                // Room work and AlarmManager effects both leave the main thread here; only the
                // final result and error reporting return to the caller's context.
                withContext(ioDispatcher) { block() }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ScheduleLimitException) {
                error = WriteError.LimitReached(failure.kind, failure.maximum)
            } catch (failure: Exception) {
                error = WriteError.Failed
            }
        }
    }
}

/** Message for a write error, resolved here so ViewModels stay free of string resources. */
@Composable
internal fun writeErrorText(error: WriteError?): String? = when (error) {
    null -> null
    is WriteError.LimitReached -> when (error.kind) {
        ScheduleLimit.OCCURRENCES -> stringResource(
            R.string.write_error_occurrence_limit,
            error.maximum,
        )

        ScheduleLimit.ACTIVE_ALARMS -> stringResource(
            R.string.write_error_alarm_limit,
            error.maximum,
        )
    }

    WriteError.Failed -> stringResource(R.string.write_error_generic)
}
