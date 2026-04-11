package com.nerve.android.domain.dm

data class DmStreamingState(
    val messageId: String,
    val nodeId: String,
    val nodeName: String,
    val startedAt: Long,
    var lastEventAt: Long,
    val text: StringBuilder = StringBuilder(),
    val blocks: MutableList<ContentBlock> = mutableListOf(),
)
