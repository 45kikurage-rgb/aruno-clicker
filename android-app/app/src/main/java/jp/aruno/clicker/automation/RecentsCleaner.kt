package jp.aruno.clicker.automation

import jp.aruno.clicker.data.RecentsLayout

internal data class RecentCard(val id: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}
internal enum class RecentsSurface { CARDS, EMPTY, HOME, UNKNOWN }
internal data class RecentsScreen(val surface: RecentsSurface, val cards: List<RecentCard> = emptyList())
internal interface RecentsPort {
    fun open(): Boolean
    fun screen(): RecentsScreen
    fun dismiss(card: RecentCard, layout: RecentsLayout, alternate: Boolean, done: (Boolean) -> Unit)
    fun home(): Boolean
}

/** Never equates missing TikTok, a missing tree, or a gesture callback with an empty history. */
internal class RecentsCleaner(
    private val port: RecentsPort,
    private val layout: RecentsLayout,
    private val progress: (String) -> Unit = {},
) : UrlRoutePreparer {
    override fun prepare(scheduler: DelayScheduler, active: () -> Boolean, done: (Boolean) -> Unit) {
        var ended = false
        var polls = 0
        var swipes = 0
        var emptyReads = 0
        var reopenings = 0
        var unchanged = 0
        var previous: List<RecentCard>? = null
        fun finish(ok: Boolean) { if (!ended && active()) { ended = true; done(ok) } }
        fun post(delay: Long = 500L, task: () -> Unit) {
            scheduler.schedule(delay) {
                if (!ended && active()) runCatching(task).onFailure { finish(false) }
            }
        }
        lateinit var check: () -> Unit
        check = check@{
            if (++polls > 180 || swipes > 100) { finish(false); return@check }
            val screen = port.screen()
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
                    post { check() }
                }
                RecentsSurface.CARDS -> {
                    emptyReads = 0
                    if (screen.cards.isEmpty()) { post { check() }; return@check }
                    unchanged = if (previous == screen.cards) unchanged + 1 else 0
                    if (unchanged >= 3) { finish(false); return@check }
                    previous = screen.cards
                    val card = screen.cards.first()
                    progress("履歴カードを削除中（操作${swipes + 1}回・表示${screen.cards.size}枚）")
                    swipes++
                    port.dismiss(card, layout, unchanged > 0) { ok ->
                        post(500L) { if (ok) check() else finish(false) }
                    }
                }
            }
        }
        progress("履歴画面を開いています")
        if (!port.open()) { finish(false); return }
        post(900L) { check() }
    }
}
