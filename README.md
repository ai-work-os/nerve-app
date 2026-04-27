# nerve-app

AI Work OS 的新版 Android 客户端。

`nerve-app` 是 `nerve-android` 的重构版，目标是让用户在手机上也能驾驭 nerve：看 AI 节点、进入 DM、看频道协作、spawn/stop agent，并在外面做关键决策。

## 它在系统里的位置

AI Work OS 的目标不是只在电脑上和 AI 聊天，而是让 AI 工作持续运行，人可以随时从手机接管。

`nerve-app` 对应 M8 的方向：

- 在外面用手机派活。
- 看每个 agent 和频道的状态。
- 对关键方案做确认或打回。
- stop 失控节点。
- 早晚查看值班 AI 的巡检结果和日报。

## 当前做到什么程度

新版已完成基础架构和核心链路：

- 多 server 管理。
- WebSocket JSON-RPC 连接 nerve server。
- 节点列表和状态展示。
- DM 聊天和流式输出处理。
- 频道列表和频道消息处理。
- spawn / stop agent。
- server / domain / presentation / UI 四层结构。
- 类型安全的 `DmKey` / `ChannelKey`。
- 事件 mapper / processor 分离。
- 可插拔 Logger。
- 单元测试、集成测试、Compose UI 测试覆盖核心路径。

当前状态：基础能力已具备，仍需要真机持续验证和体验收敛。

## 和旧版的区别

| 项目 | 定位 |
|---|---|
| `nerve-android` | 旧版 Android 客户端，证明手机端可行 |
| `nerve-app` | 新版 Android 客户端，当前主线 |

新版重点不是重写 UI，而是修正旧版在状态、事件、测试、日志上的长期维护问题，让手机端能承担 M8 的主控能力。

## 未来方向

短期：

- 真机验证 DM、频道、spawn/stop、断线重连。
- 补齐移动端最常用的控制入口。
- 和 server 的事件语义保持一致。

中期：

- 一键启动常用 agent 组合。
- 任务模板：选模板、填参数、启动 scene。
- 进度看板：看 sub-main、worker、值班 AI 当前状态。
- 异常通知：服务器自治时，手机能收到需要人判断的事件。

长期：

- 成为 M7/M8 的移动控制台。
- 人不在电脑前，AI 仍然能工作；需要人决策时，手机接住。

## 仓库关系

| 仓库 | 作用 |
|---|---|
| `nerve` | 服务端 |
| `nerve-tui` | 终端客户端 |
| `nerve-app` | Android 新客户端，当前主线 |
| `nerve-android` | Android 旧客户端 |

## 常用命令

```bash
# 单元测试
./gradlew test

# 构建 debug 包
./gradlew assembleDebug

# 连接真机后安装
./gradlew installDebug
```

统一脚本：

```bash
nerve-server build android
nerve-server install android
```

## 开发约束

- Android 端不做本地业务真相，server buffer / server events 是来源。
- 新行为先写测试，再实现。
- 连接、重连、流式输出、状态变化必须有日志。
- 真机验证优先级高于“测试绿”。
