package com.nerve.android.domain.server

import com.nerve.android.transport.ServerConfig

interface ServerConfigStore {
    suspend fun load(): List<ServerConfig>
    suspend fun saveAll(configs: List<ServerConfig>)
    suspend fun upsert(config: ServerConfig)
    suspend fun remove(serverId: String)
}
