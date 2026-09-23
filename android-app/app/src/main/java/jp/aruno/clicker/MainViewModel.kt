package jp.aruno.clicker

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.aruno.clicker.automation.AutomationContract
import jp.aruno.clicker.automation.AutomationRuntime
import jp.aruno.clicker.data.AppSettings
import jp.aruno.clicker.data.RemoteConfigClient
import jp.aruno.clicker.data.RemoteSyncResult
import jp.aruno.clicker.data.SettingsRepository
import jp.aruno.clicker.schedule.ScheduleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RemoteSyncUiState(
    val busy: Boolean = false,
    val message: String = "未同期",
    val success: Boolean? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val remoteClient = RemoteConfigClient(application)
    val settings: StateFlow<AppSettings> = repository.settings
    private val mutableRemoteSyncState = MutableStateFlow(
        RemoteSyncUiState(
            message = settings.value.remoteLastSyncMessage,
            success = settings.value.remoteLastSyncSucceeded.takeIf {
                settings.value.remoteLastSyncEpochMillis > 0L || settings.value.remoteLastSyncMessage != "未同期"
            },
        ),
    )
    val remoteSyncState: StateFlow<RemoteSyncUiState> = mutableRemoteSyncState.asStateFlow()
    private val mutableAutomationRequested = MutableStateFlow(AutomationRuntime.snapshot().requested)
    val automationRequested: StateFlow<Boolean> = mutableAutomationRequested.asStateFlow()
    private val automationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            mutableAutomationRequested.value = AutomationRuntime.snapshot().requested
        }
    }

    init {
        val filter = IntentFilter(AutomationContract.ACTION_AUTOMATION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            application.registerReceiver(automationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            application.registerReceiver(automationReceiver, filter)
        }
        ScheduleManager.rearmFromSettings(application)
        if (settings.value.remoteSyncEnabled && settings.value.remoteServerUrl.isNotBlank()) {
            fetchRemoteUrls()
        }
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(automationReceiver) }
        super.onCleared()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        repository.update(transform)
        val current = settings.value
        if (current.scheduleEnabled) {
            ScheduleManager.scheduleDaily(getApplication(), current.scheduleHour, current.scheduleMinute)
        } else {
            ScheduleManager.cancel(getApplication())
        }
    }

    fun refreshSettings() {
        repository.refresh()
        if (!mutableRemoteSyncState.value.busy) {
            mutableRemoteSyncState.value = RemoteSyncUiState(
                message = settings.value.remoteLastSyncMessage,
                success = settings.value.remoteLastSyncSucceeded.takeIf {
                    settings.value.remoteLastSyncEpochMillis > 0L || settings.value.remoteLastSyncMessage != "未同期"
                },
            )
        }
    }

    fun fetchRemoteUrls() {
        runRemoteAction { remoteClient.fetchAndCache() }
    }

    fun publishRemoteUrls() {
        runRemoteAction { remoteClient.publishCurrentUrls() }
    }

    fun registerAdminKey() {
        runRemoteAction { remoteClient.registerAdminKey() }
    }

    private fun runRemoteAction(action: suspend () -> RemoteSyncResult) {
        if (mutableRemoteSyncState.value.busy) return
        viewModelScope.launch {
            mutableRemoteSyncState.value = RemoteSyncUiState(busy = true, message = "通信中…")
            val result = action()
            repository.refresh()
            mutableRemoteSyncState.value = RemoteSyncUiState(
                busy = false,
                message = result.message,
                success = result.success,
            )
        }
    }
}
