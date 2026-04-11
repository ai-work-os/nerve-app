package com.nerve.android.domain.server

import com.nerve.android.transport.ServerConfig
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SharedPrefsServerConfigStoreTest {
    @Test
    fun `first load injects default server and persists it`() = runTest {
        val prefs = InMemorySharedPreferences()
        val store = SharedPrefsServerConfigStore(prefs)

        val configs = store.load()

        assertEquals(
            listOf(ServerConfig(id = "local", name = "Local", address = "127.0.0.1:4800")),
            configs,
        )
        assertEquals(configs, SharedPrefsServerConfigStore(prefs).load())
    }

    @Test
    fun `upsert overwrite and remove survive store recreation`() = runTest {
        val prefs = InMemorySharedPreferences()
        val store = SharedPrefsServerConfigStore(prefs)

        store.upsert(ServerConfig(id = "home", name = "Home", address = "10.0.0.1:4800"))
        store.upsert(ServerConfig(id = "home", name = "Home 2", address = "10.0.0.2:4800"))
        store.remove("local")

        val reloaded = SharedPrefsServerConfigStore(prefs).load()
        assertEquals(listOf(ServerConfig(id = "home", name = "Home 2", address = "10.0.0.2:4800")), reloaded)
    }

    @Test
    fun `dirty json falls back to default server`() = runTest {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putString("server_configs_json", "{bad json").commit()

        val configs = SharedPrefsServerConfigStore(prefs).load()

        assertEquals(listOf(ServerConfig(id = "local", name = "Local", address = "127.0.0.1:4800")), configs)
    }
}
