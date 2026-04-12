package com.nerve.android.transport

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Assembled DM message shipped in a [NerveEvent.MessageSnapshot]. Mirror of
 * the server-side `Message` type (nerve/src/protocol.ts).
 */
@Serializable
data class SnapshotMessage(
    val id: String,
    val nodeId: String,
    val role: String,
    val sender: String,
    val text: String,
    val ts: Double,
)

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

    /**
     * Full assembled DM history for a node. Delivered on subscribe
     * (first time and on reconnect-resubscribe). Replaces the client's
     * local view for this node.
     */
    data class MessageSnapshot(
        val nodeId: String,
        val name: String,
        val messages: List<SnapshotMessage>,
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
