package com.nerve.android.domain.channel

sealed interface ChannelMappedEvent {
    data class MessageReceived(val channelId: String, val message: ChannelMessage) : ChannelMappedEvent
    data class MentionReceived(val channelId: String, val message: ChannelMessage) : ChannelMappedEvent
    data class ChannelCreated(val channelId: String, val name: String) : ChannelMappedEvent
    data class ChannelClosed(val channelId: String, val name: String?) : ChannelMappedEvent
    data object Ignore : ChannelMappedEvent
}
