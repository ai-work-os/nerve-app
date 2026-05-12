# nerve-app (Android)

Nerve 项目的 Android 客户端 — Kotlin + Compose。

包名 `com.nerve.android`，主力 IDE Android Studio，构建用 `./gradlew`。

---

## AI 入口：进项目先看什么

读完这个 ai.md 就够基本上手了。需要更深：

- **本仓库规范** → `ai/specs/`
- **本仓库 skill / 流程** → `ai/skills/`
- **排错/操作流程** → `ai/runbooks/`
- **跨子仓库的事** → `../ai/ai.md`（super-project，notes 仓库下）
- **任务追踪/历史** → `../notes/`（active-tasks.md / tasks/）

`CLAUDE.md / AGENTS.md / GEMINI.md` 都是这个文件的 symlink，所有 AI 工具读的是同一份。

---

## 是什么 / 跑起来

- nerve 服务的 Android 客户端，连 `100.75.43.90:4800`（home 上的 nerve）或 `mac:4800`
- 功能：聊天 DM、agent spawn、图片附件、Life Log（24h 录音）、自动更新
- 包名 `com.nerve.android`（不是 nerve-android — 那是废弃的旧项目，feedback memory 提过）

构建：
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest        # JUnit + Robolectric 单测
```

发版（默认流程）：
```bash
# 改 versionCode/Name 之后
nerve-server publish-android "更新说明"   # 构建 + rsync 到 home nginx
```

---

## 重要规范（必读）

| 主题 | 文件 |
|---|---|
| 日志规范 | `ai/specs/logging.md` |

（更多 spec 遇到再加。空着不是漏，是 YAGNI。）

---

## 关键模块

| 模块 | 职责 |
|---|---|
| `transport/` | WebSocket 客户端、连接管理、重连 |
| `lifelog/` | Life Log 录音模块（AudioRecorder/OpusEncoder/ChunkWriter/Uploader/SyncPolicy/LifeLogService + UI） |
| `update/` | 自动更新（version.json 拉取、APK 安装） |
| `util/Logger.kt` | 日志门面（必须用，不要 `android.util.Log` 直调） |
| `util/RemoteLogBackend.kt` | WARN+ERROR 远端上报（4811 收集器） |
| `NerveAppContent.kt` | Compose 导航根 |

---

## 现状（短期演进）

- v0.5.5 (versionCode 10)：Life Log 上线
- 主聊天：DM + agent spawn 已稳定
- 仍在迭代：Life Log Phase 3 真机验证 + 双 deviceId 统一（见 `ai/specs/logging.md`）
