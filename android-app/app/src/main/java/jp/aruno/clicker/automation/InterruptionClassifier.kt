package jp.aruno.clicker.automation

internal object InterruptionClassifier {
    private val amountPattern = Regex("""\d{1,3}(?:,\d{3})+円分""")

    fun isContactsPermissionDialog(labels: Collection<String>): Boolean {
        val normalized = labels.map(::normalize).filter(String::isNotEmpty)
        val joined = normalized.joinToString(" ")
        return joined.contains("連絡先") &&
            joined.contains("アクセス") &&
            joined.contains("tiktok", ignoreCase = true) &&
            normalized.any { it == "許可" } &&
            normalized.any { it == "許可しない" }
    }

    fun isContactsDenyAction(text: CharSequence?, description: CharSequence?): Boolean =
        sequenceOf(text, description)
            .mapNotNull { it?.toString() }
            .map(::normalize)
            .any { it == "許可しない" }

    fun isCoinStockPopup(labels: Collection<String>): Boolean {
        val normalized = labels.map(::normalize).filter(String::isNotEmpty)
        val joined = normalized.joinToString(" ")
        return joined.contains("コインストック") &&
            joined.contains("ゲットしよう") &&
            amountPattern.containsMatchIn(joined) &&
            normalized.any { it == "始める" }
    }

    fun isCoinStockStartAction(text: CharSequence?, description: CharSequence?): Boolean =
        sequenceOf(text, description)
            .mapNotNull { it?.toString() }
            .map(::normalize)
            .any { it == "始める" }

    private fun normalize(value: String): String = value
        .replace(Regex("""\s+"""), "")
        .trim()
}

internal object RecentsDismissPolicy {
    fun missingTargetIsSuccess(dismissedCount: Int): Boolean = dismissedCount > 0

    fun targetRemovalSucceeded(targetStillVisibleAfterGesture: Boolean): Boolean =
        !targetStillVisibleAfterGesture
}

internal object TargetForegroundPolicy {
    fun shouldForceLaunch(missingDurationMs: Long, thresholdMs: Long): Boolean =
        missingDurationMs >= thresholdMs
}
