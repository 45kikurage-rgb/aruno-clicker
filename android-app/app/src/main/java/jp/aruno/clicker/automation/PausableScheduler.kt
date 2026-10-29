package jp.aruno.clicker.automation

/** Freeze each pending delay, including splash recovery, without executing it. */
internal class PausableScheduler(private val delegate: DelayScheduler, private val clock: UrlRunnerClock) : DelayScheduler {
    private class Entry(var remaining: Long, val task: () -> Unit) {
        var due = 0L
        var handle: ScheduledTask? = null
    }
    private val entries = mutableSetOf<Entry>()
    private var paused = false
    override fun schedule(delayMs: Long, task: () -> Unit): ScheduledTask {
        val entry = Entry(delayMs.coerceAtLeast(0), task)
        entries += entry
        if (!paused) arm(entry)
        return ScheduledTask { entry.handle?.cancel(); entries.remove(entry) }
    }
    private fun arm(entry: Entry) {
        entry.due = clock.nowMs() + entry.remaining
        entry.handle = delegate.schedule(entry.remaining) {
            if (entries.remove(entry)) entry.task()
        }
    }
    fun pause() {
        if (paused) return
        paused = true
        entries.forEach {
            it.remaining = (it.due - clock.nowMs()).coerceAtLeast(0)
            it.handle?.cancel()
            it.handle = null
        }
    }
    fun resume() {
        if (!paused) return
        paused = false
        entries.toList().forEach(::arm)
    }
    override fun cancelAll() {
        entries.forEach { it.handle?.cancel() }
        entries.clear()
        delegate.cancelAll()
    }
}
