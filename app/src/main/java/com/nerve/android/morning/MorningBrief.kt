package com.nerve.android.morning

import kotlinx.serialization.Serializable

@Serializable
data class MorningBrief(
    val date: String,
    val sourceDate: String,
    val generatedAt: String,
    val notificationTitle: String,
    val notificationBody: String,
    val markdown: String,
    val sections: List<MorningBriefSection> = emptyList(),
    val sources: List<MorningBriefSource> = emptyList(),
)

@Serializable
data class MorningBriefSection(
    val title: String,
    val items: List<String> = emptyList(),
)

@Serializable
data class MorningBriefSource(
    val kind: String,
    val path: String,
    val available: Boolean,
    val count: Int? = null,
)
