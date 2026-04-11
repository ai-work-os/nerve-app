package com.nerve.android.domain.dm

import com.nerve.android.util.Logger
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface DmStore {
    fun messages(key: DmKey): StateFlow<List<DmMessage>>
    fun appendMessage(key: DmKey, message: DmMessage): Boolean
    fun containsMessage(key: DmKey, messageId: String): Boolean
}

class InMemoryDmStore : DmStore {
    private val messageFlows = ConcurrentHashMap<DmKey, MutableStateFlow<List<DmMessage>>>()
    private val seenIds = ConcurrentHashMap<DmKey, LinkedHashSet<String>>()
    private val locks = ConcurrentHashMap<DmKey, Any>()

    override fun messages(key: DmKey): StateFlow<List<DmMessage>> = flowFor(key).asStateFlow()

    override fun appendMessage(key: DmKey, message: DmMessage): Boolean {
        synchronized(lockFor(key)) {
            val ids = seenIds.getOrPut(key) { LinkedHashSet() }
            if (!ids.add(message.id)) {
                Logger.d("DmStore", "dm dedup key=${key.value} id=${message.id}")
                return false
            }
            val flow = flowFor(key)
            flow.value = flow.value + message
            Logger.d("DmStore", "dm append key=${key.value} id=${message.id} role=${message.role} ts=${message.timestamp}")
            return true
        }
    }

    override fun containsMessage(key: DmKey, messageId: String): Boolean =
        synchronized(lockFor(key)) { seenIds[key]?.contains(messageId) == true }

    private fun flowFor(key: DmKey): MutableStateFlow<List<DmMessage>> =
        messageFlows.getOrPut(key) { MutableStateFlow(emptyList()) }

    private fun lockFor(key: DmKey): Any = locks.getOrPut(key) { Any() }
}
