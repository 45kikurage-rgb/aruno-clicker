package jp.aruno.clicker.automation

import jp.aruno.clicker.data.RecentsLayout

internal data class RecentCard(val id: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}
internal enum class RecentsSurface { CARDS, EMPTY, HOME, UNKNOWN }
internal data class RecentsScreen(val surface: RecentsSurface, val cards: List<RecentCard> = emptyList(), val detail: String = "")
internal interface RecentsPort {
    fun open(): Boolean
    fun screen(): RecentsScreen
    fun dismiss(card: RecentCard, layout: RecentsLayout, alternate: Boolean, done: (Boolean) -> Unit)
    fun home(): Boolean
    fun recordFailure(reason: String) {}
}

/** Never equates missing TikTok, a missing tree, or a gesture callback with an empty history. */
internal class RecentsCleaner(
    private val port: RecentsPort,
    private val layout: RecentsLayout,
    private val progress: (String) -> Unit = {},
) : UrlRoutePreparer {
    private var failure: String? = null
    override fun failureMessage(): String? = failure
    override fun prepare(scheduler: DelayScheduler, active: () -> Boolean, done: (Boolean) -> Unit) {
        var ended = false
        failure = null
        var unknownReads = 0
        var polls = 0
        var swipes = 0
        var emptyReads = 0
        var reopenings = 0
        var unchanged = 0
        var previous: List<RecentCard>? = null
        fun finish(ok: Boolean, reason: String = "削除完了を確認できませんでした") {
            if (!ended && active()) {
                ended = true
                if (!ok) {
                    failure = "履歴削除を停止\n$reason"
                    runCatching { port.recordFailure(reason) }
                    runCatching { port.home() }
                    progress(failure!!)
                }
                done(ok)
            }
        }
        fun post(delay: Long = 500L, task: () -> Unit) {
            scheduler.schedule(delay) {
                if (!ended && active()) runCatching(task).onFailure { finish(false) }
            }
        }
        lateinit var check: () -> Unit
        check = check@{
            if (++polls > 180 || swipes > 100) { finish(false, "履歴削除の確認回数が上限に達しました"); return@check }
            val screen = port.screen()
            if (screen.surface != RecentsSurface.UNKNOWN) unknownReads = 0
            when (screen.surface) {
                RecentsSurface.EMPTY -> {
                    // Separate observations after animations settle; card absence alone is insufficient.
                    emptyReads++
                    if (emptyReads < 3) post { check() }
                    else if (!port.home()) finish(false)
                    else post(600L) {
                        if (port.screen().surface == RecentsSurface.HOME) {
                            progress("全ての履歴カードの削除を確認しました")
                            finish(true)
                        } else finish(false)
                    }
                }
                RecentsSurface.HOME -> {
                    emptyReads = 0
                    // OEMs may return HOME after the final dismissal. Reopen and verify EMPTY explicitly.
                    if (++reopenings > 3 || !port.open()) finish(false)
                    else post(900L) { check() }
                }
                RecentsSurface.UNKNOWN -> {
                    emptyReads = 0
                    unknownReads++
                    progress("履歴画面を確認中（$unknownReads/12）\n${screen.detail}")
                    if (unknownReads >= 12) finish(false, screen.detail.ifBlank { "履歴画面の要素を取得できません" })
                    else post { check() }
                }
                RecentsSurface.CARDS -> {
                    emptyReads = 0
                    if (screen.cards.isEmpty()) { post { check() }; return@check }
                    unchanged = if (previous == screen.cards) unchanged + 1 else 0
                    if (unchanged >= (if (screen.cards.first().id == "overview-position") 12 else 3)) { finish(false, "カードが消えません。履歴表示の縦・横とロックカードを確認してください"); return@check }
                    previous = screen.cards
                    val card = screen.cards.first()
                    progress("履歴カードを削除中（操作${swipes + 1}回・表示${screen.cards.size}枚）")
                    swipes++
                    var answered = false
                    val watchdog = scheduler.schedule(2400L) {
                        if (!answered && !ended && active()) {
                            answered = true
                            finish(false, "スワイプの応答がありません。動作診断を共有してください")
                        }
                    }
                    port.dismiss(card, layout, unchanged > 0) { ok ->
                        if (!answered && !ended) {
                            answered = true; watchdog.cancel()
                            post(500L) { if (ok) check() else finish(false, "履歴カードの削除操作が拒否・中断されました") }
                        }
                    }
                }
            }
        }
        progress("履歴画面を開いています")
        if (!port.open()) { finish(false); return }
        post(900L) { check() }
    }
}

/** Empty words in launcher settings or search history cannot authorize playback. */
internal object RecentsEmptyEvidence {
    fun verified(explicitEmpty: Boolean, overview: Boolean, emptyMessageId: Boolean,
                 cardCount: Int, complete: Boolean): Boolean =
        complete && cardCount == 0 && explicitEmpty && (overview || emptyMessageId)
}
