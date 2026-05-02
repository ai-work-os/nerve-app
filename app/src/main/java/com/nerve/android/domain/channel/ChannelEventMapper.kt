package com.nerve.android.domain.channel

import com.nerve.android.transport.NerveEvent
import com.nerve.android.util.Logger
import java.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

interface ChannelEventMapper {
    fun map(event: NerveEvent): ChannelMappedEvent
}

class DefaultChannelEventMapper : ChannelEventMapper {
    override fun map(event: NerveEvent): ChannelMappedEvent = when (event) {
        is NerveEvent.ChannelMessage -> {
            mapMessage(
                channelId = event.channelId,
                payload = event.payload,
                topLevelId = event.messageId,
                isMention = false,
            )
        }

        is NerveEvent.ChannelMention -> {
            mapMessage(
                channelId = event.channelId,
                payload = event.payload,
                topLevelId = null,
                isMention = true,
            )
        }

        is NerveEvent.ChannelCreated -> ChannelMappedEvent.ChannelCreated(event.channelId, event.name)
        is NerveEvent.ChannelClosed -> ChannelMappedEvent.ChannelClosed(event.channelId, event.name)
        else -> ignore("unknown_event")
    }

    private fun mapMessage(
        channelId: String,
        payload: JsonObject,
        topLevelId: String?,
        isMention: Boolean,
    ): ChannelMappedEvent {
        val content = payload.stringPath("message", "content")
            ?: payload.stringPath("message", "text")
            ?: payload.string("content")
            ?: return ignore("missing_content")
        val from = payload.stringPath("message", "from")
            ?: payload.string("from")
            ?: payload.stringPath("message", "nodeName")
            ?: "unknown"
        val timestamp = payload.parseTimestamp()
            ?: stableFallbackTimestamp(
                channelId = channelId,
                from = from,
                content = content,
                rawId = topLevelId ?: payload.string("messageId") ?: payload.stringPath("message", "id"),
            )
        val payloadId = payload.string("messageId") ?: payload.stringPath("message", "id")
        if (topLevelId != null && payloadId != null && topLevelId != payloadId) {
            Logger.warn(
                "ChannelEventMapper",
                "channel_id_conflict",
                mapOf("channelId" to channelId, "top" to topLevelId, "payload" to payloadId),
            )
        }
        val id = topLevelId ?: payloadId ?: buildFallbackMessageId(channelId, timestamp, from, content)
        val message = ChannelMessage(
            id = id,
            channelId = channelId,
            from = from,
            content = content,
            timestamp = timestamp,
            metadata = payload,
        )
        return if (isMention) {
            ChannelMappedEvent.MentionReceived(channelId, message)
        } else {
            ChannelMappedEvent.MessageReceived(channelId, message)
        }
    }

    private fun ignore(reason: String): ChannelMappedEvent.Ignore {
        Logger.debug("ChannelEventMapper", "map_ignore", mapOf("reason" to reason))
        return ChannelMappedEvent.Ignore
    }
}

internal fun buildFallbackMessageId(channelId: String, timestamp: Long, from: String, content: String): String =
    "channel:$channelId:$timestamp:${from.hashCode()}:${content.hashCode()}"

private fun stableFallbackTimestamp(channelId: String, from: String, content: String, rawId: String?): Long {
    val seed = listOf(channelId, from, content, rawId.orEmpty()).joinToString(":")
    return abs(seed.fold(0L) { acc, c -> (acc * 131 + c.code) and Long.MAX_VALUE })
}

private fun JsonObject.parseTimestamp(): Long? {
    val candidates = listOfNotNull(
        this["message"]?.asObject()?.get("timestamp"),
        this["timestamp"],
        this["message"]?.asObject()?.get("ts"),
        this["ts"],
    )
    for (candidate in candidates) {
        candidate.asLongOrNull()?.let { return it }
        candidate.asStringOrNull()?.let { value ->
            value.toLongOrNull()?.let { return it }
            runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()?.let { return it }
        }
    }
    return null
}

private fun JsonObject.string(key: String): String? = this[key].asStringOrNull()

private fun JsonObject.stringPath(parent: String, child: String): String? =
    this[parent].asObject()?.get(child).asStringOrNull()

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.asStringOrNull(): String? = runCatching { this?.jsonPrimitive?.content }.getOrNull()

private fun JsonElement?.asLongOrNull(): Long? = when (this) {
    null -> null
    else -> this.asStringOrNull()?.toLongOrNull()
}
