package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.appBlocker

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import neth.iecal.curbox.blockers.AppBlocker
import neth.iecal.curbox.blockers.FocusModeBlocker
import neth.iecal.curbox.data.models.AppBlockerWarningScreenConfig
import neth.iecal.curbox.data.models.AppBlockingType
import neth.iecal.curbox.data.models.AppGroup
import neth.iecal.curbox.data.models.AppTimeConfig
import neth.iecal.curbox.data.models.AppUsageConfig
import neth.iecal.curbox.utils.DataStoreManager
import neth.iecal.curbox.utils.UsageStatsHelper
import neth.iecal.curbox.utils.activeWindow
import java.util.Calendar

class AppBlockerSettingViewModel(application: Application) : AndroidViewModel(application) {
    var currentUsageConfig: AppUsageConfig = AppUsageConfig()
    var currentTimeConfig: AppTimeConfig = AppTimeConfig()
    var warningScrnConfig: AppBlockerWarningScreenConfig = AppBlockerWarningScreenConfig()

    private val dataStoreManager = DataStoreManager(application)
    private val usageStats = UsageStatsHelper(application)

    /**
     * Time left today before this group hits its usage limit, in millis.
     * Returns null for groups that are not usage based. Usage is the combined
     * total of every app in the group, matching how the blocker compares it.
     */
    suspend fun getRemainingUsageMillis(group: AppGroup): Long? {
        if (group.blockingType != AppBlockingType.Usage) return null
        val config = runCatching {
            Gson().fromJson(group.setting, AppUsageConfig::class.java)
        }.getOrNull() ?: return null

        val limitMillis = limitForToday(config) * 60_000L
        val packages = group.selectedPackages.map { it.trim() }.toSet()
        val totalUsed = withContext(Dispatchers.IO) {
            usageStats.getForegroundStatsByRelativeDay(0)
                .filter { it.packageName in packages }
                .sumOf { it.totalTime }
        }
        val linkedWindow = group.linkedTimeGroupId
            ?.let { linkedId -> _groups.value.find { it.id == linkedId } }
            ?.takeIf { it.isActive && it.blockingType == AppBlockingType.Timed }
            ?.let { runCatching { Gson().fromJson(it.setting, AppTimeConfig::class.java) }.getOrNull() }
            ?.activeWindow()
        val used = if (group.linkedTimeGroupId == null) {
            totalUsed
        } else if (linkedWindow == null) {
            0L
        } else {
            withContext(Dispatchers.IO) {
                usageStats.getForegroundUsageBetween(
                    packages,
                    linkedWindow.startMs,
                    minOf(System.currentTimeMillis(), linkedWindow.endMs)
                )
            }
        }
        return (limitMillis - used).coerceAtLeast(0L)
    }

    private fun limitForToday(config: AppUsageConfig): Long {
        return if (config.isDailyUniform) config.uniformLimit
        else config.dailyLimits[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1]
    }

    private val _groups = MutableStateFlow<List<AppGroup>>(emptyList())
    val groups: StateFlow<List<AppGroup>> = _groups
    private val _temporaryDisableAvailable = MutableStateFlow(true)
    val temporaryDisableAvailable: StateFlow<Boolean> = _temporaryDisableAvailable
    init {
        viewModelScope.launch {
            dataStoreManager.settingsForEditing.collectLatest { settings ->
                _groups.value = settings.blockedAppGroups
            }
        }
        viewModelScope.launch {
            // This escape hatch must not become usable while enabling it is still pending.
            dataStoreManager.settings.collectLatest { settings ->
                _temporaryDisableAvailable.value =
                    !settings.settingsChangeDelayConfig.isEnabled
            }
        }
    }

    private fun requestAppBlockerRefresh() {
        val intent = Intent(AppBlocker.INTENT_ACTION_REFRESH_APP_BLOCKER)
        application.sendBroadcast(intent)
    }

    fun updateGroups(newGroups: List<AppGroup>) {
        viewModelScope.launch {
            dataStoreManager.updateAppGroups(newGroups)
            requestAppBlockerRefresh()
        }
    }
    
    fun addGroup(group: AppGroup) {
        viewModelScope.launch {
            val currentSettings = dataStoreManager.settingsForEditing.first()
            val updatedGroups = currentSettings.blockedAppGroups.toMutableList().apply { add(group) }
            updateGroups(updatedGroups)
        }
    }

    fun updateGroupById(updatedGroup: AppGroup) {
        viewModelScope.launch {
            val currentSettings = dataStoreManager.settingsForEditing.first()
            val updatedGroups = currentSettings.blockedAppGroups.toMutableList()
            val index = updatedGroups.indexOfFirst { it.id == updatedGroup.id }
            if (index != -1) {
                updatedGroups[index] = updatedGroup
                updateGroups(updatedGroups)
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            val currentSettings = dataStoreManager.settingsForEditing.first()
            val updatedGroups = currentSettings.blockedAppGroups.toMutableList()
            updatedGroups.removeAll { it.id == groupId }
            updatedGroups.replaceAll {
                if (it.linkedTimeGroupId == groupId) it.copy(linkedTimeGroupId = null) else it
            }
            updateGroups(updatedGroups)
        }
    }

    fun updateGroupActiveState(index: Int, isActive: Boolean) {
        viewModelScope.launch {
            val currentSettings = dataStoreManager.settingsForEditing.first()
            val updatedGroups = currentSettings.blockedAppGroups.toMutableList()
            if (index in updatedGroups.indices) {
                updatedGroups[index] = updatedGroups[index].copy(
                    isActive = isActive,
                    temporarilyDisabledUntilMs = 0L
                )
                updateGroups(updatedGroups)
            }
        }
    }

    fun temporarilyDisableGroup(groupId: String, durationMinutes: Long) {
        viewModelScope.launch {
            if (dataStoreManager.temporarilyDisableAppGroup(groupId, durationMinutes)) {
                requestAppBlockerRefresh()
            }
        }
    }
}
