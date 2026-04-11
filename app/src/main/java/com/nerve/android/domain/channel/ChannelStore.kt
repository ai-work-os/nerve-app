package com.nerve.android.domain.channel

import com.nerve.android.util.Logger
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ChannelStore {
    fun messages(key: ChannelKey): StateFlow<List<ChannelMessage>>
    fun meta(key: ChannelKey): StateFlow<ChannelMeta?>
    fun appendMessage(key: ChannelKey, message: ChannelMessage): Boolean
    fun upsertMeta(key: ChannelKey, meta: ChannelMeta)
    fun containsMessage(key: ChannelKey, messageId: String): Boolean
}

class InMemoryChannelStore : ChannelStore {
    private val messageFlows = ConcurrentHashMap<ChannelKey, MutableStateFlow<List<ChannelMessage>>>()
    private val metaFlows = ConcurrentHashMap<ChannelKey, MutableStateFlow<ChannelMeta?>>()
    private val seenIds = ConcurrentHashMap<ChannelKey, LinkedHashSet<String>>()
    private val locks = ConcurrentHashMap<ChannelKey, Any>()

    override fun messages(key: ChannelKey): StateFlow<List<ChannelMessage>> = messageFlowFor(key).asStateFlow()

    override fun meta(key: ChannelKey): StateFlow<ChannelMeta?> = metaFlowFor(key).asStateFlow()

    override fun appendMessage(key: ChannelKey, message: ChannelMessage): Boolean {
        synchronized(lockFor(key)) {
            val ids = seenIds.getOrPut(key) { LinkedHashSet() }
            if (!ids.add(message.id)) {
                Logger.d("ChannelStore", "channel dedup key=${key.value} id=${message.id}")
                return false
            }
            val flow = messageFlowFor(key)
            flow.value = flow.value + message
            Logger.d(
                "ChannelStore",
                "channel append key=${key.value} id=${message.id} from=${message.from} ts=${message.timestamp}",
            )
            return true
        }
    }

    override fun upsertMeta(key: ChannelKey, meta: ChannelMeta) {
        synchronized(lockFor(key)) {
            metaFlowFor(key).value = meta
            Logger.d("ChannelStore", "channel meta key=${key.value} name=${meta.name} closed=${meta.isClosed}")
        }
    }

    override fun containsMessage(key: ChannelKey, messageId: String): Boolean =
        synchronized(lockFor(key)) { seenIds[key]?.contains(messageId) == true }

    private fun messageFlowFor(key: ChannelKey): MutableStateFlow<List<ChannelMessage>> =
        messageFlows.getOrPut(key) { MutableStateFlow(emptyList()) }

    private fun metaFlowFor(key: ChannelKey): MutableStateFlow<ChannelMeta?> =
        metaFlows.getOrPut(key) { MutableStateFlow(null) }

    private fun lockFor(key: ChannelKey): Any = locks.getOrPut(key) { Any() }
}
