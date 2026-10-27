package jp.aruno.clicker.data

/**
 * URLランナー一式の JSON 変換。
 *
 * Android の org.json に依存せず、端末内保存・送受信・単体テストで
 * 同じ変換を使うため、この固定形式専用の小さな JSON 実装を持つ。
 * 対応する JSON は object / array / string / number / boolean / null のみ。
 */
object UrlRunnerJson {
    /** サーバーへ送る形式（schemaVersion 3）。expectedConfigVersion は任意で付ける。 */
    fun encodeRequest(config: UrlRunnerConfig, expectedConfigVersion: Long?): String {
        val fields = mutableListOf<String>()
        fields += "\"urls\":" + encodeStringArray(config.urls)
        fields += "\"individualSeconds\":" + config.individualSeconds.joinToString(",", "[", "]")
        fields += "\"commonSeconds\":" + config.commonSeconds
        fields += "\"loopMode\":\"${config.loopMode.name}\""
        fields += "\"waitFromForeground\":" + config.waitFromForeground
        fields += "\"startIndex\":" + config.startIndex
        fields += "\"schemaVersion\":3"
        if (expectedConfigVersion != null) {
            fields += "\"expectedConfigVersion\":" + expectedConfigVersion
        }
        return fields.joinToString(",", "{", "}")
    }

    /** 端末内保存・サーバー応答の共通形式。 */
    fun encodeConfig(config: UrlRunnerConfig): String {
        val fields = mutableListOf<String>()
        fields += "\"urls\":" + encodeStringArray(config.urls)
        fields += "\"individualSeconds\":" + config.individualSeconds.joinToString(",", "[", "]")
        fields += "\"commonSeconds\":" + config.commonSeconds
        fields += "\"loopMode\":\"${config.loopMode.name}\""
        fields += "\"waitFromForeground\":" + config.waitFromForeground
        fields += "\"startIndex\":" + config.startIndex
        fields += "\"configVersion\":" + config.configVersion
        fields += "\"updatedAt\":" + encodeString(config.updatedAt)
        fields += "\"schemaVersion\":3"
        return fields.joinToString(",", "{", "}")
    }

    /**
     * JSON 文字列を [UrlRunnerConfig] へ変換する。
     * 形式が不正な場合は例外ではなく null を返す（呼び出し側は既存の保存済み設定を保持すること）。
     * ここでは件数・秒数などの業務検証は行わない（[UrlRunnerConfig.validate] の責務）。
     */
    fun decodeConfig(json: String): UrlRunnerConfig? {
        val root = runCatching { MiniJson.parse(json) }.getOrNull() as? JsonValue.Obj ?: return null
        val source = (root.values["config"] as? JsonValue.Obj) ?: root
        val urlsValue = source.values["urls"] as? JsonValue.Arr ?: return null
        val urls = urlsValue.items.map { item -> (item as? JsonValue.Str)?.value ?: return null }
        val secondsValue = source.values["individualSeconds"] as? JsonValue.Arr ?: return null
        val individualSeconds = secondsValue.items.map { item ->
            (item as? JsonValue.Num)?.value?.toIntOrNull() ?: return null
        }
        val commonSeconds = (source.values["commonSeconds"] as? JsonValue.Num)?.value?.toIntOrNull()
            ?: return null
        val configVersion = (source.values["configVersion"] as? JsonValue.Num)?.value?.toLongOrNull()
            ?: 0L
        val updatedAt = (source.values["updatedAt"] as? JsonValue.Str)?.value ?: ""
        val loopMode = when (val value = source.values["loopMode"]) {
            null -> LoopMode.STOP
            is JsonValue.Str -> runCatching { LoopMode.valueOf(value.value) }.getOrNull() ?: return null
            else -> return null
        }
        val waitFromForeground = when (val value = source.values["waitFromForeground"]) {
            null -> true
            is JsonValue.Bool -> value.value
            else -> return null
        }
        val startIndex = when (val value = source.values["startIndex"]) {
            null -> 0
            is JsonValue.Num -> value.value.toIntOrNull() ?: return null
            else -> return null
        }
        val schema = source.values["schemaVersion"]
        if (schema !is JsonValue.Num || schema.value != "3") return null
        return UrlRunnerConfig(
            loopMode = loopMode, waitFromForeground = waitFromForeground, startIndex = startIndex,
            urls = urls,
            individualSeconds = individualSeconds,
            commonSeconds = commonSeconds,
            configVersion = configVersion,
            updatedAt = updatedAt,
        )
    }

    private fun encodeStringArray(values: List<String>): String =
        values.joinToString(",", "[", "]") { encodeString(it) }

    private fun encodeString(value: String): String {
        val builder = StringBuilder(value.length + 2)
        builder.append('"')
        for (char in value) {
            when (char) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                else -> {
                    if (char.code < 0x20) {
                        builder.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        builder.append(char)
                    }
                }
            }
        }
        builder.append('"')
        return builder.toString()
    }
}

private sealed interface JsonValue {
    data class Obj(val values: Map<String, JsonValue>) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val value: String) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

/** 固定形式の読み取りに十分な最小 JSON パーサ。不正な入力では例外を投げる。 */
private object MiniJson {
    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.isEnd()) throw IllegalArgumentException("trailing characters")
        return value
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun isEnd(): Boolean = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun parseValue(): JsonValue {
            skipWhitespace()
            if (isEnd()) throw IllegalArgumentException("unexpected end")
            return when (text[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.Str(parseString())
                't' -> parseLiteral("true", JsonValue.Bool(true))
                'f' -> parseLiteral("false", JsonValue.Bool(false))
                'n' -> parseLiteral("null", JsonValue.Null)
                else -> parseNumber()
            }
        }

        private fun parseObject(): JsonValue.Obj {
            expect('{')
            val values = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (consume('}')) return JsonValue.Obj(values)
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                if (values.containsKey(key)) throw IllegalArgumentException("duplicate key")
                values[key] = parseValue()
                skipWhitespace()
                if (consume('}')) return JsonValue.Obj(values)
                expect(',')
            }
        }

        private fun parseArray(): JsonValue.Arr {
            expect('[')
            val items = mutableListOf<JsonValue>()
            skipWhitespace()
            if (consume(']')) return JsonValue.Arr(items)
            while (true) {
                items += parseValue()
                skipWhitespace()
                if (consume(']')) return JsonValue.Arr(items)
                expect(',')
            }
        }

        private fun parseString(): String {
            expect('"')
            val builder = StringBuilder()
            while (index < text.length) {
                val char = text[index++]
                when (char) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        if (index >= text.length) break
                        when (val escaped = text[index++]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (index + 4 > text.length) throw IllegalArgumentException("bad unicode escape")
                                val hex = text.substring(index, index + 4)
                                index += 4
                                builder.append(hex.toInt(16).toChar())
                            }
                            else -> throw IllegalArgumentException("bad escape: $escaped")
                        }
                    }
                    else -> { require(char.code >= 0x20); builder.append(char) }
                }
            }
            throw IllegalArgumentException("unterminated string")
        }

        private fun parseNumber(): JsonValue.Num {
            val start = index
            while (index < text.length && text[index] in "-+0123456789.eE") index++
            if (start == index) throw IllegalArgumentException("invalid value at $index")
            return JsonValue.Num(text.substring(start, index))
        }

        private fun parseLiteral(literal: String, value: JsonValue): JsonValue {
            if (!text.startsWith(literal, index)) throw IllegalArgumentException("invalid literal at $index")
            index += literal.length
            return value
        }

        private fun expect(char: Char) {
            skipWhitespace()
            if (isEnd() || text[index] != char) throw IllegalArgumentException("expected '$char' at $index")
            index++
        }

        private fun consume(char: Char): Boolean {
            if (!isEnd() && text[index] == char) {
                index++
                return true
            }
            return false
        }
    }
}
