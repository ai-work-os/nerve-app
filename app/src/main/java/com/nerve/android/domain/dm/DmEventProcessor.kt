package com.nerve.android.domain.dm

import com.nerve.android.transport.NerveEvent
import com.nerve.android.util.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

interface DmEventProcessor {
    suspend fun attach(serverId: String, nodeId: String, events: Flow<NerveEvent>)
}

class DefaultDmEventProcessor(
    private val store: DmStore,
    private val mapper: DmEventMapper = DmEventMapper(),
    private val accumulator: StreamingAccumulator = InMemoryStreamingAccumulator(),
) : DmEventProcessor {
    private val activeKeys = ConcurrentHashMap.newKeySet<DmKey>()

    override suspend fun attach(serverId: String, nodeId: String, events: Flow<NerveEvent>) {
        val key = DmKey("$serverId:$nodeId")
        if (!activeKeys.add(key)) {
            Logger.w("DmEventProcessor", "dm ignore key=${key.value} reason=duplicate_attach")
            return
        }
        try {
            events.collect { event ->
                handleEvent(key, nodeId, event)
            }
        } finally {
            activeKeys.remove(key)
        }
    }

    private fun handleEvent(key: DmKey, targetNodeId: String, event: NerveEvent) {
        val eventNodeId = when (event) {
            is NerveEvent.NodeUpdate -> event.nodeId
            is NerveEvent.NodeStatusChanged -> event.nodeId
            else -> null
        }
        if (eventNodeId != targetNodeId) return
        when (val mapped = mapper.map(event)) {
            is DmMappedEvent.UserMessage -> {
                Logger.d("DmEventProcessor", "dm event key=${key.value} kind=user_message")
                accumulator.flush(key, mapped.timestamp, FlushReason.USER_MESSAGE)?.let { store.appendMessage(key, it) }
                store.appendMessage(
                    key,
                    DmMessage(
                        id = mapped.messageId,
                        role = DmRole.USER,
                        content = mapped.content,
                        timestamp = mapped.timestamp,
                        nodeId = mapped.nodeId,
                        nodeName = mapped.nodeName,
                    ),
                )
            }

            is DmMappedEvent.AgentMessageStart -> {
                Logger.d("DmEventProcessor", "dm event key=${key.value} kind=agent_message_start")
                accumulator.onStart(key, mapped.nodeId, mapped.nodeName, mapped.messageId, mapped.timestamp)
            }

            is DmMappedEvent.AgentMessageChunk -> {
                Logger.d("DmEventProcessor", "dm event key=${key.value} kind=agent_message_chunk")
                accumulator.onChunk(key, mapped.text, mapped.timestamp)
            }

            is DmMappedEvent.AgentMessageEnd -> {
                Logger.d("DmEventProcessor", "dm event key=${key.value} kind=agent_message_end")
                accumulator.onEnd(key, mapped.fallbackText, mapped.timestamp)?.let { store.appendMessage(key, it) }
            }

            is DmMappedEvent.NodeIdle -> {
                Logger.d("DmEventProcessor", "dm event key=${key.value} kind=node_idle")
                accumulator.flush(key, mapped.timestamp, FlushReason.NODE_IDLE)?.let { store.appendMessage(key, it) }
            }

            DmMappedEvent.Ignore -> Unit
        }
    }
}
