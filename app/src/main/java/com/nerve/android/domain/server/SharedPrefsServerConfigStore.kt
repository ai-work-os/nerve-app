package com.nerve.android.domain.server

import android.content.SharedPreferences
import com.nerve.android.transport.ServerConfig
import com.nerve.android.util.Logger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class SharedPrefsServerConfigStore(
    private val prefs: SharedPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ServerConfigStore {
    override suspend fun load(): List<ServerConfig> {
        val raw = prefs.getString(KEY_SERVER_CONFIGS_JSON, null)
        val configs = if (raw.isNullOrBlank()) {
            defaultConfigs().also {
                saveAllSync(it)
                Logger.d("ServerConfigStore", "server default_injected id=local")
            }
        } else {
            runCatching {
                json.decodeFromString(ListSerializer(ServerConfig.serializer()), raw)
            }.getOrElse {
                Logger.e("ServerConfigStore", "server load_failed", it)
                defaultConfigs().also {
                    saveAllSync(it)
                    Logger.d("ServerConfigStore", "server default_injected id=local")
                }
            }
        }
        Logger.d("ServerConfigStore", "server load count=${configs.size}")
        return configs
    }

    override suspend fun saveAll(configs: List<ServerConfig>) {
        saveAllSync(configs)
    }

    override suspend fun upsert(config: ServerConfig) {
        val updated = load().filterNot { it.id == config.id } + config
        saveAllSync(updated)
    }

    override suspend fun remove(serverId: String) {
        val updated = load().filterNot { it.id == serverId }
        saveAllSync(updated)
    }

    private fun saveAllSync(configs: List<ServerConfig>) {
        val raw = json.encodeToString(ListSerializer(ServerConfig.serializer()), configs)
        prefs.edit().putString(KEY_SERVER_CONFIGS_JSON, raw).commit()
    }

    private fun defaultConfigs(): List<ServerConfig> =
        listOf(
            ServerConfig(id = "mac", name = "Mac", address = "100.109.126.37:4800"),
            ServerConfig(id = "home", name = "Home Server", address = "100.75.43.90:4800"),
        )

    companion object {
        const val PREFS_NAME: String = "nerve_servers"
        const val KEY_SERVER_CONFIGS_JSON: String = "server_configs_json"
    }
}
