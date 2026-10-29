package jp.aruno.clicker.automation

import jp.aruno.clicker.data.GiftHomeMode

enum class GiftHomeSurface { FEED, REWARDS, UNKNOWN }
enum class GiftHomeTarget { GIFT, HOME }

data class GiftHomeScreen(
    val targetPackage: Boolean,
    val surface: GiftHomeSurface,
    val left: Int, val top: Int, val width: Int, val height: Int,
    val bottomTabsVisible: Boolean,
    val tabCenterY: Float? = null,
)

interface GiftHomePort {
    fun screen(): GiftHomeScreen?
    fun click(target: GiftHomeTarget): Boolean
    fun tap(x: Float, y: Float, done: (Boolean) -> Unit)
    fun back(): Boolean
}

/** The runner owns the pausable scheduler and cancels it on stop/lock/unbind. */
fun interface UrlRoutePreparer {
    fun prepare(scheduler: DelayScheduler, active: () -> Boolean, done: (Boolean) -> Unit)
}

/** Bounded navigation, never clicks rewards, ads, or redemption buttons.
 * A successful gesture alone is not a verified screen transition. */
class GiftHomeNavigator(
    private val port: GiftHomePort,
    private val mode: GiftHomeMode,
    private val progress: (String) -> Unit = {},
) : UrlRoutePreparer {
    override fun prepare(scheduler: DelayScheduler, active: () -> Boolean, done: (Boolean) -> Unit) {
        var ended = false
        fun finish(ok: Boolean) { if (!ended && active()) { ended = true; done(ok) } }
        fun post(delay: Long = POLL_MS, task: () -> Unit) {
            scheduler.schedule(delay) {
                if (!ended && active()) runCatching(task).onFailure { finish(false) }
            }
        }
        if (mode == GiftHomeMode.OFF) { finish(true); return }

        fun click(target: GiftHomeTarget, coordinate: Boolean, result: (Boolean) -> Unit) {
            val screen = port.screen()
            if (screen == null || !screen.targetPackage) { result(false); return }
            progress((if (target == GiftHomeTarget.GIFT) "ギフト" else "ホーム") +
                "を開いています（" + (if (coordinate) "位置" else "要素") + "）")
            if (!coordinate) { result(port.click(target)); return }
            // Calibrated to bottom app tabs, excluding Android's navigation bar.
            // Only a portrait full-screen TikTok surface with visible tab labels.
            if (!screen.bottomTabsVisible || screen.width <= 0 || screen.height <= screen.width) {
                result(false); return
            }
            val x = screen.left + screen.width * (if (target == GiftHomeTarget.GIFT) 0.30f else 0.10f)
            val y = screen.tabCenterY ?: (screen.top + screen.height * 0.97f)
            if (y < screen.top || y >= screen.top + screen.height) { result(false); return }
            port.tap(x, y) { ok -> post(0) { result(ok) } }
        }

        fun returnHome() {
            var attemptedBack = false
            var usedCoordinates = mode == GiftHomeMode.COORDINATES
            var attempt = 0
            var stable = 0
            lateinit var check: () -> Unit
            fun goBack() {
                val screen = port.screen()
                if (attemptedBack || screen?.targetPackage != true || screen.surface != GiftHomeSurface.REWARDS) {
                    finish(false); return
                }
                attemptedBack = true
                progress("ギフトから戻ってホームを確認しています")
                if (!port.back()) { finish(false); return }
                attempt = 0
                post { check() }
            }
            check = check@{
                val screen = port.screen()
                if (screen == null) {
                    attempt++
                    if (attempt >= MAX_POLLS) finish(false) else post { check() }
                    return@check
                }
                if (!screen.targetPackage) { finish(false); return@check }
                if (screen.surface == GiftHomeSurface.FEED) {
                    stable++
                    if (stable >= 2) { progress("ギフト→ホームを確認しました"); finish(true); return@check }
                } else stable = 0
                attempt++
                if (attempt >= MAX_POLLS) {
                    // A confirmed rewards page may be a full-screen WebView
                    // without bottom Home tabs, as seen in the supplied video.
                    goBack()
                } else post { check() }
            }
            val screen = port.screen()
            if (screen?.targetPackage != true) { finish(false); return }
            if (!screen.bottomTabsVisible) { goBack(); return }
            click(GiftHomeTarget.HOME, usedCoordinates) { clicked ->
                if (clicked) post { check() }
                else if (mode == GiftHomeMode.AUTO) {
                    usedCoordinates = true
                    click(GiftHomeTarget.HOME, true) { fallback -> if (fallback) post { check() } else goBack() }
                } else goBack()
            }
        }

        var triedCoordinates = mode == GiftHomeMode.COORDINATES
        var polls = 0
        lateinit var checkGift: () -> Unit
        fun openGift(coordinates: Boolean) {
            click(GiftHomeTarget.GIFT, coordinates) { clicked ->
                if (!clicked) {
                    if (mode == GiftHomeMode.AUTO && !triedCoordinates) {
                        triedCoordinates = true
                        click(GiftHomeTarget.GIFT, true) { fallback ->
                            if (fallback) post { checkGift() } else finish(false)
                        }
                    } else finish(false)
                } else post { checkGift() }
            }
        }
        checkGift = check@{
            val screen = port.screen()
            if (screen == null) {
                polls++
                if (polls >= MAX_POLLS) finish(false) else post { checkGift() }
                return@check
            }
            if (!screen.targetPackage) { finish(false); return@check }
            if (screen.surface == GiftHomeSurface.REWARDS) { returnHome(); return@check }
            polls++
            if (polls >= MAX_POLLS) {
                if (mode == GiftHomeMode.AUTO && !triedCoordinates && screen.surface == GiftHomeSurface.FEED) {
                    triedCoordinates = true; polls = 0; openGift(true)
                } else finish(false)
            } else post { checkGift() }
        }
        var initialPolls = 0
        lateinit var begin: () -> Unit
        begin = {
            val initial = port.screen()
            when {
                initial != null && !initial.targetPackage -> finish(false)
                initial?.surface == GiftHomeSurface.REWARDS -> returnHome()
                initial?.surface == GiftHomeSurface.FEED -> openGift(triedCoordinates)
                ++initialPolls >= MAX_POLLS -> finish(false)
                else -> post { begin() }
            }
        }
        begin()
    }

    companion object {
        const val POLL_MS = 250L
        const val MAX_POLLS = 32
    }
}
