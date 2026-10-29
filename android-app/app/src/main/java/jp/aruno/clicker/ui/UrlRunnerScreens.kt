package jp.aruno.clicker.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import jp.aruno.clicker.R
import jp.aruno.clicker.data.PasteMode
import jp.aruno.clicker.data.PasteReport
import jp.aruno.clicker.data.UrlListEditState
import jp.aruno.clicker.data.UrlRunnerConfig
import jp.aruno.clicker.automation.AccessibilityHealthSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import jp.aruno.clicker.data.UrlRunnerLimits
import jp.aruno.clicker.data.DailyVideoViewStore
import jp.aruno.clicker.data.GiftHomeMode
import jp.aruno.clicker.data.GiftHomeModeStore

/**
 * URLランナーの画面群。
 *
 * 起動画面のボタン構成は管理端末モード（remoteAdminMode）で決まる:
 * - 全端末: ユーザー補助設定、通常スタート、6番目からスタート、データダウンロード。
 * - メイン端末（管理モードON）: 上記＋「設定」。
 * flavor（通常版/ver.S/シェア版）では出し分けしない。
 * 実行中の停止は既存どおりフロート／通知／この画面の「停止する」から行う。
 */

private val accessibilityEventFormatter = DateTimeFormatter
    .ofPattern("M/d HH:mm", Locale.JAPAN)
    .withZone(ZoneId.of("Asia/Tokyo"))

private fun formatAccessibilityEventTime(timestamp: Long): String =
    runCatching { accessibilityEventFormatter.format(Instant.ofEpochMilli(timestamp)) }
        .getOrDefault("--/-- --:--")

/** Bulk URL slots start at 006. The first 5 slots remain individually editable. */
val URL_RUNNER_RANGES: List<IntRange> =
    (UrlRunnerLimits.INDIVIDUAL_COUNT until UrlRunnerLimits.MAX_URLS step 10).map { start ->
        (start + 1)..minOf(UrlRunnerLimits.MAX_URLS, start + 10)
    }

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun UrlRunnerHomeScreen(
    isAdmin: Boolean,
    isRunning: Boolean,
    savedConfig: UrlRunnerConfig?,
    statusMessage: String,
    syncBusy: Boolean,
    syncMessage: String,
    accessibilityGranted: Boolean,
    accessibilityHealth: AccessibilityHealthSnapshot,
    openAccessibilitySettings: () -> Unit,
    onStartVideo: () -> Unit,
    onStartFromSixthWithSplash: () -> Unit,
    onStartFromSixthWithoutSplash: () -> Unit,
    onStop: () -> Unit,
    onDownload: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLegacySettings: () -> Unit,
    onTestGiftHome: () -> Unit,
) {
    val context = LocalContext.current
    val dailyStore = remember(context) { DailyVideoViewStore(context) }
    var dailyLimit by remember { mutableStateOf(dailyStore.limit()) }
    var dailyLimitError by remember { mutableStateOf(false) }
    val giftHomeStore = remember(context) { GiftHomeModeStore(context) }
    var giftHomeMode by remember { mutableStateOf(giftHomeStore.mode()) }
    var giftHomeError by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 管理モードの有効化は既存の管理パス照合（従来設定画面）でのみ行う。
                    // サブ端末のボタン数を増やさないため、入口はタイトルの長押しに置く。
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = onOpenLegacySettings,
                        ),
                    )
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
                OutlinedButton(
                    onClick = { jp.aruno.clicker.automation.PopupDiagnostics.share(context) },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("×解除の診断を共有") }
            }
            item {
                Text(
                    if (isRunning) "実行中" else "待機中",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MatrixTextMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    if (savedConfig == null) {
                        "保存済み設定: なし（先にデータダウンロードしてください）"
                    } else {
                        "保存済み設定: URL ${savedConfig.urls.size}件" +
                            "（${UrlRunnerRevisionLabel.format(savedConfig.updatedAt)}）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MatrixTextMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                Text("6番以降の当日上限（この端末）", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(2, 3).forEach { value ->
                        OutlinedButton(
                            onClick = {
                                dailyLimitError = !dailyStore.setLimit(value)
                                if (!dailyLimitError) dailyLimit = value
                            },
                            enabled = !isRunning,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text((if (dailyLimit == value) "選択中：" else "") + "${value}回") }
                    }
                }
                Text(
                    if (dailyLimitError) "保存できませんでした。もう一度選んでください。"
                    else "1〜5番は上限対象外。6番以降は途中停止も1回に含み、日本時間0時に切り替わります。",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dailyLimitError) DangerRed else MatrixTextMuted,
                )
            }
            item {
                Text("6番以降：対象URLを直接開きます", fontWeight = FontWeight.Bold)
                Text(
                    "6番から開始すると6番URLへ進みます。途中に1番URLを挟む処理と3秒待機はありません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MatrixTextMuted,
                )
            }
            item {
                MatrixCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val connected = accessibilityHealth.serviceConnected
                        Text(
                            when {
                                !accessibilityGranted -> "ユーザー補助：設定OFF"
                                connected -> "ユーザー補助：設定ON ／ サービス接続中"
                                else -> "ユーザー補助：設定ON ／ サービス未接続"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "動画操作：" + (if (isRunning) "実行中" else "待機中"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MatrixTextMuted,
                        )
                        if (accessibilityGranted && !connected) {
                            Text(
                                "設定はONですが、サービスが接続されていません。設定画面で接続状況を確認してください。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MatrixTextMuted,
                                modifier = Modifier.padding(top = 5.dp, bottom = 4.dp),
                            )
                        } else if (!accessibilityGranted) {
                            Text(
                                "設定でAru-Tikerを選び、ONにしてください。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MatrixTextMuted,
                            )
                        }
                        Text(
                            "直近の停止：" + accessibilityHealth.lastStop.ifEmpty { "記録なし" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MatrixTextMuted,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        var historyExpanded by rememberSaveable { mutableStateOf(false) }
                        TextButton(onClick = { historyExpanded = !historyExpanded }) {
                            Text(if (historyExpanded) "接続・停止履歴を閉じる" else "接続・停止履歴を表示")
                        }
                        if (historyExpanded) {
                            if (accessibilityHealth.recent.isEmpty()) {
                                Text("履歴はありません", style = MaterialTheme.typography.bodySmall)
                            } else {
                                accessibilityHealth.recent.forEach { entry ->
                                    Text(
                                        formatAccessibilityEventTime(entry.at) + "  " +
                                            entry.category + "：" + entry.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MatrixTextMuted,
                                        modifier = Modifier.padding(bottom = 3.dp),
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = openAccessibilitySettings,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("ユーザー補助設定") }
                    }
                }
            }
            item {
                Button(
                    onClick = if (isRunning) onStop else onStartVideo,
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
                    Text(
                        if (isRunning) "停止する" else "動画スタート",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = onStartFromSixthWithSplash,
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth().height(62.dp),
                    shape = RoundedCornerShape(17.dp),
                ) { Text("6番以降から開始\nスプラッシュあり", fontWeight = FontWeight.Bold) }
            }
            item {
                OutlinedButton(
                    onClick = onStartFromSixthWithoutSplash,
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth().height(62.dp),
                    shape = RoundedCornerShape(17.dp),
                ) { Text("6番以降から開始\nスプラッシュなし", fontWeight = FontWeight.Bold) }
            }
            item {
                OutlinedButton(
                    onClick = onDownload,
                    enabled = !syncBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(17.dp),
                ) {
                    Text(
                        if (syncBusy) "通信中…" else "データダウンロード",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (isAdmin) {
                item {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(17.dp),
                    ) {
                        Text("設定", fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Text(
                    syncMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MatrixTextMuted,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlRunnerSettingsScreen(
    editState: UrlListEditState,
    editRevision: Int,
    isAdmin: Boolean,
    syncBusy: Boolean,
    syncMessage: String,
    onOpenUrlInput: () -> Unit,
    onPublish: () -> Unit,
    onOpenLegacySettings: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("戻る") }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 上部: URL入力ボタン
            item {
                Button(
                    onClick = onOpenUrlInput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("URL入力（現在${editState.filledCount()}件）", fontWeight = FontWeight.Bold)
                }
            }
            // 秒数入力欄: 1〜5番は個別、6番以降は共通
            item { SectionTitle("待機秒数（URLを開いてから次へ進むまで）") }
            for (index in 0 until UrlRunnerLimits.INDIVIDUAL_COUNT) {
                item {
                    OutlinedTextField(
                        value = editState.individualSecondsText[index],
                        onValueChange = { editState.setIndividualSeconds(index, it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("${index + 1}番") },
                        suffix = { Text("秒") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = editState.commonSecondsText,
                    onValueChange = { editState.commonSecondsText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("6番以降（共通）") },
                    suffix = { Text("秒") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            item {
                Text(
                    "秒数は${UrlRunnerLimits.MIN_SECONDS}〜${UrlRunnerLimits.MAX_SECONDS}秒で入力してください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MatrixTextMuted,
                )
            }
            item {
                OutlinedButton(onClick = {
                    editState.loopMode = if (editState.loopMode == jp.aruno.clicker.data.LoopMode.STOP)
                        jp.aruno.clicker.data.LoopMode.LOOP else jp.aruno.clicker.data.LoopMode.STOP
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (editState.loopMode == jp.aruno.clicker.data.LoopMode.STOP) "最後のURLで停止" else "最後まで進んだら繰り返す")
                }
            }
            item {
                OutlinedButton(onClick = { editState.waitFromForeground = !editState.waitFromForeground },
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (editState.waitFromForeground) "計時：前面表示の確認から" else "計時：URLの起動要求から")
                }
            }
            item {
                OutlinedTextField(value = editState.startNumberText,
                    onValueChange = { editState.startNumberText = it },
                    label = { Text("開始番号（再スタート時もここから）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            // 最下部: データ送信ボタン
            item {
                Button(
                    onClick = onPublish,
                    enabled = isAdmin && !syncBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (syncBusy) "通信中…" else "データ送信", fontWeight = FontWeight.Bold)
                }
                if (!isAdmin) {
                    Text(
                        "データ送信は管理端末モードでのみ行えます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MatrixTextMuted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    syncMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MatrixTextMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item {
                OutlinedButton(
                    onClick = onOpenLegacySettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("端末の詳細設定（管理パス・従来設定）")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlInputScreen(
    editState: UrlListEditState,
    editRevision: Int,
    onBack: () -> Unit,
) {
    var pasteText by rememberSaveable { mutableStateOf("") }
    var report by remember { mutableStateOf<PasteReport?>(null) }
    var selectedRangeIndex by rememberSaveable { mutableStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("URL入力", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("戻る") }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle("1〜5番：個別URL指定") }
            for (number in 1..UrlRunnerLimits.INDIVIDUAL_COUNT) {
                item(key = "individual-url-$number") {
                    OutlinedTextField(
                        value = editState.slotAt(number - 1),
                        onValueChange = { editState.setSlot(number - 1, it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("$number 番の動画URL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
            }
            item { SectionTitle("6〜200番：URLをまとめて貼り付け") }
            // 一括貼り付けは6番以降だけ。1〜5番は保持する。
            item {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { value ->
                        val pasted = value.length > pasteText.length + 1
                        pasteText = value
                        if (pasted && jp.aruno.clicker.data.UrlExtractor.extract(value).isNotEmpty()) {
                            report = editState.applyPaste(value, PasteMode.APPEND)
                            pasteText = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    label = { Text("URL一覧を貼り付け（6番以降へ順番に追加）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { report = editState.applyPaste(pasteText, PasteMode.APPEND) },
                        modifier = Modifier.weight(1f),
                    ) { Text("6番以降に追記") }
                    OutlinedButton(
                        onClick = { report = editState.applyPaste(pasteText, PasteMode.REPLACE) },
                        modifier = Modifier.weight(1f),
                    ) { Text("6番以降を置換") }
                }
                report?.let { current ->
                    Text(
                        current.summary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MatrixTextMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (current.excessCount > 0) {
                        Text(
                            "取り込めなかったURL: " + current.excessUrls.joinToString(" / "),
                            style = MaterialTheme.typography.bodySmall,
                            color = DangerRed,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    current.rejected.forEach { rejected ->
                        Text(
                            "除外: ${rejected.raw}（${rejected.reason}）",
                            style = MaterialTheme.typography.bodySmall,
                            color = DangerRed,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            // 6〜200番は10件単位で確認・個別修正も可能。
            item { SectionTitle("6〜200番の確認・修正（現在${editState.filledCount()}件入力済み）") }
            item {
                Text("URLがない番号は実行時に自動スキップします。1〜5番は上部で個別指定してください。",
                    style = MaterialTheme.typography.bodySmall, color = MatrixTextMuted)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    URL_RUNNER_RANGES.chunked(3).forEach { rowRanges ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowRanges.forEach { range ->
                                val rangeIndex = (range.first - UrlRunnerLimits.INDIVIDUAL_COUNT - 1) / 10
                                val selected = rangeIndex == selectedRangeIndex
                                if (selected) {
                                    Button(
                                        onClick = { selectedRangeIndex = rangeIndex },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(rangeLabel(range))
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { selectedRangeIndex = rangeIndex },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(rangeLabel(range))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // 選択範囲の番号付き入力欄10件。編集はホルダーへ即時保存され、範囲切替でも保持される。
            val range = URL_RUNNER_RANGES[selectedRangeIndex]
            for (number in range) {
                item(key = "url-slot-$number") {
                    OutlinedTextField(
                        value = editState.slotAt(number - 1),
                        onValueChange = { editState.setSlot(number - 1, it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(number.toString().padStart(3, '0')) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
            }
        }
    }
}

private fun rangeLabel(range: IntRange): String =
    "${range.first.toString().padStart(3, '0')}–${range.last.toString().padStart(3, '0')}"


