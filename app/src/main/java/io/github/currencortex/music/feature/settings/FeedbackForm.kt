package io.github.currencortex.music.feature.settings

internal data class FeedbackField(
    val id: String,
    val label: String,
    val required: Boolean = true,
    val options: List<String> = emptyList(),
    val multiline: Boolean = false,
    val placeholder: String = "",
)

private val titleField = FeedbackField("title", "标题")
internal const val FEEDBACK_AUDIO_OUTPUT_MODULE = "音频输出"
private val moduleField = FeedbackField(
    "module",
    "问题模块",
    options = listOf("播放与音效", "歌词", "音源与网络", "界面与外观", "下载与缓存", "其他"),
)

internal enum class FeedbackTemplate(val title: String, val fields: List<FeedbackField>) {
    BUG(
        "报告问题",
        listOf(
            titleField,
            FeedbackField("version", "应用版本", placeholder = "例如：1.3.5-ci.264 (3800264)"),
            FeedbackField("device", "设备与 Android 版本", placeholder = "例如：小米 15 / Android 17 / HyperOS ..."),
            FeedbackField("build", "安装来源", options = listOf("GitHub 正式版", "测试版", "自行构建", "其他")),
            moduleField.copy(options = moduleField.options.toMutableList().apply { add(1, FEEDBACK_AUDIO_OUTPUT_MODULE) }),
            FeedbackField(
                "steps",
                "复现步骤",
                multiline = true,
                placeholder = "1. 打开「…」\n2. 进入「…」并点击「…」\n3. 出现问题",
            ),
            FeedbackField("result", "实际与预期结果", multiline = true, placeholder = "实际结果：……\n\n预期结果：……"),
            FeedbackField(
                "frequency",
                "出现频率",
                required = false,
                options = listOf("不确定", "每次都出现", "经常出现", "偶尔出现", "只发生过一次"),
            ),
            FeedbackField(
                "environment",
                "相关设置与使用环境（可选）",
                required = false,
                multiline = true,
                placeholder = "例如：网易云原生音源、USB 独占输出、SNOWSKY TINY A",
            ),
            FeedbackField(
                "diagnostics",
                "截图与诊断信息",
                required = false,
                multiline = true,
                placeholder = "可填写错误提示或已脱敏的诊断说明",
            ),
        ),
    ),
    FEATURE(
        "功能建议",
        listOf(
            titleField,
            moduleField,
            FeedbackField("problem", "需求与使用场景", multiline = true, placeholder = "例如：在……场景下，我需要……，但当前只能……"),
            FeedbackField("proposal", "建议方案", multiline = true, placeholder = "希望在……页面增加……，点击后可以……"),
            FeedbackField(
                "alternatives",
                "替代方案与补充信息",
                required = false,
                multiline = true,
                placeholder = "替代方案、示例截图或补充说明（可选）",
            ),
        ),
    ),
}

internal data class FeedbackDraft(
    val template: FeedbackTemplate,
    val values: Map<String, String> = emptyMap(),
) {
    val complete: Boolean
        get() = template.fields.all { !it.required || fieldValue(it).isNotBlank() }

    fun content(): String = buildString {
        append(template.prefix)
        val title = fieldValue(titleField)
        if (title.isNotEmpty()) append(' ').append(title)
        val headings = template.bodyFields.map { it.heading }.toSet()
        for (field in template.bodyFields) {
            append("\n\n").append(field.heading)
            val value = fieldValue(field)
            if (value.isNotEmpty()) {
                append("\n\n")
                append(value.lineSequence().joinToString("\n") { line ->
                    // Keep a heading typed into a value from being parsed as a section boundary.
                    if (line.trimStart('\\') in headings) "\\$line" else line
                })
            }
        }
    }

    private fun fieldValue(field: FeedbackField): String {
        val value = values[field.id].orEmpty()
        return if (field == titleField) value.singleLineTitle() else value.bodyValue()
    }

    companion object {
        fun fromContent(content: String): FeedbackDraft {
            if (content.isFeedbackBlank()) return FeedbackDraft(FeedbackTemplate.BUG)
            val lines = content.lineSequence().toList()
            val firstLine = lines.first().trimEnd()
            val template = FeedbackTemplate.entries.firstOrNull {
                firstLine == it.prefix || firstLine.startsWith("${it.prefix} ")
            }
            if (template != null) {
                val fields = template.bodyFields
                val headings = fields.map { it.heading }
                val boundaries = lines.indices.filter { lines[it] in headings }
                // A type prefix alone can also occur in an old free-text submission.
                if (boundaries.firstOrNull() == 2 && lines[1].isEmpty() && boundaries.map { lines[it] } == headings) {
                    val values = buildMap {
                        val title = firstLine.removePrefix(template.prefix).singleLineTitle()
                        if (title.isNotEmpty()) put(titleField.id, title)
                        for ((index, field) in fields.withIndex()) {
                            val end = boundaries.getOrElse(index + 1) { lines.size }
                            val value = lines.subList(boundaries[index] + 1, end).joinToString("\n") { line ->
                                if (line.startsWith('\\') && line.trimStart('\\') in headings) line.drop(1) else line
                            }.bodyValue()
                            if (value.isNotEmpty()) put(field.id, value)
                        }
                    }
                    return FeedbackDraft(template, values)
                }
            }
            return FeedbackDraft(FeedbackTemplate.BUG, mapOf("result" to content))
        }
    }
}

private val FeedbackTemplate.prefix: String
    get() = when (this) {
        FeedbackTemplate.BUG -> "[Bug]"
        FeedbackTemplate.FEATURE -> "[Feature]"
    }

private val FeedbackTemplate.bodyFields: List<FeedbackField>
    get() = fields.drop(1)

private val FeedbackField.heading: String
    get() = "### $label"

private val titleLineBreaks = Regex("\\R")

private fun String.singleLineTitle(): String = titleLineBreaks.splitToSequence(this)
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .joinToString(" ")

private fun String.bodyValue(): String = lineSequence()
    .dropWhile { it.isFeedbackBlank() }
    .joinToString("\n")
    .trimEnd { it.isWhitespace() || it == '\u0085' }

private fun String.isFeedbackBlank(): Boolean = all { it.isWhitespace() || it == '\u0085' }
