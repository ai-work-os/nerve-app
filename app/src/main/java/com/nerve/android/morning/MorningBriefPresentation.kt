package com.nerve.android.morning

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class MorningBriefPresentation(
    val title: String,
    val sourceLabel: String,
    val summary: String,
    val sections: List<MorningBriefSection>,
    val statusMessage: String?,
) {
    companion object {
        fun from(brief: MorningBrief?, loading: Boolean, error: String?): MorningBriefPresentation {
            val cleanSections = brief?.sections.orEmpty().map { section ->
                section.copy(items = section.items.map(::cleanItem).filter { it.isNotBlank() })
            }
            val summary = brief?.notificationBody
                ?.let(::cleanItem)
                ?.let { it.takeWithEllipsis(90) }
                .orEmpty()

            return MorningBriefPresentation(
                title = brief?.date?.let(::formatTitle) ?: "早报",
                sourceLabel = brief?.sourceDate?.let { "基于 $it 的记录" }.orEmpty(),
                summary = summary,
                sections = cleanSections,
                statusMessage = friendlyStatus(loading, error),
            )
        }

        private fun formatTitle(date: String): String {
            return runCatching {
                val parsed = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
                "${parsed.monthValue}月${parsed.dayOfMonth}日早报"
            }.getOrElse { "早报 $date" }
        }

        private fun friendlyStatus(loading: Boolean, error: String?): String? {
            if (loading) return "正在更新早报..."
            if (error.isNullOrBlank()) return null
            return when {
                error.contains("failed to connect", ignoreCase = true) ||
                    error.contains("timeout", ignoreCase = true) ||
                    error.contains("timed out", ignoreCase = true) -> "暂时连不上服务器，稍后会自动重试。"
                else -> "早报更新失败，稍后会自动重试。"
            }
        }

        private fun cleanItem(raw: String): String {
            var text = raw
                .replace("**", "")
                .replace("`", "")
                .replace(Regex("/(?:home|tmp|Users)/\\S+"), "")
                .replace(Regex("\\bworktree\\b", RegexOption.IGNORE_CASE), "workspace")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (text.contains("conversation-archive")) {
                val count = Regex("home:\\s*(\\d+)\\s*条").find(text)?.groupValues?.getOrNull(1)
                text = if (count != null) "会话归档完成: home $count 条" else "会话归档完成"
            }
            return text.trim(' ', '—', '-')
        }

        private fun String.takeWithEllipsis(limit: Int): String {
            if (length <= limit) return this
            return take(limit - 1).trimEnd(' ', '；', '，', ',', '。') + "…"
        }
    }
}
