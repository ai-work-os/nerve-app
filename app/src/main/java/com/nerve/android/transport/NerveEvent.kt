package com.nerve.android.transport

import kotlinx.serialization.json.JsonObject

sealed interface NerveEvent {
    data class ChannelMessage(
        val channelId: String,
        val messageId: String?,
        val payload: JsonObject,
    ) : NerveEvent

    data class ChannelMention(
        val channelId: String,
        val payload: JsonObject,
    ) : NerveEvent

    data class NodeJoined(
        val channelId: String,
        val nodeId: String,
        val name: String?,
    ) : NerveEvent

    data class NodeLeft(
        val channelId: String,
        val nodeId: String,
        val name: String?,
    ) : NerveEvent

    data class NodeUpdate(
        val nodeId: String,
        val name: String,
        val detail: JsonObject,
    ) : NerveEvent

    data class NodeStatusChanged(
        val nodeId: String,
        val status: String,
        val detail: JsonObject,
    ) : NerveEvent

    data class NodeRegistered(
        val nodeId: String,
        val name: String?,
    ) : NerveEvent

    data class NodeStopped(
        val nodeId: String,
    ) : NerveEvent

    data class ChannelCreated(
        val channelId: String,
        val name: String,
    ) : NerveEvent

    data class ChannelClosed(
        val channelId: String,
        val name: String?,
    ) : NerveEvent
}
