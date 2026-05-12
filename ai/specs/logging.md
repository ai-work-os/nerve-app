# nerve-app 日志规范

> 适用范围：nerve-app（Android/Kotlin）。跨项目的通用日志原则在 `../../../../notes/LOGGING.md`（todo: 后续搬到 super-project ai/specs/）。

新写 Kotlin 代码、改老模块前过一眼。CLAUDE 项目铁律"日志—可观测（强制）"的具体落实。

---

## 必须走 `com.nerve.android.util.Logger`

**铁律：不准用 `android.util.Log` 直调**。唯一例外是 `Logger.kt` 自己和 `RemoteLogBackend.kt`（避免反馈循环）。

```kotlin
import com.nerve.android.util.Logger

Logger.debug("MyComponent", "event_name", mapOf("k" to v))
Logger.warn("MyComponent", "fail_event", mapOf("reason" to err.message))
Logger.error("MyComponent", "crash_event", mapOf(...), throwable)
```

理由：直接 `Log.w` 只到 logcat、**远端 collector (`100.75.43.90:4811`) 看不到** —— 你不在身边时排查必须插 USB。2026-05-12 早上 Life Log 0 行远端日志就是这个根因（LifeLog 全用 `android.util.Log` 直调）。

---

## Level 选择

`RemoteLogBackend` 只收 **WARN + ERROR**（DEBUG 在 `RemoteLogBackend.write` 早 return）。也就是说 level = 决定 home 端能不能看到。

| Level | 用法 | 远端可见 | 例 |
|---|---|---|---|
| `DEBUG` | 高频事件、路由进出、状态轮询、内部循环 tick | ❌ | `Logger.debug("ChatRoute", "route_enter", ...)` |
| `WARN` | 预期外但可自恢复 + 用户该看到的状态变更 | ✅ | socket 重连、上传重试、暂停/恢复 |
| `ERROR` | 不可恢复 / 要人介入 / 丢数据 | ✅ + `throwable` | DB 加载失败、mic 不可用、crash |

判断标准：**"用户/我下次需要从远端看到这件事吗？"** Yes → WARN/ERROR；No → DEBUG。

不要为了"以防万一"把 DEBUG 升 WARN —— `RemoteLogBackend` 内存 buffer cap 1000 行，正常事件刷掉故障行。

---

## tag / event 命名

- **tag** = PascalCase 类名 / 模块名（`NerveClient`, `LifeLogService`, `UploadQueue`）
- **event** = snake_case 动作（`socket_closing`, `chunk_rotate`, `upload_fail`, `db_open_fail`）
- **fields** = `mapOf("key" to value)` 结构化，不要拼字符串

❌ `Logger.warn("X", "upload failed for chunk $id at $t reason: $r")`
✅ `Logger.warn("X", "upload_fail", mapOf("chunkId" to id, "ts" to t, "reason" to r))`

理由：远端 collector 写到 `~/.nerve/client-logs/nerve-app-{date}.log`，按字段过滤/分组比 grep 自由字符串容易得多。

---

## 写日志的 4 个时机（CLAUDE.md 铁律）

1. **状态变更** — 上下线、暂停恢复、连接断开、文件 rotate
2. **命令执行** — 收到 intent / WS notification / 用户操作，执行前后各一行
3. **触发原因** — 自动行为前说明"为啥触发"
4. **错误异常** — catch 到的所有 throwable，不准吞

---

## LifeLog 模块事件清单（参考实现）

完整接入参考 commit `dcad7fd`，主要事件：

| 类 | event | level | 字段 |
|---|---|---|---|
| `LifeLogService` | `service_start/stop/destroy` | DEBUG | — |
| `LifeLogService` | `recording_pause/resume` | WARN | reason |
| `LifeLogService` | `chunk_rotate` | DEBUG | chunkId, durationMs, sizeBytes |
| `LifeLogService` | `encoder_finish_fail` / `chunk_persist_fail` | ERROR | reason + throwable |
| `AudioRecorder` | `recorder_stop_fail` | WARN | reason + throwable |
| `AudioRecorder` | `mic_unavailable` | ERROR | reason + throwable |
| `UploadQueue` | `chunk_mark_failed` | WARN | chunkId, reason |
| `UploadQueue` | `db_open_fail` | ERROR | reason + throwable |
| `Uploader` | `upload_success` | DEBUG | chunkId, code |
| `Uploader` | `upload_fail` / `upload_io_fail` | WARN | chunkId, code/reason, attempts |
| `SyncPolicy` | `flush_start/done` | DEBUG (auto) / WARN (manual) | trigger, sent |

---

## 双 deviceId 现状（坑，待统一）

- **主 deviceId**（远端日志 `dev=` 字段）：`NerveApp.attachRemoteLogBackend`，SharedPreferences key=`nerve-device.deviceId`，完整 UUID
- **LifeLog deviceId**（转录文件 `[android-XXX]` tag）：`LifeLogConfig.deviceId`，SharedPreferences key=`lifelog_config.device_id`，UUID 前 8 位

两个**目前独立无关联**。如果要关联（"这条转录是哪台手机来的"），统一用主 deviceId — 改 `LifeLogConfig.deviceId` 读 `nerve-device` prefs。

---

## 排查 SOP

```bash
# home 上看 nerve-app 远端日志（按今天）
ssh home "tail -50 ~/.nerve/client-logs/nerve-app-$(date +%F).log"

# 按特定设备过滤（dev= 前缀就行，不需要完整 UUID）
ssh home "grep 'dev=883e8fdf' ~/.nerve/client-logs/nerve-app-$(date +%F).log"

# 按事件类型分布
ssh home "grep -oE 'event=[a-z_]+' ~/.nerve/client-logs/nerve-app-$(date +%F).log | sort | uniq -c | sort -rn"

# 本地日志（filesDir/logs）— 插 USB 跑 adb
adb shell run-as com.nerve.android cat files/logs/nerve-*.log | tail -50
```

---

## RemoteLogBackend 参数（已落地）

| 参数 | 值 | 说明 |
|---|---|---|
| flushIntervalMs | 30_000 | 30s 一批 |
| flushBatchSize | 50 | （注：实现里实际每次发完整 buffer，flushBatchSize 字段未严格用上） |
| bufferCap | 1000 | 内存 cap，满了从老的开始丢 |

端点：`http://100.75.43.90:4811/log`（home 上 `nerve-log-collector.service`）

---

## 改这份规范

发现新场景 / 漏掉的事件类 / 命名不一致 → 直接 PR。

跨项目通用部分可以提取上推到 super-project `../ai/specs/logging.md`（todo）。
