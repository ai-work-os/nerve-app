# Android DM Streaming 修复 — 进展

## 目标

Android 端 DM 聊天能正常使用：流式输出实时可见、thinking/tool_call 过程可见、结束后只留 text 结论、不崩溃。

## 待办总览

| # | 任务 | 状态 | 说明 |
|---|------|------|------|
| 1 | DmEventMapper 新事件类型 | ✅ | thought/tool_call 四个类型 |
| 2 | ContentBlock 模型 | ✅ | Text/Thinking/ToolCall |
| 3 | StreamingAccumulator 多 block | ✅ | |
| 4 | ChatRoute + ChatViewModel | ✅ | |
| 5 | UI 渲染 thinking/tool_call | ✅ | |
| 6 | JsonArray 崩溃修复 | ✅ | content 为数组时不再 crash |
| 7 | Logger 文件持久化 | ✅ | FileLogBackend + 轮转 |
| 8 | 隐式 start | ✅ | ensureState in accumulator, reviewer 通过 |
| 9 | tool_call 双格式解析 | ✅ | DmEventMapper + ChatRoute 都支持 ACP flat + legacy, reviewer 通过 |
| 10 | 异常隔离 | ✅ | Processor try-catch + ChatRoute try-catch, reviewer 通过 |
| 11 | **AI 消息存入 DmStore** | 🔄 依赖 #8 | 隐式 start 已修好，待验证 accumulator 正常攒消息 |
| 12 | 聊天 UI 气泡布局 | ✅ | MessageBubble: ASSISTANT 显示 nodeName, 80% maxWidth, reviewer 通过 |
| 13 | stop 按钮确认对话框 | ✅ 代码已写 | 待验证 |
| 14 | Markdown 渲染 | ✅ | Markwon 4.6.2 (core+tables+strikethrough), reviewer 通过 |
| 15 | Thinking 折叠 | ✅ | 流式展开 / 完成折叠 / 点击切换, reviewer 通过 |

## 验收记录

### 第 1 轮（#1-5 完成后）
- 结果：**闪退**
- 原因：DmEventMapper tool_call 用 `.jsonObject` 强转，content 为 JsonArray 时崩溃
- 修复：#6

### 第 2 轮（#6-7 完成后）
- 结果：**不闪退，但 AI 消息完全不显示**
- 发现：
  1. DmStore 只有 USER 消息，零条 ASSISTANT — accumulator 从未 onStart，所有 chunk 被丢弃
  2. tool_call/tool_call_update 全部 `missing_tool_call`/`missing_tool_update` — 解析格式和 server 实际不匹配
  3. thinking 流式过程中可见，结束后清空，DmStore 没数据所以什么都没了
  4. 聊天界面是流水账，没有用户/AI 消息区分

### 根因对比：TUI vs Android

| 点 | TUI（能用） | Android（不能用） |
|---|---|---|
| 隐式 start | ✅ 任何 chunk 进来自动创建 streaming message | ❌ 必须先收到 agent_message_start |
| tool_call 格式 | ✅ 兼容 `toolCall` 和 `tool_call` 两种字段名 | ❌ 只处理一种，还猜错了 |
| 异常处理 | ✅ 解析失败不影响整体 | ❌ 一个字段类型错误就 crash |

TUI 关键代码（dm_view.rs:226-228）：
```rust
if !self.streaming_messages.contains_key(agent_name) {
    self.start_streaming_message(agent_name);  // 隐式 start
}
```

## 关键设计决策

- **流式过程中**：显示完整过程（thinking → tool_call → text）
- **流式结束后**：最终消息只保留 text，thinking 和 tool_call 不持久化
- **对齐 TUI**：引入 ContentBlock 模型，隐式 start，双格式兼容

## 已完成的代码改动

- DmMappedEvent.kt — +4 类型
- DmEventMapper.kt — +4 映射 case + JsonArray 安全处理
- DmMessage.kt — ContentBlock sealed class
- StreamingAccumulator.kt — 多 block 累积
- DmEventProcessor.kt — 路由 thought/toolCall
- ChatViewModel.kt — thinking 触发 isStreaming
- ChatRoute.kt — streamingBlocks 状态
- MessageList.kt — 三种 block 渲染
- Logger — FileLogBackend + CompositeLogBackend
- MessageBubble.kt — ASSISTANT nodeName 标签 + 80% maxWidth + MarkdownText 替换
- MessageList.kt — ThinkingBlock 折叠组件

## 团队

| Agent | 角色 | 状态 |
|---|---|---|
| dm-lead | 协调 | 🔄 |
| dm-analyst | 分析 | 🔄 查 server 真实格式 + TUI 隐式 start |
| dm-tester | 测试 | 🔄 写异常隔离测试 |
| dm-reviewer | 审查 | ✅ #1-10,12 通过 |
| dm-coder | 实现 | ✅ #1-10,12 完成 |
