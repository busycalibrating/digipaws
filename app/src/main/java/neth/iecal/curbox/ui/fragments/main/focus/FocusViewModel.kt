package neth.iecal.curbox.ui.fragments.main.focus

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.blockers.FocusModeBlocker
import neth.iecal.curbox.data.models.FOCUS_NO_END_TIME
import neth.iecal.curbox.data.models.ManualFocusGroup
import neth.iecal.curbox.data.models.PomodoroPhase
import neth.iecal.curbox.data.models.PomodoroState
import neth.iecal.curbox.utils.DataStoreManager

class FocusViewModel(application: Application) : AndroidViewModel(application) {
    var newGroupSelectedApps = hashSetOf<String>()
    var newGroupSelectedKeywords = hashSetOf<String>()

    private val dataStoreManager = DataStoreManager(application)
    private val db = neth.iecal.curbox.data.db.AppDatabase.getInstance(application)
    private val statsDao = db.focusStatsDao()

    private val _groups = MutableStateFlow<List<neth.iecal.curbox.data.models.ManualFocusGroup>>(emptyList())
    val groups: StateFlow<List<neth.iecal.curbox.data.models.ManualFocusGroup>> = _groups

    val allSessions = statsDao.getAllSessionsFlow()

    private val _autoDndGroups = MutableStateFlow<List<neth.iecal.curbox.data.models.AutoDndGroup>>(emptyList())
    val autoDndGroups: StateFlow<List<neth.iecal.curbox.data.models.AutoDndGroup>> = _autoDndGroups
    
    private val prefs = application.getSharedPreferences("AppPreferences", android.content.Context.MODE_PRIVATE)
    var selectedMins = prefs.getInt("lastFocusDuration", 25)
    var isPomodoroEnabled = prefs.getBoolean("lastPomodoroEnabled", false)
    var pomodoroBreakMins = prefs.getInt(
        "lastPomodoroBreakDuration",
        PomodoroState.DEFAULT_BREAK_MINUTES
    )
    var pomodoroFocusIntervals = prefs.getInt(
        "lastPomodoroFocusIntervals",
        PomodoroState.DEFAULT_FOCUS_INTERVALS
    )

    private val _selectedGroup = MutableStateFlow<ManualFocusGroup?>(null)

    /** Observable form of [selectedGroup], so the screen can follow the setup sheet. */
    val selectedGroupFlow: StateFlow<ManualFocusGroup?> = _selectedGroup

    var selectedGroup: ManualFocusGroup?
        get() = _selectedGroup.value
        set(value) { _selectedGroup.value = value }


    private val _currentRunningFocus = MutableStateFlow<Pair<String?, Long>>(Pair(null,0L))
    val currentRunningFocus: StateFlow<Pair<String?, Long>> = _currentRunningFocus

    private val _currentPomodoroState = MutableStateFlow(PomodoroState())
    val currentPomodoroState: StateFlow<PomodoroState> = _currentPomodoroState

    private var timerJob: Job? = null
    private var _currentRunningTimer = MutableStateFlow<Long>(0L)
    var currentRunningTimer: StateFlow<Long> = _currentRunningTimer


    init {
        viewModelScope.launch {
            dataStoreManager.settings.collectLatest { settings ->
                _groups.value = settings.manualFocusGroups
                _autoDndGroups.value = settings.autoDndGroups
                _currentPomodoroState.value = settings.activePomodoroState
                _currentRunningFocus.value = settings.activeManualFocusGroupId

                if (selectedGroup == null && settings.manualFocusGroups.isNotEmpty()) {
                    val lastGroupId = prefs.getString("lastFocusGroupId", null)
                    val lastUsedGroup = settings.manualFocusGroups.find { it.groupId == lastGroupId }

                    if (lastUsedGroup != null) {
                        selectedGroup = lastUsedGroup
                    } else if (settings.manualFocusGroups.size == 1) {
                        selectedGroup = settings.manualFocusGroups[0]
                    }
                }

                if (settings.activeManualFocusGroupId.first != null) {
                    if (settings.activeManualFocusGroupId.second < System.currentTimeMillis()) {
                        if (settings.activePomodoroState.isActive) {
                            requestFocusBlockerRefresh()
                        } else {
                            forceStopFocus(wasMidwayExit = false)
                        }
                    } else {
                        requestFocusBlockerRefresh()
                    }
                }
            }
        }
    }


    fun updateGroups(newGroups: List<ManualFocusGroup>) {
        viewModelScope.launch {
            dataStoreManager.updateManualFocusGroups(newGroups)
        }
    }

    private fun requestFocusBlockerRefresh() {
        val intent = Intent(FocusModeBlocker.INTENT_ACTION_REFRESH_FOCUS_MODE)
        application.sendBroadcast(intent)
    }

    fun forceStopFocus(wasMidwayExit: Boolean = false){
        viewModelScope.launch {
            val runningSessions = statsDao.getRunningSessions()
            val now = System.currentTimeMillis()
            for (session in runningSessions) {
                if (wasMidwayExit) {
                    statsDao.update(session.copy(status = 2, actualEndTimeInMillis = now))
                } else {
                    val actEnd = if (session.estimatedEndTimeInMillis < now) session.estimatedEndTimeInMillis else now
                    statsDao.update(session.copy(status = 1, actualEndTimeInMillis = actEnd))
                }
            }
            dataStoreManager.setManualFocusStateToInactive()
            requestFocusBlockerRefresh()
        }
    }
    fun startFocusing() {
        if(selectedGroup == null) return
        // Pomodoro is a sequence of timed phases, so it cannot apply to an untimed group.
        val untimed = selectedGroup?.isUntimed == true
        val pomodoroState = if (isPomodoroEnabled && !untimed) {
            PomodoroState(
                isActive = true,
                phase = PomodoroPhase.FOCUS,
                focusMinutes = selectedMins,
                breakMinutes = pomodoroBreakMins,
                totalFocusIntervals = pomodoroFocusIntervals
            ).normalized()
        } else {
            PomodoroState()
        }
        prefs.edit()
            .putInt("lastFocusDuration", selectedMins)
            .putString("lastFocusGroupId", selectedGroup?.groupId)
            .putBoolean("lastPomodoroEnabled", isPomodoroEnabled)
            .putInt("lastPomodoroBreakDuration", pomodoroState.breakMinutes)
            .putInt("lastPomodoroFocusIntervals", pomodoroState.totalFocusIntervals)
            .apply()
        
        val durationMs = if (pomodoroState.isActive) {
            pomodoroState.currentPhaseDurationMs()
        } else {
            selectedMins.coerceAtLeast(1) * 60_000L
        }
        val startTime = System.currentTimeMillis()
        val endTime = if (untimed) FOCUS_NO_END_TIME else startTime + durationMs
        viewModelScope.launch {
            val session = neth.iecal.curbox.data.db.FocusStatsEntity(
                groupId = selectedGroup!!.groupId,
                startTimeInMillis = startTime,
                estimatedEndTimeInMillis = endTime,
                actualEndTimeInMillis = 0L,
                status = 0
            )
            statsDao.insert(session)
            
            dataStoreManager.setManualFocusStateToActive(
                selectedGroup!!.groupId,
                durationMs,
                pomodoroState,
                endTime
            )
            requestFocusBlockerRefresh()
        }
    }

    fun configurePomodoro(enabled: Boolean, breakMinutes: Int, focusIntervals: Int) {
        isPomodoroEnabled = enabled
        pomodoroBreakMins = breakMinutes.coerceIn(
            PomodoroState.MIN_MINUTES,
            PomodoroState.MAX_BREAK_MINUTES
        )
        pomodoroFocusIntervals = focusIntervals.coerceIn(
            PomodoroState.MIN_FOCUS_INTERVALS,
            PomodoroState.MAX_FOCUS_INTERVALS
        )
    }
    fun addGroup(group: ManualFocusGroup) {
        val updatedGroups = _groups.value.toMutableList().apply { add(group) }
        updateGroups(updatedGroups)
    }

    fun removeGroup(group: ManualFocusGroup) {
        val currentGroups = _groups.value.toMutableList()
        currentGroups.remove(group)
        updateGroups(currentGroups)
    }

    fun updateGroup(group: ManualFocusGroup) {
        val currentGroups = _groups.value.toMutableList()
        val index = currentGroups.indexOfFirst { it.groupId == group.groupId }
        if (index != -1) {
            currentGroups[index] = group
            updateGroups(currentGroups)
        }
    }


    fun startTimer( endTime: Long) {
        // Stop any existing timer before starting a new one
        timerJob?.cancel()

        timerJob = viewModelScope.launch {
            if (endTime == FOCUS_NO_END_TIME) {
                // Nothing to count towards, so report how long the session has run. The
                // start comes from the open stats row so it survives leaving the screen.
                val startedAt = statsDao.getRunningSessions()
                    .minOfOrNull { it.startTimeInMillis } ?: System.currentTimeMillis()
                while (true) {
                    _currentRunningTimer.value = System.currentTimeMillis() - startedAt
                    delay(100L)
                }
            }

            while (System.currentTimeMillis() < endTime) {
                val remaining = endTime - System.currentTimeMillis()

                _currentRunningTimer.value = remaining

                delay(100L)
            }

            onTimerFinished()
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        _currentRunningTimer.value = 0L
    }

    private fun onTimerFinished() {
        _currentRunningTimer.value = 0L
    }
}
