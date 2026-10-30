package jp.aruno.clicker.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import jp.aruno.clicker.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** A user-labelled reference, never an automatic assertion that a popup closed. */
internal object ElementReferenceCapture {
    private const val MAX_NODES = 4000
    fun capture(service: AccessibilityService, screen: Rect, urlNumber: Int): JSONObject {
        val windows = service.windows
        val roots = windows.sortedByDescending { it.layer }.mapNotNull { w ->
            w.root?.takeIf { it.packageName?.toString() in AutomationConfig.TARGET_PACKAGES }
                ?.let { Triple(it, w.type, w.layer) }
        }.toMutableList()
        service.rootInActiveWindow?.takeIf { it.packageName?.toString() in AutomationConfig.TARGET_PACKAGES }?.let { root ->
            if (roots.none { it.first.windowId == root.windowId }) roots.add(Triple(root, 1, 0))
        }
        val rows = JSONArray()
        var complete = roots.isNotEmpty() && windows.none { it.type == 1 && it.root == null }
        var visibleCount = 0
        for ((windowIndex, entry) in roots.withIndex()) {
            val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
            queue.add(entry.first to -1)
            while (queue.isNotEmpty() && rows.length() < MAX_NODES) {
                val (node, parent) = queue.removeFirst()
                val b = Rect().also(node::getBoundsInScreen)
                val visible = node.isVisibleToUser && !b.isEmpty && Rect.intersects(screen, b)
                if (visible) visibleCount++
                val id = rows.length()
                val text = node.text?.toString()
                val description = node.contentDescription?.toString()
                val viewId = node.viewIdResourceName
                rows.put(JSONObject().put("id", id).put("parent", parent)
                    .put("window", windowIndex).put("windowType", entry.second).put("windowLayer", entry.third)
                    .put("package", node.packageName?.toString()).put("class", node.className?.toString())
                    .put("bounds", JSONArray(listOf(b.left, b.top, b.right, b.bottom)))
                    .put("visible", visible).put("fullyOnScreen", screen.contains(b))
                    .put("clickable", node.isClickable).put("enabled", node.isEnabled)
                    .put("childCount", node.childCount)
                    .put("text", if (visible) clean(text) else JSONObject.NULL)
                    .put("description", if (visible) clean(description) else JSONObject.NULL)
                    .put("viewId", viewId ?: JSONObject.NULL)
                    .put("textMissing", text.isNullOrBlank()).put("descriptionMissing", description.isNullOrBlank())
                    .put("viewIdMissing", viewId.isNullOrBlank())
                    .put("labelsTruncated", visible && (text.orEmpty().length > 240 || description.orEmpty().length > 240)))
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child == null) complete = false else queue.add(child to id)
                }
            }
            if (queue.isNotEmpty()) complete = false
        }
        val prefs = service.getSharedPreferences("element-reference-device", Context.MODE_PRIVATE)
        val device = prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString("id", it).commit())
        }
        return JSONObject().put("schema", 1).put("recordId", UUID.randomUUID().toString())
            .put("capturedAt", System.currentTimeMillis()).put("source", "user-element-inspection")
            .put("package", service.packageName).put("versionCode", BuildConfig.VERSION_CODE)
            .put("versionName", BuildConfig.VERSION_NAME).put("deviceReference", device)
            .put("model", Build.MODEL).put("manufacturer", Build.MANUFACTURER).put("sdk", Build.VERSION.SDK_INT)
            .put("screen", JSONArray(listOf(screen.left, screen.top, screen.right, screen.bottom)))
            .put("density", service.resources.displayMetrics.density)
            .put("urlNumber", if (urlNumber in 1..200) urlNumber else JSONObject.NULL)
            .put("complete", complete).put("visibleCount", visibleCount).put("nodes", rows)
            .also { it.put("candidates", ElementReferenceCandidates.analyse(it)) }
    }

    private fun clean(value: String?): Any = value?.replace(Regex("https?://\\S+"), "[URL]")?.take(240) ?: JSONObject.NULL
}

internal object ElementReferenceSelection {
    fun bounds(node: JSONObject): Rect = node.getJSONArray("bounds").let { Rect(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3)) }
    fun atPoint(snapshot: JSONObject, x: Int, y: Int): List<JSONObject> {
        val rows = snapshot.getJSONArray("nodes")
        return (0 until rows.length()).map { rows.getJSONObject(it) }
            .filter { it.optBoolean("visible") && bounds(it).contains(x, y) }
            .sortedWith(compareByDescending<JSONObject> { it.optInt("windowLayer") }
                .thenBy { bounds(it).let { b -> b.width().toLong() * b.height() } }
                .thenBy { it.optInt("childCount") })
    }
    fun pick(snapshot: JSONObject, x: Int, y: Int): JSONObject? = atPoint(snapshot, x, y).firstOrNull()
    fun labelled(snapshot: JSONObject, selectedId: Int, isClose: Boolean): JSONObject {
        val rows = snapshot.getJSONArray("nodes")
        require(selectedId in 0 until rows.length() && rows.getJSONObject(selectedId).optBoolean("visible"))
        return JSONObject(snapshot.toString()).put("selectedNodeId", selectedId).put("isClose", isClose)
            .put("selectedBounds", rows.getJSONObject(selectedId).getJSONArray("bounds"))
            .put("selectionType", "element").put("confirmedAt", System.currentTimeMillis()).put("labelSource", "user-yes-no")
    }
    fun labelledRegion(snapshot: JSONObject, region: Rect, isClose: Boolean): JSONObject {
        val screen = snapshot.getJSONArray("screen").let { Rect(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3)) }
        require(!region.isEmpty && screen.contains(region))
        return JSONObject(snapshot.toString()).put("selectedNodeId", JSONObject.NULL).put("isClose", isClose)
            .put("selectedBounds", JSONArray(listOf(region.left, region.top, region.right, region.bottom)))
            .put("selectionType", "region").put("confirmedAt", System.currentTimeMillis()).put("labelSource", "user-yes-no")
    }
}

internal class ElementReferenceStore(context: Context) {
    private val folder = File(context.filesDir, "element-references").apply { mkdirs() }
    fun save(record: JSONObject): File = synchronized(lock) {
        val id = record.getString("recordId")
        require(ID.matches(id) && record.has("isClose") && record.has("selectedNodeId"))
        val raw = record.toString().toByteArray(Charsets.UTF_8)
        require(raw.size <= MAX_BYTES) { "記録が大きすぎるため保存できません" }
        val files = records()
        require(files.size < 100 && files.sumOf { it.length() } + raw.size <= 100L * 1024 * 1024) { "保存上限に達しました。記録を共有して確認してください" }
        val target = File(folder, "$id.json")
        require(!target.exists())
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try { output.write(raw); atomic.finishWrite(output) } catch (e: Exception) { atomic.failWrite(output); throw e }
        target
    }
    fun records(): List<File> = folder.listFiles()?.filter { it.name.endsWith(".json") && ID.matches(it.name.removeSuffix(".json")) }
        ?.sortedBy { it.lastModified() }.orEmpty()
    fun pending(): List<File> = records().filter { !File(folder, it.nameWithoutExtension + ".sent").exists() }
    fun markSent(record: JSONObject, receipt: JSONObject) {
        require(receipt.optBoolean("stored") && receipt.optString("recordId") == record.getString("recordId"))
        val atomic = AtomicFile(File(folder, record.getString("recordId") + ".sent"))
        val out = atomic.startWrite()
        try { out.write(receipt.toString().toByteArray()); atomic.finishWrite(out) }
        catch (e: Exception) { atomic.failWrite(out); throw e }
    }
    companion object {
        private val lock = Any()
        const val MAX_BYTES = 4 * 1024 * 1024
        val ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}

internal object ElementReferenceUpload {
    const val ENDPOINT = "https://aruno-tiker-elements.45kikurage.workers.dev/v1/references"
    private val busy = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    fun send(context: Context) {
        val app = context.applicationContext
        fun notice(message: String) { main.post { Toast.makeText(app, message, Toast.LENGTH_LONG).show() } }
        if (!busy.compareAndSet(false, true)) { notice("送信中です"); return }
        executor.execute {
            var sent = 0
            try {
                val store = ElementReferenceStore(app)
                val pending = store.pending().take(20)
                if (pending.isEmpty()) { notice("未送信の参考データはありません"); return@execute }
                notice("参考データ${pending.size}件を送信します")
                for (file in pending) {
                    val raw = file.readBytes()
                    require(raw.size <= ElementReferenceStore.MAX_BYTES)
                    val record = JSONObject(raw.toString(Charsets.UTF_8))
                    val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 10000; connection.readTimeout = 20000
                        connection.instanceFollowRedirects = false
                        connection.requestMethod = "POST"; connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        connection.setFixedLengthStreamingMode(raw.size)
                        connection.outputStream.use { it.write(raw) }
                        require(connection.responseCode in 200..201) { "HTTP ${connection.responseCode}" }
                        val receipt = JSONObject(connection.inputStream.use { stream ->
                            val bytes = stream.readBytesBounded(4096)
                            bytes.toString(Charsets.UTF_8)
                        })
                        store.markSent(record, receipt); sent++
                    } finally { connection.disconnect() }
                }
                notice("中央へ${sent}件送信しました。未送信${store.pending().size}件")
            } catch (_: Exception) { notice("${sent}件送信。残りは端末に保持しました。通信後に再送できます") }
            finally { busy.set(false) }
        }
    }
}

private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val buffer = ByteArray(limit + 1)
    var count = 0
    while (count <= limit) {
        val n = read(buffer, count, buffer.size - count)
        if (n < 0) return buffer.copyOf(count)
        count += n
    }
    error("応答が大きすぎます")
}
