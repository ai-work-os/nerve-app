package com.nerve.android.transport

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

class NerveClientConnectTimeoutTest {
    @Test
    fun `connect to unreachable endpoint throws within connect timeout`() = runTest {
        val shortTimeoutClient = OkHttpClient.Builder()
            .connectTimeout(500, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
        // Use a non-routable address so connection never completes
        val client = RealNerveClient(
            endpoint = "ws://192.0.2.1:9999/ws",
            okHttpClient = shortTimeoutClient,
            backoffStrategy = FakeBackoffStrategy(),
        )
        assertFailsWith<Throwable> {
            client.connect(
                server = ServerConfig(id = "s1", name = "unreachable", address = "192.0.2.1:9999"),
                registration = ClientRegistration(name = "android-ui"),
            )
        }
    }
}
