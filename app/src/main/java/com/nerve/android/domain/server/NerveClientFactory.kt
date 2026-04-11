package com.nerve.android.domain.server

import com.nerve.android.transport.NerveClient

fun interface NerveClientFactory {
    fun create(serverId: String): NerveClient
}
