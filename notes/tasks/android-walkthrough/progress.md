# Android Walkthrough - 进度

| # | 任务 | 状态 | 执行者 |
|---|------|------|--------|
| 1 | 状态栏遮挡 — Surface 加 systemBars padding | ✅ | android-ui-fix |
| 2 | 默认服务器 — 改为 Mac + Home Server | ✅ | android-ui-fix |
| 3 | 连接状态 — 服务器卡片绿点/红点 | ✅ | android-ui-fix |
| 4 | Logger println→android.util.Log | ✅ | android-coder |
| 5 | Manifest INTERNET + cleartext | ✅ | android-coder |
| 6 | OkHttpClient readTimeout=0 + pingInterval=15s | ✅ | android-coder |
| 7 | openSocket 连接超时保护 | ✅ | android-coder |
| 8 | start() 并行连接 | ✅ | android-coder |
| 9 | DM 进入即退出 — guardChat 跳过空 nodes | ✅ | debug-coder |
| 10 | 频道消息不显示 — 加 channel.history | ✅ | debug-coder |
| 11 | DM 用户消息不显示 — sendMessage 加本地消息 | ✅ | debug-coder |
| 12 | DM 历史为空 — 加 session.list/load | ✅ | debug-coder |
| 13 | DM crash — extractText 加 JsonArray 支持 | ✅ | debug-coder |
| 14 | AI 回复消失 — 删 activeKeys 竞态 | ✅ | debug-coder |
| 15 | prompt 超时 10s→120s | ✅ | debug-coder |
