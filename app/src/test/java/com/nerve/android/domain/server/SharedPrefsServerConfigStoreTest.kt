package com.nerve.android.domain.server

import com.nerve.android.transport.ServerConfig
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SharedPrefsServerConfigStoreTest {
    @Test
    fun `first load injects default servers and persists them`() = runTest {
        val prefs = InMemorySharedPreferences()
        val store = SharedPrefsServerConfigStore(prefs)

        val configs = store.load()

        assertEquals(
            listOf(
                ServerConfig(id = "mac", name = "Mac", address = "100.109.126.37:4800"),
                ServerConfig(id = "mac-test", name = "Mac (test 4801)", address = "100.109.126.37:4801"),
                ServerConfig(id = "home", name = "Home Server", address = "100.75.43.90:4800"),
            ),
            configs,
        )
        assertEquals(configs, SharedPrefsServerConfigStore(prefs).load())
    }

    @Test
    fun `upsert overwrite and remove survive store recreation`() = runTest {
        val prefs = InMemorySharedPreferences()
        val store = SharedPrefsServerConfigStore(prefs)

        store.upsert(ServerConfig(id = "office", name = "Office", address = "10.0.0.1:4800"))
        store.upsert(ServerConfig(id = "office", name = "Office 2", address = "10.0.0.2:4800"))
        store.remove("mac")

        val reloaded = SharedPrefsServerConfigStore(prefs).load()
        assertEquals(
            listOf(
                ServerConfig(id = "mac-test", name = "Mac (test 4801)", address = "100.109.126.37:4801"),
                ServerConfig(id = "home", name = "Home Server", address = "100.75.43.90:4800"),
                ServerConfig(id = "office", name = "Office 2", address = "10.0.0.2:4800"),
                ServerConfig(id = "mac", name = "Mac", address = "100.109.126.37:4800"),
            ),
            reloaded,
        )
    }

    @Test
    fun `dirty json falls back to default server`() = runTest {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putString("server_configs_json", "{bad json").commit()

        val configs = SharedPrefsServerConfigStore(prefs).load()

        assertEquals(
            listOf(
                ServerConfig(id = "mac", name = "Mac", address = "100.109.126.37:4800"),
                ServerConfig(id = "mac-test", name = "Mac (test 4801)", address = "100.109.126.37:4801"),
                ServerConfig(id = "home", name = "Home Server", address = "100.75.43.90:4800"),
            ),
            configs,
        )
    }
}
