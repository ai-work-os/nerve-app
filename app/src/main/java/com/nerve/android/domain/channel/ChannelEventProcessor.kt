package com.nerve.android.domain.channel

import com.nerve.android.transport.NerveEvent
import com.nerve.android.util.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

interface ChannelEventProcessor {
    suspend fun attach(serverId: String, events: Flow<NerveEvent>)
}

class RealChannelEventProcessor(
    private val store: ChannelStore,
    private val mapper: ChannelEventMapper = DefaultChannelEventMapper(),
) : ChannelEventProcessor {
    private val activeServers = ConcurrentHashMap.newKeySet<String>()

    override suspend fun attach(serverId: String, events: Flow<NerveEvent>) {
        if (!activeServers.add(serverId)) {
            Logger.warn(
                "ChannelEventProcessor",
                "channel_attach_ignore",
                mapOf("serverId" to serverId, "reason" to "duplicate_attach"),
            )
            return
        }
        val context = currentCoroutineContext()
        CoroutineScope(context.minusKey(context.job.key) + SupervisorJob()).launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                events.collect { event -> handleEvent(serverId, event) }
            } finally {
                activeServers.remove(serverId)
            }
        }
    }

    private fun handleEvent(serverId: String, event: NerveEvent) {
        when (val mapped = mapper.map(event)) {
            is ChannelMappedEvent.MessageReceived -> {
                val key = ChannelKey("$serverId:${mapped.channelId}")
                Logger.debug("ChannelEventProcessor", "channel_event", mapOf("key" to key.value, "kind" to "message"))
                store.appendMessage(key, mapped.message)
            }

            is ChannelMappedEvent.MentionReceived -> {
                val key = ChannelKey("$serverId:${mapped.channelId}")
                Logger.debug("ChannelEventProcessor", "channel_event", mapOf("key" to key.value, "kind" to "mention"))
                store.appendMessage(key, mapped.message)
            }

            is ChannelMappedEvent.ChannelCreated -> {
                val key = ChannelKey("$serverId:${mapped.channelId}")
                Logger.debug("ChannelEventProcessor", "channel_event", mapOf("key" to key.value, "kind" to "created"))
                val current = store.meta(key).value
                store.upsertMeta(
                    key,
                    ChannelMeta(
                        channelId = mapped.channelId,
                        name = mapped.name,
                        cwd = current?.cwd,
                        isClosed = false,
                    ),
                )
            }

            is ChannelMappedEvent.ChannelClosed -> {
                val key = ChannelKey("$serverId:${mapped.channelId}")
                Logger.debug("ChannelEventProcessor", "channel_event", mapOf("key" to key.value, "kind" to "closed"))
                val current = store.meta(key).value
                store.upsertMeta(
                    key,
                    ChannelMeta(
                        channelId = mapped.channelId,
                        name = mapped.name ?: current?.name,
                        cwd = current?.cwd,
                        isClosed = true,
                    ),
                )
            }

            ChannelMappedEvent.Ignore -> Unit
        }
    }
}
