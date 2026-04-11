package com.nerve.android.domain.server

import com.nerve.android.transport.NerveEvent

data class ServerScopedEvent(
    val serverId: String,
    val event: NerveEvent,
)
