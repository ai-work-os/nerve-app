# Android Structured Logging Design

## Scope

`nerve-app` will replace all business log calls with structured event logs.

This is a breaking cleanup for a personal app. Compatibility with the old `Logger.d/w/e` business API is not required.

## Goals

- Make real-device logs reconstruct the full execution chain.
- Use one log format across Android logcat, stdout tests, and persisted log files.
- Remove natural-language log messages from business code.
- Keep logs useful for debugging without recording message body, base64 data, tokens, or large payloads.

## Non-Goals

- No remote log upload.
- No log viewer UI.
- No runtime log-level settings.
- No new logging dependency.

## Logger API

`Logger` exposes only event-style calls:

```kotlin
Logger.debug(
    tag = "NerveClient",
    event = "rpc_send",
    fields = mapOf(
        "method" to method,
        "requestId" to requestId,
        "serverId" to serverId,
    ),
)

Logger.warn(
    tag = "NerveClient",
    event = "rpc_timeout",
    fields = mapOf(
        "method" to method,
        "requestId" to requestId,
        "reason" to "timeout",
    ),
)

Logger.error(
    tag = "NerveClient",
    event = "socket_failure",
    fields = mapOf("reason" to throwable.message),
    throwable = throwable,
)
```

`Logger.d`, `Logger.w`, and `Logger.e` are removed.

## Backend Contract

`LogBackend` receives a formatted line:

```kotlin
interface LogBackend {
    fun write(level: LogLevel, tag: String, line: String, throwable: Throwable? = null)
}
```

Backends:

- `AndroidLogBackend` maps `LogLevel.DEBUG/WARN/ERROR` to `android.util.Log`.
- `PrintlnLogBackend` prints `D/<tag> <line>`, `W/<tag> <line>`, or `E/<tag> <line>`.
- `FileLogBackend` writes timestamped lines and throwable stack traces.
- `CompositeLogBackend` forwards the same structured line to all backends.

## Format

Every log line starts with `event=<snake_case_name>`.

Example:

```text
D/NerveClient event=rpc_send method=node.prompt requestId=12 serverId=local
W/NerveClient event=rpc_timeout method=node.prompt requestId=12 reason=timeout
E/NerveClient event=socket_failure reason="connection reset"
```

Field rules:

- Field names use lower camel case, matching existing Kotlin identifiers where possible.
- Event names use snake_case.
- `null` field values are omitted.
- Strings containing whitespace, newline, quote, backslash, or `=` are JSON-escaped strings.
- Primitive values are written as plain text.
- Message text, base64 payloads, tokens, and full JSON payloads are not logged.
- For user content, log `len`, `bytes`, `mime`, `count`, `type`, or IDs only.

## Replacement Coverage

All `app/src/main/java` calls to `Logger.d/w/e` are replaced.

### Transport

Events:

- `connect_begin`
- `connect_success`
- `disconnect_begin`
- `disconnect_success`
- `socket_open`
- `socket_closing`
- `socket_closed`
- `socket_failure`
- `register_begin`
- `register_success`
- `rpc_send`
- `rpc_recv`
- `rpc_buffered`
- `rpc_timeout`
- `rpc_error`
- `notify_recv`
- `notify_ignored`
- `state_change`
- `reconnect_attempt`
- `reconnect_fail`
- `reconnect_success`
- `reconnect_stop`
- `subscribe_begin`
- `subscribe_success`
- `unsubscribe_begin`
- `unsubscribe_success`
- `resubscribe_begin`
- `resubscribe_item`
- `resubscribe_success`

Required context where available:

- `serverId`
- `url`
- `method`
- `requestId`
- `nodeId`
- `attempt`
- `delayMs`
- `oldState`
- `newState`
- `reason`

### Server Domain

Events:

- `registry_start`
- `registry_started`
- `refresh_begin`
- `refresh_success`
- `refresh_fail`
- `server_add`
- `server_remove`
- `client_create`
- `connect_begin`
- `connect_fail`
- `disconnect_begin`
- `disconnect_success`
- `state_change`
- `event_forward`
- `config_load`
- `config_load_fail`
- `config_default_injected`
- `config_default_appended`

Required context where available:

- `serverId`
- `address`
- `nodeCount`
- `channelCount`
- `state`
- `eventType`
- `reason`

### Presentation/UI Boundary

Events:

- `route_enter`
- `route_leave`
- `screen_error_visible`
- `dm_enter`
- `dm_leave`
- `dm_send_begin`
- `dm_send_fail`
- `dm_image_send_begin`
- `dm_image_selected`
- `dm_subscribe_fail`
- `dm_unsubscribe_fail`
- `dm_snapshot_received`
- `dm_node_spawned`
- `dm_event_mapped`
- `channel_enter`
- `channel_join`
- `channel_post_begin`
- `channel_history_loaded`
- `channel_history_fail`
- `node_spawn_begin`
- `node_stop_begin`
- `server_add_begin`
- `server_remove_begin`
- `server_refresh_begin`

Required context where available:

- `serverId`
- `nodeId`
- `channelId`
- `nodeName`
- `adapter`
- `len`
- `bytes`
- `mime`
- `count`
- `reason`

### DM/Channel Domain

Events:

- `map_error`
- `map_ignore`
- `unhandled_event`
- `batch_begin`
- `batch_end`
- `session_reset`
- `history_replace`
- `system_message_add`
- `stream_start`
- `stream_implicit_start`
- `stream_chunk`
- `stream_flush`
- `stream_end`
- `stream_end_without_active`
- `channel_attach_ignore`
- `channel_event`
- `channel_id_conflict`
- `channel_dedup`
- `channel_message_add`
- `channel_meta_update`

Required context where available:

- `serverId`
- `nodeId`
- `channelId`
- `key`
- `messageId`
- `eventType`
- `kind`
- `count`
- `len`
- `reason`

## Error Handling Policy

- Expected failures use `warn`.
- Unexpected failures with throwable objects use `error`.
- Every failure log includes `reason`.
- Empty catch blocks are not allowed. The reconnect loop logs `reconnect_fail`.
- If a failure is intentionally ignored, it logs `*_ignore` with `reason`.

## Testing Strategy

Follow TDD.

1. Update `LoggerTest` first and confirm red:
   - structured line starts with `event=...`
   - fields render as `key=value`
   - null fields are omitted
   - strings needing escaping are JSON strings
   - throwable is passed through
   - file backend persists structured lines
   - composite backend forwards the same line
2. Add a source scan test and confirm red:
   - `app/src/main/java` must not contain `Logger.d(`
   - `app/src/main/java` must not contain `Logger.w(`
   - `app/src/main/java` must not contain `Logger.e(`
3. Implement Logger API and backend changes.
4. Replace all business log calls.
5. Run:

```bash
./gradlew test
```

Android instrumentation tests are not required for this logging-only change unless unit tests reveal UI behavior changes.

## Acceptance Criteria

- No `Logger.d/w/e` calls remain in `app/src/main/java`.
- Business logs use explicit event names.
- Failure logs include `reason`.
- `./gradlew test` passes.
- Output has no test error or warning caused by the logging migration.
