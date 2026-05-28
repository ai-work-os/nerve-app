# nerve-app AI 入口

`nerve-app` 是 ai-work-os 的 Android 手机端，Kotlin + Compose。它现在是主要协作/控制面之一，也承担 Harness 压力测试和移动端真实使用入口的角色。

## 当前角色

| 主题 | 当前事实 |
| --- | --- |
| 应用 | Android 客户端，包名 `com.nerve.android` |
| 主要用途 | 聊天、agent spawn、图片附件、Life Log、自动更新、移动端控制 |
| 服务连接 | 连接 home 上的 nerve 或本地开发 nerve |
| 验证边界 | home 可跑 unit tests；真机、安装、权限、后台保活需要单独说明 |

## 常用命令

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew testDebugUnitTest --tests "com.nerve.android.SomeTest"
```

UI / androidTest / 真机安装验证需要设备时，直接说明没有覆盖，不要用单测结果冒充。

## 关键目录

| 路径 | 说明 |
| --- | --- |
| `app/src/main/java/com/nerve/android/transport/` | WebSocket、连接管理、重连 |
| `app/src/main/java/com/nerve/android/lifelog/` | Life Log 录音、编码、上传、同步和服务 |
| `app/src/main/java/com/nerve/android/update/` | 自动更新 |
| `app/src/main/java/com/nerve/android/util/Logger.kt` | 日志门面，优先使用它 |
| `app/src/main/java/com/nerve/android/NerveAppContent.kt` | Compose 导航根 |
| `ai/specs/` | 仍可放较细规范，但不要让入口分散 |

## 发布边界

- 不要随手发布 Android 包。
- release、signing、versionCode/versionName bump、APK 分发是独立流程。
- 需要发布时按当前项目发布脚本和用户确认走，不把普通修复顺手变成发版。

## 修改规则

- 本仓主入口是 `AGENTS.md`；`CLAUDE.md` 应指向它。
- `ai/ai.md` 只保留跳转说明，避免旧入口继续说 `AGENTS.md` 是它的 symlink。
- 不要把已废弃的 `nerve-android` 当成本项目。
- 改连接、后台服务、Life Log、更新安装、权限相关逻辑时，优先加 focused test，并说明真机验证缺口。
