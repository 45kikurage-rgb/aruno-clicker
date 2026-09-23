package jp.aruno.clicker.automation

internal object ContentClassifier {
    const val AD = "広告"
    const val PHOTO = "写真"
    const val TEXT_IMAGE = "テキスト画像"
    const val PAGE_DOTS = "ページ表示"
    const val NO_GAUGE = "サークルゲージなし"
    const val CLASSIFICATION_TIMEOUT = "判定待機5秒"

    private val whitespace = Regex("""\s+""")
    private val photoCounter = Regex(
        """(?<!\d)[1-9]\d?\s*[/／]\s*(?:[2-9]|[1-9]\d)(?:枚|ページ)?(?!\d)""",
    )
    private val photoBadge = Regex(
        """(?:^|[\s,、・|])(?:写真|フォト|photo)(?:$|[\s,、・|])""",
        RegexOption.IGNORE_CASE,
    )

    private val adLabels = setOf(
        "広告",
        "スポンサー",
        "sponsored",
        "promoted",
        "advertisement",
        "プロモーション",
        "詳しくはこちら",
        "今すぐ購入",
        "shop now",
    )

    private val photoLabels = setOf(
        "写真",
        "写真投稿",
        "フォト",
        "写真モード",
        "フォトモード",
        "photo",
        "photo mode",
    )

    private val textImageLabels = setOf(
        "テキスト",
        "テキスト画像",
        "テキスト画像を作成",
        "text",
        "text image",
    )

    fun classify(labels: Iterable<String>): String? {
        var photoFound = false
        labels.forEach { rawLabel ->
            val label = rawLabel.trim().replace(whitespace, " ")
            if (label.isEmpty()) return@forEach
            val comparable = label.lowercase()
            if (comparable in adLabels) return AD
            if (comparable in textImageLabels) return TEXT_IMAGE
            if (
                comparable in photoLabels ||
                (label.length <= MAX_COMPOSITE_LABEL_LENGTH && photoBadge.containsMatchIn(label)) ||
                (label.length <= MAX_COUNTER_LABEL_LENGTH && photoCounter.containsMatchIn(label))
            ) {
                photoFound = true
            }
        }
        return if (photoFound) PHOTO else null
    }

    private const val MAX_COMPOSITE_LABEL_LENGTH = 40
    private const val MAX_COUNTER_LABEL_LENGTH = 24
}
