package jp.aruno.clicker.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.aruno.clicker.MainViewModel
import jp.aruno.clicker.RemoteSyncUiState
import jp.aruno.clicker.data.AppSettings
import kotlin.math.roundToInt

private enum class AppScreen { HOME, SETTINGS, DETAILS }

@Composable
fun ArunoClickerApp(
    viewModel: MainViewModel,
    permissionRefresh: Int,
    openAccessibilitySettings: () -> Unit,
    requestOverlayPermission: () -> Unit,
    requestNotificationPermission: () -> Unit,
    startFirstAutomation: () -> Unit,
    startRepeatAutomation: () -> Unit,
    stopAutomation: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val remoteSyncState by viewModel.remoteSyncState.collectAsStateWithLifecycle()
    val isRunning by viewModel.automationRequested.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (!settings.onboardingComplete) {
        OnboardingScreen(
            context = context,
            permissionRefresh = permissionRefresh,
            openAccessibilitySettings = openAccessibilitySettings,
            requestOverlayPermission = requestOverlayPermission,
            requestNotificationPermission = requestNotificationPermission,
            onComplete = { viewModel.update { it.copy(onboardingComplete = true) } },
        )
        return
    }

    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    BackHandler(enabled = screen != AppScreen.HOME) { screen = AppScreen.HOME }

    when (screen) {
        AppScreen.HOME -> HomeScreen(
            settings = settings,
            isRunning = isRunning,
            onStartFirst = {
                startFirstAutomation()
            },
            onStartRepeat = {
                startRepeatAutomation()
            },
            onStop = {
                stopAutomation()
            },
            openSettings = { screen = AppScreen.SETTINGS },
        )

        AppScreen.SETTINGS -> SettingsScreen(
            settings = settings,
            remoteSyncState = remoteSyncState,
            onUpdate = viewModel::update,
            onFetchRemoteUrls = viewModel::fetchRemoteUrls,
            onPublishRemoteUrls = viewModel::publishRemoteUrls,
            onRegisterAdminKey = viewModel::registerAdminKey,
            onBack = { screen = AppScreen.HOME },
            openDetails = { screen = AppScreen.DETAILS },
        )

        AppScreen.DETAILS -> DetailsScreen(
            settings = settings,
            onUpdate = viewModel::update,
            onBack = { screen = AppScreen.SETTINGS },
        )
    }
}

@Composable
private fun OnboardingScreen(
    context: Context,
    permissionRefresh: Int,
    openAccessibilitySettings: () -> Unit,
    requestOverlayPermission: () -> Unit,
    requestNotificationPermission: () -> Unit,
    onComplete: () -> Unit,
) {
    val overlayGranted = remember(permissionRefresh) { Settings.canDrawOverlays(context) }
    val accessibilityGranted = remember(permissionRefresh) {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        enabled.contains(context.packageName, ignoreCase = true) &&
            enabled.contains("ArunoAccessibilityService", ignoreCase = true)
    }
    val notificationGranted = remember(permissionRefresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF04170B), MatrixBlack, MatrixBlack)),
            ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MatrixGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("A↘", color = MatrixGreen, fontSize = 35.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "ARUNO CLICKER",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                "自動スライドを、安定してシンプルに。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
            )
        }
        item {
            PermissionCard(
                title = "ユーザー補助",
                body = "画面操作に必要です。ONにできない場合は、アプリ情報の右上メニューから「制限付き設定を許可」してから有効にしてください。",
                granted = accessibilityGranted,
                buttonLabel = if (accessibilityGranted) "有効" else "設定を開く",
                icon = Icons.Rounded.AccessibilityNew,
                onClick = openAccessibilitySettings,
            )
        }
        item {
            PermissionCard(
                title = "他のアプリの上に表示",
                body = "TikTok Lite 上に停止ボタンを表示します。",
                granted = overlayGranted,
                buttonLabel = if (overlayGranted) "許可済み" else "許可する",
                icon = Icons.Rounded.Layers,
                onClick = requestOverlayPermission,
            )
        }
        item {
            PermissionCard(
                title = "通知",
                body = "実行状態と停止操作を通知から確認できます。",
                granted = notificationGranted,
                buttonLabel = if (notificationGranted) "許可済み" else "許可する",
                icon = Icons.Rounded.Notifications,
                onClick = requestNotificationPermission,
            )
        }
        item {
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("初期設定を完了", fontWeight = FontWeight.Bold)
            }
            Text(
                "権限は後から端末の設定で変更できます。",
                style = MaterialTheme.typography.bodySmall,
                color = MatrixTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    buttonLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    MatrixCard {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconBubble(icon, if (granted) MatrixGreen else AlertAmber)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (granted) {
                        Icon(Icons.Rounded.Check, contentDescription = "許可済み", tint = MatrixGreen)
                    }
                }
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MatrixTextMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                OutlinedButton(onClick = onClick, enabled = !granted) { Text(buttonLabel) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    settings: AppSettings,
    isRunning: Boolean,
    onStartFirst: () -> Unit,
    onStartRepeat: () -> Unit,
    onStop: () -> Unit,
    openSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ARUNO CLICKER", fontWeight = FontWeight.Black)
                        Text(
                            "AUTO SLIDE CONTROLLER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MatrixGreen,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = openSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "設定")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MatrixBlack),
            )
        },
        containerColor = MatrixBlack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF123B20), Color(0xFF081A0E), Color(0xFF06120A)),
                                ),
                            )
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) MatrixGreen.copy(alpha = .16f) else Color.White.copy(alpha = .06f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (isRunning) Icons.Rounded.SwipeUp else Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = if (isRunning) MatrixGreen else MatrixTextMuted,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                        Text(
                            if (isRunning) "自動スライド中" else "待機中",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        Text(
                            if (isRunning) {
                                "画面を切り替えてもバックグラウンドで実行します"
                            } else {
                                "初回起動または2回目以降の動作を選択してください"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MatrixTextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
                        )
                        Button(
                            onClick = if (isRunning) onStop else onStartFirst,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp),
                            shape = RoundedCornerShape(17.dp),
                            colors = if (isRunning) {
                                ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White)
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                        ) {
                            Icon(
                                if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                            )
                            Text(
                                if (isRunning) "停止する" else "初回起動スタート",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        if (!isRunning) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onStartRepeat,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp),
                                shape = RoundedCornerShape(17.dp),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Text(
                                    "2回目以降スタート",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                        AnimatedVisibility(isRunning) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 18.dp)
                                    .clip(CircleShape),
                                color = MatrixGreen,
                                trackColor = MatrixGreen.copy(alpha = .12f),
                            )
                        }
                    }
                }
            }
            item { SectionTitle("現在の設定") }
            item {
                MatrixCard {
                    SummaryLine(Icons.Rounded.Refresh, "スライド間隔", "約 ${settings.scrollIntervalSeconds} 秒")
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    SummaryLine(Icons.Rounded.Bolt, "操作速度", "${settings.swipeDurationMillis} ms")
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    SummaryLine(
                        Icons.Rounded.Alarm,
                        "実行時間",
                        if (settings.runMinutes == 0) "停止するまで" else "${settings.runMinutes} 分",
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = openSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null)
                    Text("動作を設定", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MatrixGreen, modifier = Modifier.size(20.dp))
        Text(label, color = MatrixTextMuted, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    settings: AppSettings,
    remoteSyncState: RemoteSyncUiState,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onFetchRemoteUrls: () -> Unit,
    onPublishRemoteUrls: () -> Unit,
    onRegisterAdminKey: () -> Unit,
    onBack: () -> Unit,
    openDetails: () -> Unit,
) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(PowerManager::class.java)
    val batteryUnrestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }
    StandardScaffold(title = "動作設定", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle("基本動作") }
            item {
                MatrixCard {
                    SliderSetting(
                        title = "スライド間隔",
                        valueLabel = if (settings.randomInterval) "7・8・9秒" else "${settings.scrollIntervalSeconds} 秒",
                        value = settings.scrollIntervalSeconds.toFloat(),
                        range = 3f..60f,
                        steps = 56,
                        onChange = { value ->
                            onUpdate { it.copy(scrollIntervalSeconds = value.roundToInt()) }
                        },
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    SliderSetting(
                        title = "1回のスライド速度",
                        valueLabel = "${swipeSpeedLabel(settings.swipeDurationMillis)}・${settings.swipeDurationMillis} ms",
                        value = settings.swipeDurationMillis.toFloat(),
                        range = 80f..1_500f,
                        steps = 70,
                        onChange = { value ->
                            val snapped = (value / 20).roundToInt() * 20
                            onUpdate { it.copy(swipeDurationMillis = snapped.coerceIn(80, 1_500)) }
                        },
                    )
                    Text(
                        "速い（80ms） ← スワイプ速度 → 遅い（1500ms）\n数値が小さいほど速く画面が切り替わります。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MatrixTextMuted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    SliderSetting(
                        title = "自動停止",
                        valueLabel = if (settings.runMinutes == 0) "なし" else "${settings.runMinutes} 分後",
                        value = settings.runMinutes.toFloat(),
                        range = 0f..180f,
                        steps = 11,
                        onChange = { value ->
                            val snapped = (value / 15).roundToInt() * 15
                            onUpdate { it.copy(runMinutes = snapped) }
                        },
                    )
                }
            }
            item { SectionTitle("起動URL", Modifier.padding(top = 6.dp)) }
            item {
                MatrixCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "初回起動スタートではURL 1を2回、URL 2を2回、それぞれ10秒間隔で開きます。日付による自動判定は使用しません。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MatrixTextMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = settings.startupUrl1,
                            onValueChange = { value -> onUpdate { it.copy(startupUrl1 = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("URL 1") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = settings.startupUrl2,
                            onValueChange = { value -> onUpdate { it.copy(startupUrl2 = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("URL 2") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                    }
                }
            }
            item { SectionTitle("端末間URL同期", Modifier.padding(top = 6.dp)) }
            item {
                MatrixCard {
                    SettingSwitchRow(
                        title = "サーバー同期",
                        subtitle = "アプリ起動時と開始直前に共通URLを取得します",
                        checked = settings.remoteSyncEnabled,
                        onCheckedChange = { checked -> onUpdate { it.copy(remoteSyncEnabled = checked) } },
                        icon = Icons.Rounded.Refresh,
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = settings.remoteServerUrl,
                            onValueChange = { value -> onUpdate { it.copy(remoteServerUrl = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("同期サーバーURL（https）") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onFetchRemoteUrls,
                            enabled = !remoteSyncState.busy && settings.remoteSyncEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Text("サーバーから取得", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    SettingSwitchRow(
                        title = "管理端末モード",
                        subtitle = "この端末から全端末用URLを変更できます",
                        checked = settings.remoteAdminMode,
                        onCheckedChange = { checked -> onUpdate { it.copy(remoteAdminMode = checked) } },
                        icon = Icons.Rounded.Tune,
                    )
                    if (settings.remoteAdminMode) {
                        HorizontalDivider(color = Color.White.copy(alpha = .07f))
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = settings.remoteAdminKey,
                                onValueChange = { value ->
                                    val normalized = value.filter { character ->
                                        character in 'A'..'Z' || character in 'a'..'z' || character in '0'..'9'
                                    }.take(8)
                                    onUpdate { it.copy(remoteAdminKey = normalized) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("管理キー（英数字8文字）") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                            Text(
                                "最初の管理端末で好きな英数字8文字を一度だけ登録します。キーはこの端末内に暗号化保存します。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MatrixTextMuted,
                                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                            )
                            OutlinedButton(
                                onClick = onRegisterAdminKey,
                                enabled = !remoteSyncState.busy && settings.remoteSyncEnabled && settings.remoteAdminKey.length == 8,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("この8文字を初回登録")
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = onPublishRemoteUrls,
                                enabled = !remoteSyncState.busy && settings.remoteSyncEnabled,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("URL 1・2を全端末へ送信")
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("同期状態", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (remoteSyncState.busy) "通信中…" else remoteSyncState.message.ifBlank {
                                settings.remoteLastSyncMessage
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                remoteSyncState.busy -> AlertAmber
                                remoteSyncState.success == true -> MatrixGreen
                                remoteSyncState.success == false -> DangerRed
                                settings.remoteLastSyncSucceeded -> MatrixGreen
                                else -> MatrixTextMuted
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (settings.remoteLastSyncEpochMillis > 0L) {
                            Text(
                                "設定版: ${settings.remoteConfigVersion}　最終成功: ${formatSyncTime(settings.remoteLastSyncEpochMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MatrixTextMuted,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Text(
                            "取得に失敗した場合は、最後に保存できた端末内URLで続行します。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MatrixTextMuted,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
            item { SectionTitle("高速・強制スライド", Modifier.padding(top = 6.dp)) }
            item {
                MatrixCard {
                    SettingSwitchRow(
                        title = "広告・写真を早く送る",
                        subtitle = "画面要素から判定できた場合に高速設定へ切り替えます",
                        checked = settings.fastContentEnabled,
                        onCheckedChange = { checked -> onUpdate { it.copy(fastContentEnabled = checked) } },
                        icon = Icons.Rounded.Bolt,
                    )
                    Column {
                        HorizontalDivider(color = Color.White.copy(alpha = .07f))
                        SliderSetting(
                            title = "高速時の待機",
                            valueLabel = "${settings.fastIntervalSeconds} 秒",
                            value = settings.fastIntervalSeconds.toFloat(),
                            range = 1f..7f,
                            steps = 5,
                            onChange = { value ->
                                onUpdate { it.copy(fastIntervalSeconds = value.roundToInt()) }
                            },
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = .07f))
                        SliderSetting(
                            title = "高速時のスライド速度",
                            valueLabel = "${swipeSpeedLabel(settings.fastSwipeDurationMillis)}・${settings.fastSwipeDurationMillis} ms",
                            value = settings.fastSwipeDurationMillis.toFloat(),
                            range = 80f..300f,
                            steps = 21,
                            onChange = { value ->
                                val snapped = (value / 10).roundToInt() * 10
                                onUpdate { it.copy(fastSwipeDurationMillis = snapped) }
                            },
                        )
                    }
                }
            }
            item { SectionTitle("安定動作", Modifier.padding(top = 6.dp)) }
            item {
                MatrixCard {
                    SettingSwitchRow(
                        title = "7・8・9秒をランダムにする",
                        subtitle = "サークルゲージがない画面も強制スライドします",
                        checked = settings.randomInterval,
                        onCheckedChange = { checked -> onUpdate { it.copy(randomInterval = checked) } },
                        icon = Icons.Rounded.AutoAwesome,
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    SettingSwitchRow(
                        title = "ポップアップの×を自動で閉じる",
                        subtitle = "画面上端から90%以内の閉じる要素を押して動作を再開します",
                        checked = settings.autoDismissPopups,
                        onCheckedChange = { checked -> onUpdate { it.copy(autoDismissPopups = checked) } },
                        icon = Icons.Rounded.Close,
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    SettingSwitchRow(
                        title = "画面を点灯したままにする",
                        subtitle = "実行中のスリープを防ぎます",
                        checked = settings.keepScreenAwake,
                        onCheckedChange = { checked -> onUpdate { it.copy(keepScreenAwake = checked) } },
                        icon = Icons.Rounded.Visibility,
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    NavigationRow(
                        title = if (batteryUnrestricted) "バッテリー制限なし（設定済み）" else "バッテリー制限を解除",
                        subtitle = "長時間の自動操作が止まりにくい設定を開きます",
                        icon = Icons.Rounded.BatteryChargingFull,
                        onClick = { openBatteryOptimizationSettings(context, batteryUnrestricted) },
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    SettingSwitchRow(
                        title = "中断後に自動で再開",
                        subtitle = "広告や一時停止後にスライドを再開します",
                        checked = settings.autoResume,
                        onCheckedChange = { checked -> onUpdate { it.copy(autoResume = checked) } },
                        icon = Icons.Rounded.Refresh,
                    )
                }
            }
            item { SectionTitle("表示・予約", Modifier.padding(top = 6.dp)) }
            item {
                MatrixCard {
                    SettingSwitchRow(
                        title = "フローティング操作",
                        subtitle = "他のアプリ上に開始・停止を表示します",
                        checked = settings.floatingController,
                        onCheckedChange = { checked -> onUpdate { it.copy(floatingController = checked) } },
                        icon = Icons.Rounded.Layers,
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    SettingSwitchRow(
                        title = "予約起動",
                        subtitle = "%02d:%02d に自動開始".format(settings.scheduleHour, settings.scheduleMinute),
                        checked = settings.scheduleEnabled,
                        onCheckedChange = { checked -> onUpdate { it.copy(scheduleEnabled = checked) } },
                        icon = Icons.Rounded.Alarm,
                    )
                }
            }
            item {
                MatrixCard {
                    NavigationRow(
                        title = "詳細設定",
                        subtitle = "スワイプ位置・再試行・画面待機",
                        icon = Icons.Rounded.Tune,
                        onClick = openDetails,
                    )
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

private fun swipeSpeedLabel(durationMillis: Int): String = when {
    durationMillis <= 100 -> "最速"
    durationMillis <= 200 -> "速い"
    durationMillis <= 450 -> "標準"
    durationMillis <= 900 -> "遅い"
    else -> "最も遅い"
}

private fun formatSyncTime(epochMillis: Long): String = runCatching {
    java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
}.getOrDefault("--")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsScreen(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    StandardScaffold(title = "詳細設定", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MatrixCard {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = AlertAmber)
                        Text(
                            "通常は変更不要です。画面サイズやTikTok Liteの表示に合わせて調整してください。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MatrixTextMuted,
                        )
                    }
                }
            }
            item { SectionTitle("スワイプ範囲", Modifier.padding(top = 4.dp)) }
            item {
                MatrixCard {
                    SliderSetting(
                        title = "開始位置（画面下から）",
                        valueLabel = "${settings.swipeStartPercent}%",
                        value = settings.swipeStartPercent.toFloat(),
                        range = 55f..95f,
                        steps = 39,
                        onChange = { value -> onUpdate { it.copy(swipeStartPercent = value.roundToInt()) } },
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    SliderSetting(
                        title = "終了位置（画面上から）",
                        valueLabel = "${settings.swipeEndPercent}%",
                        value = settings.swipeEndPercent.toFloat(),
                        range = 5f..45f,
                        steps = 39,
                        onChange = { value -> onUpdate { it.copy(swipeEndPercent = value.roundToInt()) } },
                    )
                }
            }
            item { SectionTitle("エラー回復", Modifier.padding(top = 6.dp)) }
            item {
                MatrixCard {
                    SliderSetting(
                        title = "操作失敗時の再試行",
                        valueLabel = "${settings.actionRetryCount} 回",
                        value = settings.actionRetryCount.toFloat(),
                        range = 0f..5f,
                        steps = 4,
                        onChange = { value -> onUpdate { it.copy(actionRetryCount = value.roundToInt()) } },
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        onUpdate {
                            it.copy(
                                scrollIntervalSeconds = 8,
                                swipeDurationMillis = 300,
                                randomInterval = true,
                                fastContentEnabled = true,
                                fastIntervalSeconds = 2,
                                fastSwipeDurationMillis = 150,
                                swipeStartPercent = 56,
                                swipeEndPercent = 29,
                                actionRetryCount = 3,
                                pageSettleMillis = 1_200,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Text("推奨値に戻す", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

private fun openBatteryOptimizationSettings(context: Context, alreadyUnrestricted: Boolean) {
    val primary = if (!alreadyUnrestricted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
    } else {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
    runCatching { context.startActivity(primary) }.onFailure {
        context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
    }
}

@Composable
private fun SliderSetting(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(valueLabel, color = MatrixGreen, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StandardScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MatrixBlack),
            )
        },
        containerColor = MatrixBlack,
        content = content,
    )
}
