package com.dinoe.clockwise.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dinoe.clockwise.ClockwiseApp
import com.dinoe.clockwise.data.AlarmDao
import com.dinoe.clockwise.data.Alarm
import com.dinoe.clockwise.data.EventDao
import com.dinoe.clockwise.data.ScheduleWriter
import com.dinoe.clockwise.data.Tag
import com.dinoe.clockwise.data.TagDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Everything [AlarmViewModel] needs; production wiring lives in [from]. */
class AlarmDeps(
    val alarmDao: AlarmDao,
    val eventDao: EventDao,
    val tagDao: TagDao,
    val writer: ScheduleWriter,
    val alarmExpandStyle: kotlinx.coroutines.flow.Flow<AlarmStackExpandStyle>,
    val timePickerStyle: kotlinx.coroutines.flow.Flow<TimePickerStyle>,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        fun from(application: Application): AlarmDeps {
            val app = application as ClockwiseApp
            val preferences = ThemePreferences(application)
            return AlarmDeps(
                alarmDao = app.database.alarmDao(),
                eventDao = app.database.eventDao(),
                tagDao = app.database.tagDao(),
                writer = app.scheduleWriter,
                alarmExpandStyle = preferences.alarmExpandStyle,
                timePickerStyle = preferences.timePickerStyle,
            )
        }
    }
}

class AlarmViewModel(private val deps: AlarmDeps) : ViewModel() {
    constructor(application: Application) : this(AlarmDeps.from(application))

    companion object {
        /** Builds the ViewModel with the application available from the CreationExtras. */
        val factory = viewModelFactory {
            initializer {
                val app = checkNotNull(
                    this[ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY],
                )
                AlarmViewModel(AlarmDeps.from(app))
            }
        }
    }

    private val alarmDao = deps.alarmDao
    private val eventDao = deps.eventDao
    private val tagDao = deps.tagDao
    private val writer = deps.writer
    private val writes = WriteReporter(viewModelScope, deps.ioDispatcher)

    val alarmExpandStyle: StateFlow<AlarmStackExpandStyle> = deps.alarmExpandStyle
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AlarmStackExpandStyle.DRAWER,
        )

    val timePickerStyle: StateFlow<TimePickerStyle> = deps.timePickerStyle
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            TimePickerStyle.WHEEL,
        )

    val alarmGroups: StateFlow<List<AlarmGroup>> =
        combine(alarmDao.observeAll(), eventDao.observeAll()) { alarms, events ->
            buildAlarmGroups(alarms, events)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var expandedGroupKeys: Set<String> by mutableStateOf(emptySet())
        private set

    /** One-shot report of a failed write; the screen shows it and calls [consumeError]. */
    val writeError: WriteError? get() = writes.error

    fun consumeError() {
        writes.consume()
    }

    fun toggleGroup(key: String) {
        expandedGroupKeys =
            if (key in expandedGroupKeys) expandedGroupKeys - key else expandedGroupKeys + key
    }

    private fun launchWrite(block: suspend () -> Unit) {
        writes.launch(block)
    }

    val allTags: StateFlow<List<Tag>> = tagDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val alarmTagMap: StateFlow<Map<Long, List<Tag>>> =
        combine(tagDao.observeAlarmTagRefs(), tagDao.observeAll()) { refs, tags ->
            val tagsById = tags.associateBy { it.id }
            refs.groupBy { it.alarmId }
                .mapValues { (_, alarmRefs) ->
                    alarmRefs.mapNotNull { tagsById[it.tagId] }.sortedBy { it.name }
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun addAlarm(
        time: Int,
        name: String,
        note: String,
        oneTime: Boolean,
        ringtone: String,
        tags: List<Tag>,
    ) {
        launchWrite {
            writer.addAlarm(
                time = time,
                name = name,
                note = note,
                oneTime = oneTime,
                tags = tags,
                ringtone = ringtone,
            )
        }
    }

    fun addAlarmStack(
        times: List<Int>,
        name: String,
        note: String,
        oneTime: Boolean,
        ringtone: String,
        tags: List<Tag>,
    ) {
        launchWrite {
            writer.addAlarmStack(
                times = times,
                name = name,
                note = note,
                oneTime = oneTime,
                tags = tags,
                ringtone = ringtone,
            )
        }
    }

    fun updateAlarmDetails(
        alarm: Alarm,
        time: Int,
        name: String,
        note: String,
        oneTime: Boolean,
        ringtone: String,
        tags: List<Tag>,
    ) {
        launchWrite {
            writer.updateAlarm(
                alarm = alarm,
                time = time,
                fireDate = alarm.fireDate,
                name = name,
                note = note,
                enabled = alarm.enabled,
                oneTime = oneTime,
                tags = tags,
                ringtone = ringtone,
            )
        }
    }

    fun setRingtone(alarm: Alarm, ringtone: String) {
        launchWrite {
            writer.updateAlarmRingtone(alarm, ringtone)
        }
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        launchWrite {
            writer.setAlarmEnabled(alarm, enabled)
        }
    }

    fun deleteAlarms(alarms: List<Alarm>) {
        launchWrite {
            writer.deleteAlarms(alarms)
        }
    }
}
