package jp.aruno.clicker.automation

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

internal object PopupDiagnostics {
    private fun folder(context: Context) = File(context.filesDir, "popup-diagnostics")
    @Synchronized fun save(context: Context, data: JSONObject) {
        val dir = folder(context).apply { mkdirs() }
        File(dir, "popup-${System.currentTimeMillis()}.json").writeText(data.toString(2))
        dir.listFiles()?.sortedByDescending { it.name }?.drop(3)?.forEach { it.delete() }
    }
    fun share(context: Context) {
        val file = folder(context).listFiles()?.maxByOrNull { it.name }
        if (file == null) {
            Toast.makeText(context, "ポップアップの記録はまだありません", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".popup-diagnostics", file)
        val intent = Intent(Intent.ACTION_SEND).setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = android.content.ClipData.newRawUri("ポップアップ診断", uri) }
        runCatching { context.startActivity(Intent.createChooser(intent, "×解除の診断を共有")) }
            .onFailure { Toast.makeText(context, "共有先を開けませんでした", Toast.LENGTH_LONG).show() }
    }
}
