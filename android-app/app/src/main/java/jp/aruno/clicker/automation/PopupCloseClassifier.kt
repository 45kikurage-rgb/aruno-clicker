package jp.aruno.clicker.automation

internal object PopupCloseClassifier {
    private val exactSymbols = setOf("×", "✕", "✖", "✗", "❌")
    private val exactWords = setOf("閉じる", "とじる", "close", "dismiss")
    private val resourceHints = listOf(
        "popup_close",
        "dialog_close",
        "close_button",
        "close_btn",
        "dismiss_button",
        "dismiss_btn",
    )

    fun score(text: CharSequence?, description: CharSequence?, viewId: String?): Int {
        val labels = sequenceOf(text, description)
            .mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            .toList()
        if (labels.any { it in exactSymbols }) return 100
        if (labels.any { it.lowercase() in exactWords }) return 90

        val normalizedId = viewId.orEmpty().lowercase()
        if (resourceHints.any(normalizedId::contains)) return 70
        return 0
    }
}
