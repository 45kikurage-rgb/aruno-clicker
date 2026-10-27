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
import jp.aruno.clicker.data.UrlRunnerClient
import jp.aruno.clicker.data.UrlRunnerConfig
import jp.aruno.clicker.data.UrlRunnerRepository
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

/** URLランナーのデータ送受信と保存済み設定のUI状態。 */
data class UrlRunnerUiState(
    val busy: Boolean = false,
    val message: String = "未取得",
    val success: Boolean? = null,
    val savedConfig: UrlRunnerConfig? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val remoteClient = RemoteConfigClient(application)
    private val urlRunnerRepository = UrlRunnerRepository(application)
    private val urlRunnerClient = UrlRunnerClient(application)
    private val mutableEditRevision = MutableStateFlow(0)
    val editRevision: StateFlow<Int> = mutableEditRevision.asStateFlow()
    val urlEditState = jp.aruno.clicker.data.UrlListEditState {
        mutableEditRevision.value += 1
    }.also { state -> urlRunnerRepository.loadForStart()?.let(state::loadFrom) }
    val settings: StateFlow<AppSettings> = repository.settings
    private val mutableUrlRunnerState = MutableStateFlow(
        UrlRunnerUiState(
            message = urlRunnerRepository.load().detail.ifBlank { "未取得" },
            savedConfig = urlRunnerRepository.loadForStart(),
        ),
    )
    val urlRunnerState: StateFlow<UrlRunnerUiState> = mutableUrlRunnerState.asStateFlow()
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
        // URLランナー仕様: アプリ起動時の自動取得は行わない。
        // 設定の更新は「データダウンロード」の明示操作でのみ行う。
        // （従来の3枠同期 fetchRemoteUrls() は、従来設定画面の手動操作用に残す。）
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

    // ---- URLランナー ----

    fun refreshUrlRunnerSaved() {
        val load = urlRunnerRepository.load()
        mutableUrlRunnerState.value = mutableUrlRunnerState.value.copy(
            savedConfig = load.config,
            message = if (mutableUrlRunnerState.value.busy) {
                mutableUrlRunnerState.value.message
            } else {
                load.detail.ifBlank { mutableUrlRunnerState.value.message }
            },
        )
    }

    /** 明示的な「データダウンロード」。成功時のみ、検証済みの一式で保存を原子的に差し替える。 */
    fun downloadUrlRunnerConfig() {
        if (mutableUrlRunnerState.value.busy) return
        viewModelScope.launch {
            mutableUrlRunnerState.value = mutableUrlRunnerState.value.copy(
                busy = true, message = "取得中…",
            )
            val result = urlRunnerClient.download()
            val remote = result.config
            if (result.success && remote != null) {
                val save = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { urlRunnerRepository.save(remote) }
                if (save.success) urlEditState.loadFrom(remote)
                mutableUrlRunnerState.value = UrlRunnerUiState(
                    busy = false,
                    message = if (save.success) result.message else save.message,
                    success = save.success,
                    savedConfig = urlRunnerRepository.loadForStart(),
                )
            } else {
                // 取得失敗では保存済み設定に触れない。
                mutableUrlRunnerState.value = mutableUrlRunnerState.value.copy(
                    busy = false,
                    message = result.message,
                    success = false,
                    savedConfig = urlRunnerRepository.loadForStart(),
                )
            }
        }
    }

    /** メイン端末の「データ送信」。サーバー確定応答をそのまま端末にも保存する。 */
    fun publishUrlRunnerConfig(config: UrlRunnerConfig) {
        if (mutableUrlRunnerState.value.busy) return
        viewModelScope.launch {
            mutableUrlRunnerState.value = mutableUrlRunnerState.value.copy(
                busy = true, message = "送信中…",
            )
            val result = urlRunnerClient.publish(config)
            val remote = result.config
            if (result.success && remote != null) {
                val save = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { urlRunnerRepository.save(remote) }
                if (save.success) urlEditState.loadFrom(remote)
                mutableUrlRunnerState.value = UrlRunnerUiState(
                    busy = false,
                    message = if (save.success) result.message else save.message,
                    success = save.success,
                    savedConfig = urlRunnerRepository.loadForStart(),
                )
            } else {
                mutableUrlRunnerState.value = mutableUrlRunnerState.value.copy(
                    busy = false,
                    message = result.message,
                    success = false,
                )
            }
        }
    }

    /**
     * 旧3枠から初期一覧を取り込む任意の移行操作（従来設定画面の管理者向け）。
     * 取り込んだ内容は保存されるが、サーバーへは自動送信しない。
     */
    fun importLegacyDestinationsToUrlRunner(): String {
        val current = settings.value
        val imported = urlRunnerRepository.buildLegacyImport(
            current.startupUrl1, current.startupUrl2, current.shareUrl,
        ) ?: return "旧設定にURLがありませんでした"
        val save = urlRunnerRepository.save(imported)
        refreshUrlRunnerSaved()
        return if (save.success) {
            "旧設定の${imported.urls.size}件をURL一覧へ取り込みました（未送信）"
        } else {
            save.message
        }
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
