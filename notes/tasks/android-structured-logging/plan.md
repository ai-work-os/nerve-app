# Android Structured Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all `nerve-app` business logs with structured event logs and remove `Logger.d/w/e`.

**Architecture:** `Logger` becomes the only formatter. Backends receive a final formatted event line plus optional throwable. Business code emits explicit event names and typed fields; no backend knows about field formatting.

**Tech Stack:** Kotlin, Android `android.util.Log`, JUnit 5, Gradle, existing `Logger.kt`.

---

## File Structure

- Modify: `app/src/main/java/com/nerve/android/util/Logger.kt`
  - Owns `LogLevel`, structured formatting, backend contract, Android/stdout/file/composite backends.
- Modify: `app/src/test/java/com/nerve/android/util/LoggerTest.kt`
  - Verifies structured formatting, escaping, backend delegation, file persistence, and source scan.
- Modify all production files using `Logger.d/w/e`:
  - `app/src/main/java/com/nerve/android/transport/RealNerveClient.kt`
  - `app/src/main/java/com/nerve/android/domain/server/ServerRegistry.kt`
  - `app/src/main/java/com/nerve/android/domain/server/SharedPrefsServerConfigStore.kt`
  - `app/src/main/java/com/nerve/android/domain/dm/DmEventMapper.kt`
  - `app/src/main/java/com/nerve/android/domain/dm/DmSessionManager.kt`
  - `app/src/main/java/com/nerve/android/domain/channel/ChannelEventMapper.kt`
  - `app/src/main/java/com/nerve/android/domain/channel/ChannelEventProcessor.kt`
  - `app/src/main/java/com/nerve/android/domain/channel/ChannelStore.kt`
  - `app/src/main/java/com/nerve/android/presentation/server/ServerViewModel.kt`
  - `app/src/main/java/com/nerve/android/presentation/nodes/NodesViewModel.kt`
  - `app/src/main/java/com/nerve/android/presentation/chat/ChatViewModel.kt`
  - `app/src/main/java/com/nerve/android/presentation/channels/ChannelsViewModel.kt`
  - `app/src/main/java/com/nerve/android/ui/chat/ChatRoute.kt`
  - `app/src/main/java/com/nerve/android/ui/chat/ChatScreen.kt`
  - `app/src/main/java/com/nerve/android/ui/chat/MessageList.kt`
  - `app/src/main/java/com/nerve/android/ui/channels/ChannelChatRoute.kt`

## Task 1: Logger API Red Tests

**Files:**
- Modify: `app/src/test/java/com/nerve/android/util/LoggerTest.kt`
- Test: `app/src/test/java/com/nerve/android/util/LoggerTest.kt`

- [ ] **Step 1: Replace Logger tests with structured API expectations**

Use this full test shape in `LoggerTest`:

```kotlin
class LoggerTest {
    private lateinit var spy: SpyLogBackend

    @BeforeEach
    fun setUp() {
        spy = SpyLogBackend()
        Logger.backend = spy
    }

    @AfterEach
    fun tearDown() {
        Logger.backend = Logger.detectBackend()
    }

    @Test
    fun `debug formats event and fields`() {
        Logger.debug("MyTag", "rpc_send", mapOf("method" to "node.prompt", "requestId" to 12L))

        assertEquals(1, spy.entries.size)
        val entry = spy.entries[0]
        assertEquals(LogLevel.DEBUG, entry.level)
        assertEquals("MyTag", entry.tag)
        assertEquals("event=rpc_send method=node.prompt requestId=12", entry.line)
        assertEquals(null, entry.throwable)
    }

    @Test
    fun `warn omits null fields`() {
        Logger.warn("T", "refresh_fail", mapOf("serverId" to "local", "reason" to null))

        assertEquals("event=refresh_fail serverId=local", spy.entries[0].line)
    }

    @Test
    fun `error passes throwable`() {
        val ex = RuntimeException("boom")

        Logger.error("E", "socket_failure", mapOf("reason" to ex.message), ex)

        val entry = spy.entries[0]
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("event=socket_failure reason=boom", entry.line)
        assertEquals(ex, entry.throwable)
    }

    @Test
    fun `strings needing escaping are JSON strings`() {
        Logger.debug(
            "Escape",
            "field_escape",
            mapOf(
                "space" to "hello world",
                "equals" to "a=b",
                "newline" to "a\nb",
                "quote" to "say \"hi\"",
            ),
        )

        assertEquals(
            "event=field_escape space=\"hello world\" equals=\"a=b\" newline=\"a\\nb\" quote=\"say \\\"hi\\\"\"",
            spy.entries[0].line,
        )
    }

    @Test
    fun `PrintlnLogBackend formats output correctly`() {
        val originalOut = System.out
        val baos = java.io.ByteArrayOutputStream()
        System.setOut(java.io.PrintStream(baos))
        try {
            val backend = PrintlnLogBackend()
            backend.write(LogLevel.DEBUG, "tag", "event=test_event")
            assertEquals("D/tag event=test_event\n", baos.toString())
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `detectBackend returns PrintlnLogBackend in test environment`() {
        val backend = Logger.detectBackend()
        assertTrue(backend is PrintlnLogBackend)
    }

    @Test
    fun `initFileLogging writes to android and file backends`(@TempDir tempDir: File) {
        val androidSpy = SpyLogBackend()

        Logger.initFileLogging(tempDir, androidSpy)
        Logger.debug("Init", "persist_test")

        assertEquals(2, androidSpy.entries.size)
        assertEquals("event=logger_initialized fileLogging=true dir=${tempDir.absolutePath}", androidSpy.entries[0].line)
        assertEquals("event=persist_test", androidSpy.entries[1].line)

        val content = tempDir.listFiles()?.filter { it.extension == "log" }
            ?.joinToString("\n") { it.readText() }
            .orEmpty()
        assertTrue(content.contains("event=logger_initialized fileLogging=true"), "file log should contain init line")
        assertTrue(content.contains("D/Init event=persist_test"), "file log should contain app log line")
    }

    private class SpyLogBackend : LogBackend {
        data class Entry(
            val level: LogLevel,
            val tag: String,
            val line: String,
            val throwable: Throwable? = null,
        )
        val entries = mutableListOf<Entry>()

        override fun write(level: LogLevel, tag: String, line: String, throwable: Throwable?) {
            entries += Entry(level, tag, line, throwable)
        }
    }
}
```

- [ ] **Step 2: Run logger tests and verify red**

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.nerve.android.util.LoggerTest'
```

Expected: compile failure because `LogLevel`, `Logger.debug`, `Logger.warn`, `Logger.error`, and `LogBackend.write` do not exist.

## Task 2: Implement Structured Logger

**Files:**
- Modify: `app/src/main/java/com/nerve/android/util/Logger.kt`
- Test: `app/src/test/java/com/nerve/android/util/LoggerTest.kt`

- [ ] **Step 1: Replace Logger implementation**

Implement these public shapes in `Logger.kt`:

```kotlin
enum class LogLevel { DEBUG, WARN, ERROR }

interface LogBackend {
    fun write(level: LogLevel, tag: String, line: String, throwable: Throwable? = null)
}

object Logger {
    var backend: LogBackend = detectBackend()

    fun debug(tag: String, event: String, fields: Map<String, Any?> = emptyMap()) {
        backend.write(LogLevel.DEBUG, tag, format(event, fields))
    }

    fun warn(tag: String, event: String, fields: Map<String, Any?> = emptyMap()) {
        backend.write(LogLevel.WARN, tag, format(event, fields))
    }

    fun error(
        tag: String,
        event: String,
        fields: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) {
        backend.write(LogLevel.ERROR, tag, format(event, fields), throwable)
    }
}
```

The private formatter must:

```kotlin
private fun format(event: String, fields: Map<String, Any?>): String {
    val parts = mutableListOf("event=$event")
    fields.forEach { (key, value) ->
        if (value != null) parts += "$key=${formatValue(value)}"
    }
    return parts.joinToString(" ")
}
```

Use a local `formatValue(value: Any): String` that returns plain `toString()` when the string has no whitespace, quote, backslash, or `=`, and otherwise returns `Json.encodeToString(value)`.

- [ ] **Step 2: Update backends**

Keep backend classes but change them to `write(...)`:

```kotlin
class AndroidLogBackend : LogBackend {
    override fun write(level: LogLevel, tag: String, line: String, throwable: Throwable?) {
        when (level) {
            LogLevel.DEBUG -> android.util.Log.d(tag, line)
            LogLevel.WARN -> android.util.Log.w(tag, line)
            LogLevel.ERROR -> if (throwable != null) android.util.Log.e(tag, line, throwable) else android.util.Log.e(tag, line)
        }
    }
}
```

`PrintlnLogBackend` prefixes `D/`, `W/`, or `E/`. `FileLogBackend` writes `"${Instant.now()} ${prefix(level)}/$tag $line\n"` and then stack trace when present. `CompositeLogBackend` forwards `write` to every backend.

- [ ] **Step 3: Update logger initialization event**

In `Logger.initFileLogging`, emit:

```kotlin
debug(
    "Logger",
    "logger_initialized",
    mapOf("fileLogging" to true, "dir" to logsDir.absolutePath),
)
```

- [ ] **Step 4: Run logger tests and verify green**

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.nerve.android.util.LoggerTest'
```

Expected: `LoggerTest` passes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nerve/android/util/Logger.kt app/src/test/java/com/nerve/android/util/LoggerTest.kt
git commit -m "Refactor Android logger to structured events"
```

## Task 3: Source Scan Red Test

**Files:**
- Modify: `app/src/test/java/com/nerve/android/util/LoggerTest.kt`
- Test: `app/src/test/java/com/nerve/android/util/LoggerTest.kt`

- [ ] **Step 1: Add source scan test**

Add this test class to `LoggerTest.kt`:

```kotlin
class LoggerSourceUsageTest {
    @Test
    fun `production code does not use legacy logger API`() {
        val root = File("src/main/java")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (
                        line.contains("Logger.d(") ||
                        line.contains("Logger.w(") ||
                        line.contains("Logger.e(")
                    ) {
                        "${file.path}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertTrue(offenders.isEmpty(), offenders.joinToString(separator = "\n"))
    }
}
```

- [ ] **Step 2: Run source scan and verify red**

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.nerve.android.util.LoggerSourceUsageTest'
```

Expected: fail with current `Logger.d/w/e` production usages listed.

## Task 4: Replace Transport Logs

**Files:**
- Modify: `app/src/main/java/com/nerve/android/transport/RealNerveClient.kt`
- Test: `app/src/test/java/com/nerve/android/transport/NerveClientConnectTest.kt`
- Test: `app/src/test/java/com/nerve/android/transport/NerveClientReconnectTest.kt`
- Test: `app/src/test/java/com/nerve/android/transport/NerveClientRpcTest.kt`
- Test: `app/src/test/java/com/nerve/android/util/LoggerTest.kt`

- [ ] **Step 1: Replace subscribe/unsubscribe logs**

Use:

```kotlin
Logger.debug("NerveClient", "subscribe_success", mapOf("nodeId" to nodeId))
Logger.debug("NerveClient", "unsubscribe_success", mapOf("nodeId" to nodeId))
```

- [ ] **Step 2: Replace connection and socket logs**

Use these events:

```kotlin
Logger.debug("NerveClient", "connect_begin", mapOf("url" to url))
Logger.debug("NerveClient", "socket_open", mapOf("url" to url))
Logger.warn("NerveClient", "socket_closing", mapOf("code" to code, "reason" to reason))
Logger.warn("NerveClient", "socket_closed", mapOf("code" to code, "reason" to reason))
Logger.error("NerveClient", "socket_failure", mapOf("reason" to throwable.message), throwable)
```

Add `connect_success` after `openAndRegister` completes in `connect`.

- [ ] **Step 3: Replace register/RPC/notification logs**

Use:

```kotlin
Logger.debug("NerveClient", "register_begin", mapOf("name" to registration.name))
Logger.debug("NerveClient", "register_success", mapOf("name" to registration.name))
Logger.debug("NerveClient", "notify_recv", mapOf("method" to method))
Logger.warn("NerveClient", "notify_ignored", mapOf("method" to method, "reason" to "unmapped_notification"))
Logger.debug("NerveClient", "rpc_recv", mapOf("requestId" to responseId))
Logger.debug("NerveClient", "rpc_buffered", mapOf("requestId" to responseId, "reason" to "pending_not_found"))
Logger.debug("NerveClient", "rpc_send", mapOf("method" to method, "requestId" to requestId))
Logger.warn("NerveClient", "rpc_timeout", mapOf("method" to method, "requestId" to requestId, "reason" to "timeout"))
```

In `completePending`, log `rpc_error` before completing exceptionally:

```kotlin
Logger.warn("NerveClient", "rpc_error", mapOf("code" to error.code, "reason" to error.message))
```

- [ ] **Step 4: Replace reconnect and state logs**

Use:

```kotlin
Logger.debug("NerveClient", "reconnect_attempt", mapOf("attempt" to attempt, "delayMs" to delayMs))
Logger.warn("NerveClient", "reconnect_fail", mapOf("attempt" to attempt, "reason" to error.message))
Logger.debug("NerveClient", "reconnect_success", mapOf("attempt" to attempt))
Logger.warn("NerveClient", "reconnect_stop", mapOf("reason" to "missing_server_or_registration"))
Logger.debug("NerveClient", "state_change", mapOf("oldState" to old, "newState" to next))
```

The reconnect `catch (_: Throwable)` becomes `catch (error: Throwable)` and logs `reconnect_fail`.

- [ ] **Step 5: Run focused tests**

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.nerve.android.transport.*' --tests 'com.nerve.android.util.LoggerSourceUsageTest'
```

Expected: transport tests pass; source scan still fails because non-transport files remain.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/nerve/android/transport/RealNerveClient.kt app/src/test/java/com/nerve/android/util/LoggerTest.kt
git commit -m "Migrate Android transport logs to structured events"
```

## Task 5: Replace Server Domain Logs

**Files:**
- Modify: `app/src/main/java/com/nerve/android/domain/server/ServerRegistry.kt`
- Modify: `app/src/main/java/com/nerve/android/domain/server/SharedPrefsServerConfigStore.kt`
- Test: `app/src/test/java/com/nerve/android/domain/server/ServerRegistryTest.kt`
- Test: `app/src/test/java/com/nerve/android/domain/server/SharedPrefsServerConfigStoreTest.kt`
- Test: `app/src/test/java/com/nerve/android/util/LoggerTest.kt`

- [ ] **Step 1: Replace registry lifecycle logs**

Use:

```kotlin
Logger.debug("ServerRegistry", "registry_start", mapOf("serverCount" to configs.size))
Logger.debug("ServerRegistry", "registry_started", mapOf("serverCount" to configs.size))
Logger.debug("ServerRegistry", "refresh_begin", mapOf("serverId" to serverId, "targetCount" to targetConfigs.size))
Logger.debug("ServerRegistry", "refresh_success", mapOf("serverId" to config.id, "nodeCount" to nodeCache[config.id]?.size, "channelCount" to channelCache[config.id]?.size))
Logger.warn("ServerRegistry", "refresh_fail", mapOf("serverId" to config.id, "target" to "nodes", "reason" to it.message))
Logger.warn("ServerRegistry", "refresh_fail", mapOf("serverId" to config.id, "target" to "channels", "reason" to it.message))
```

- [ ] **Step 2: Replace server mutation and connection logs**

Use:

```kotlin
Logger.debug("ServerRegistry", "server_add", mapOf("serverId" to config.id, "address" to config.address))
Logger.debug("ServerRegistry", "server_remove", mapOf("serverId" to serverId))
Logger.debug("ServerRegistry", "client_create", mapOf("serverId" to config.id))
Logger.debug("ServerRegistry", "connect_begin", mapOf("serverId" to config.id))
Logger.warn("ServerRegistry", "connect_fail", mapOf("serverId" to serverId, "reason" to error.message))
Logger.debug("ServerRegistry", "disconnect_begin", mapOf("serverId" to serverId))
Logger.debug("ServerRegistry", "disconnect_success", mapOf("serverId" to serverId))
Logger.debug("ServerRegistry", "state_change", mapOf("serverId" to config.id, "state" to state))
Logger.debug("ServerRegistry", "event_forward", mapOf("serverId" to config.id, "eventType" to event::class.simpleName))
```

- [ ] **Step 3: Replace config store logs**

Use:

```kotlin
Logger.debug("ServerConfigStore", "config_default_injected", mapOf("serverId" to "local"))
Logger.error("ServerConfigStore", "config_load_fail", mapOf("reason" to it.message), it)
Logger.debug("ServerConfigStore", "config_default_appended", mapOf("serverIds" to missing.joinToString(",") { config -> config.id }))
Logger.debug("ServerConfigStore", "config_load", mapOf("count" to configs.size))
```

- [ ] **Step 4: Run focused tests**

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.nerve.android.domain.server.*' --tests 'com.nerve.android.util.LoggerSourceUsageTest'
```

Expected: server domain tests pass; source scan may still fail for remaining files.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nerve/android/domain/server/ServerRegistry.kt app/src/main/java/com/nerve/android/domain/server/SharedPrefsServerConfigStore.kt
git commit -m "Migrate Android server logs to structured events"
```

## Task 6: Replace DM and Channel Domain Logs

**Files:**
- Modify: `app/src/main/java/com/nerve/android/domain/dm/DmEventMapper.kt`
- Modify: `app/src/main/java/com/nerve/android/domain/dm/DmSessionManager.kt`
- Modify: `app/src/main/java/com/nerve/android/domain/channel/ChannelEventMapper.kt`
- Modify: `app/src/main/java/com/nerve/android/domain/channel/ChannelEventProcessor.kt`
- Modify: `app/src/main/java/com/nerve/android/domain/channel/ChannelStore.kt`
- Test: `app/src/test/java/com/nerve/android/domain/dm/DmEventMapperTest.kt`
- Test: `app/src/test/java/com/nerve/android/domain/dm/DmSessionManagerTest.kt`
- Test: `app/src/test/java/com/nerve/android/domain/channel/ChannelEventMapperTest.kt`
- Test: `app/src/test/java/com/nerve/android/domain/channel/ChannelEventProcessorTest.kt`
- Test: `app/src/test/java/com/nerve/android/domain/channel/ChannelStoreTest.kt`
- Test: `app/src/test/java/com/nerve/android/util/LoggerTest.kt`

- [ ] **Step 1: Replace DM mapper logs**

Use:

```kotlin
Logger.warn("DmEventMapper", "map_error", mapOf("reason" to e.message))
Logger.debug("DmEventMapper", "map_ignore", mapOf("nodeId" to nodeId, "reason" to reason))
```

- [ ] **Step 2: Replace DM session logs**

Use:

```kotlin
Logger.warn("DmSessionManager", "unhandled_event", mapOf("eventType" to event::class.simpleName, "reason" to "unsupported_event"))
Logger.debug("DmSessionManager", "batch_begin")
Logger.debug("DmSessionManager", "batch_end", mapOf("count" to pending.size))
Logger.debug("DmSessionManager", "session_reset", mapOf("count" to _messages.value.size))
Logger.debug("DmSessionManager", "history_replace", mapOf("count" to messages.size))
Logger.debug("DmSessionManager", "system_message_add", mapOf("messageId" to message.id, "hasAction" to (message.action != null)))
Logger.debug("DmSessionManager", "stream_start", mapOf("messageId" to event.messageId, "nodeId" to event.nodeId))
Logger.debug("DmSessionManager", "stream_implicit_start", mapOf("nodeId" to nodeId))
Logger.debug("DmSessionManager", "stream_chunk", mapOf("len" to event.text.length))
Logger.debug("DmSessionManager", "stream_end", mapOf("messageId" to s.messageId))
Logger.warn("DmSessionManager", "stream_end_without_active", mapOf("reason" to "no_active_stream_no_fallback"))
Logger.debug("DmSessionManager", "stream_flush", mapOf("reason" to reason))
```

- [ ] **Step 3: Replace channel mapper/processor/store logs**

Use:

```kotlin
Logger.warn("ChannelEventMapper", "channel_id_conflict", mapOf("channelId" to channelId, "top" to topLevelId, "payload" to payloadId))
Logger.debug("ChannelEventMapper", "map_ignore", mapOf("reason" to reason))
Logger.warn("ChannelEventProcessor", "channel_attach_ignore", mapOf("serverId" to serverId, "reason" to "duplicate_attach"))
Logger.debug("ChannelEventProcessor", "channel_event", mapOf("key" to key.value, "kind" to "message"))
Logger.debug("ChannelStore", "channel_dedup", mapOf("key" to key.value, "messageId" to message.id))
Logger.debug("ChannelStore", "channel_message_add", mapOf("key" to key.value, "messageId" to message.id, "count" to updated.size))
Logger.debug("ChannelStore", "channel_meta_update", mapOf("key" to key.value, "name" to meta.name, "closed" to meta.isClosed))
```

- [ ] **Step 4: Run focused tests**

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.nerve.android.domain.dm.*' --tests 'com.nerve.android.domain.channel.*' --tests 'com.nerve.android.util.LoggerSourceUsageTest'
```

Expected: domain tests pass; source scan may still fail for presentation/UI files.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nerve/android/domain/dm app/src/main/java/com/nerve/android/domain/channel
git commit -m "Migrate Android domain logs to structured events"
```

## Task 7: Replace Presentation and UI Logs

**Files:**
- Modify: `app/src/main/java/com/nerve/android/presentation/server/ServerViewModel.kt`
- Modify: `app/src/main/java/com/nerve/android/presentation/nodes/NodesViewModel.kt`
- Modify: `app/src/main/java/com/nerve/android/presentation/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/nerve/android/presentation/channels/ChannelsViewModel.kt`
- Modify: `app/src/main/java/com/nerve/android/ui/chat/ChatRoute.kt`
- Modify: `app/src/main/java/com/nerve/android/ui/chat/ChatScreen.kt`
- Modify: `app/src/main/java/com/nerve/android/ui/chat/MessageList.kt`
- Modify: `app/src/main/java/com/nerve/android/ui/channels/ChannelChatRoute.kt`
- Test: `app/src/test/java/com/nerve/android/presentation/server/ServerViewModelTest.kt`
- Test: `app/src/test/java/com/nerve/android/presentation/nodes/NodesViewModelTest.kt`
- Test: `app/src/test/java/com/nerve/android/presentation/chat/ChatViewModelTest.kt`
- Test: `app/src/test/java/com/nerve/android/presentation/channels/ChannelsViewModelTest.kt`
- Test: `app/src/test/java/com/nerve/android/util/LoggerTest.kt`

- [ ] **Step 1: Replace server/nodes ViewModel logs**

Use:

```kotlin
Logger.debug("ServerViewModel", "server_add_begin", mapOf("serverId" to id, "address" to address))
Logger.debug("ServerViewModel", "server_remove_begin", mapOf("serverId" to serverId))
Logger.debug("ServerViewModel", "server_refresh_begin")
Logger.debug("ServerViewModel", "server_refresh_begin", mapOf("serverId" to serverId))
Logger.debug("NodesViewModel", "node_spawn_begin", mapOf("serverId" to serverId, "adapter" to adapter))
Logger.debug("NodesViewModel", "node_stop_begin", mapOf("serverId" to serverId, "nodeId" to nodeId))
```

- [ ] **Step 2: Replace chat ViewModel logs**

Use:

```kotlin
Logger.warn("ChatViewModel", "dm_unsubscribe_fail", mapOf("nodeId" to previousNodeId, "reason" to it.message))
Logger.debug("ChatViewModel", "dm_enter", mapOf("serverId" to serverId, "nodeId" to nodeId))
Logger.debug("ChatViewModel", "dm_snapshot_received", mapOf("nodeId" to nodeId, "count" to event.messages.size))
Logger.debug("ChatViewModel", "dm_node_spawned", mapOf("parentNodeId" to nodeId, "nodeId" to event.nodeId, "nodeName" to event.name))
Logger.debug("ChatViewModel", "dm_event_mapped", mapOf("mappedType" to mapped::class.simpleName, "eventType" to event::class.simpleName))
Logger.warn("ChatViewModel", "dm_subscribe_fail", mapOf("nodeId" to nodeId, "reason" to it.message))
Logger.debug("ChatViewModel", "dm_leave", mapOf("serverId" to serverId, "nodeId" to nodeId))
Logger.debug("ChatViewModel", "dm_send_begin", mapOf("serverId" to serverId, "nodeId" to nodeId, "len" to text.length))
Logger.warn("ChatViewModel", "dm_send_fail", mapOf("serverId" to serverId, "nodeId" to nodeId, "reason" to it.message))
Logger.debug("ChatViewModel", "dm_image_send_begin", mapOf("serverId" to serverId, "nodeId" to nodeId, "mime" to mimeType, "bytes" to base64Data.length))
Logger.debug("ChatViewModel", "session_load_begin", mapOf("serverId" to serverId, "nodeId" to nodeId, "sessionId" to latestSessionId))
Logger.warn("ChatViewModel", "session_load_fail", mapOf("serverId" to serverId, "nodeId" to nodeId, "reason" to it.message))
```

- [ ] **Step 3: Replace channels ViewModel logs**

Use:

```kotlin
Logger.debug("ChannelsViewModel", "channel_enter", mapOf("key" to key.value))
Logger.debug("ChannelsViewModel", "channel_join", mapOf("serverId" to serverId, "channelId" to channelId))
Logger.debug("ChannelsViewModel", "channel_post_begin", mapOf("serverId" to serverId, "channelId" to channelId, "len" to text.length))
Logger.debug("ChannelsViewModel", "channel_history_loaded", mapOf("key" to key.value, "count" to messages.size))
Logger.warn("ChannelsViewModel", "channel_history_fail", mapOf("key" to key.value, "reason" to it.message))
```

- [ ] **Step 4: Replace UI route logs**

Use:

```kotlin
Logger.debug("ChatRoute", "dm_image_selected", mapOf("serverId" to serverId, "nodeId" to nodeId, "mime" to mimeType, "bytes" to bytes.size))
Logger.debug("ChatRoute", "route_enter", mapOf("serverId" to serverId, "nodeId" to nodeId, "route" to "dm"))
Logger.debug("ChatRoute", "route_leave", mapOf("serverId" to serverId, "nodeId" to nodeId, "route" to "dm"))
Logger.debug("ChatRoute", "dm_send_begin", mapOf("serverId" to serverId, "nodeId" to nodeId, "len" to text.length))
Logger.debug("ChatRoute", "dm_cancel_begin", mapOf("serverId" to serverId, "nodeId" to nodeId))
Logger.debug("ChatRoute", "dm_stop_begin", mapOf("serverId" to serverId, "nodeId" to nodeId))
Logger.debug("ChatScreen", "screen_error_visible", mapOf("visible" to (state.errorMessage != null)))
Logger.debug("ChatScreen", "scroll_bottom", mapOf("count" to visibleMessages.size + extra))
Logger.debug("ChannelChatRoute", "route_enter", mapOf("serverId" to serverId, "channelId" to channelId, "route" to "channel"))
Logger.debug("ChannelChatRoute", "channel_post_begin", mapOf("serverId" to serverId, "channelId" to channelId, "len" to text.length))
```

- [ ] **Step 5: Run focused tests**

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.nerve.android.presentation.*' --tests 'com.nerve.android.util.LoggerSourceUsageTest'
```

Expected: presentation tests pass and source scan passes.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/nerve/android/presentation app/src/main/java/com/nerve/android/ui app/src/test/java/com/nerve/android/util/LoggerTest.kt
git commit -m "Migrate Android presentation logs to structured events"
```

## Task 8: Full Verification

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run all unit tests**

Run:

```bash
./gradlew test
```

Expected: all unit tests pass.

- [ ] **Step 2: Scan for legacy usage**

Run:

```bash
rg -n 'Logger\.(d|w|e)\(' app/src/main/java app/src/test/java
```

Expected: no output.

- [ ] **Step 3: Scan for empty catch introduced or left in touched files**

Run:

```bash
rg -n 'catch \([^)]*\) \{\s*\}' app/src/main/java/com/nerve/android
```

Expected: no output.

- [ ] **Step 4: Review final diff**

Run:

```bash
git diff --stat HEAD~5..HEAD
git log --oneline -5
```

Expected: commits correspond to logger refactor, transport migration, server migration, domain migration, presentation/UI migration.

- [ ] **Step 5: Commit any final cleanup**

If verification required small fixes:

```bash
git add app/src/main/java app/src/test/java
git commit -m "Verify Android structured logging migration"
```

If no cleanup was needed, do not create an empty commit.
