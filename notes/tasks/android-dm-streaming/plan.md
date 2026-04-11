# Android DM Streaming 修复方案

## 根因总结

| 问题 | 根因 | 修复 |
|---|---|---|
| thinking 不显示 | `agent_thought_chunk` 被 DmEventMapper 忽略，全链路未处理 | 新增事件类型 + UI 渲染 |
| 流式输出看不到 | 代码分析链路正确，需运行时验证。可能与 thinking 事件干扰流式状态有关 | 加日志 + 支持 thinking 后复测 |
| 无输入指示 | isStreaming + typing indicator 代码已存在，但 thinking 阶段未触发 | thinking 阶段也设 isStreaming |

## 修复范围（对齐 TUI，按优先级）

### P0 — 本次修复

1. **DmMappedEvent 新增类型**：`AgentThoughtChunk`, `AgentThoughtEnd`, `ToolCall`, `ToolCallUpdate`
2. **DmEventMapper 新增映射**：处理 `agent_thought_chunk`, `agent_thought_end`, `tool_call`, `tool_call_update`
3. **DmMessage 引入 ContentBlock 模型**：参考 TUI，支持 Text/Thinking/ToolCall 三种 block
4. **StreamingAccumulator 支持多 block**：thinking chunk → Thinking block, tool_call → ToolCall block
5. **ChatRoute 累积 thinking 文本**：streamingText 包含 thinking 内容（或独立 thinkingText）
6. **ChatViewModel streaming 状态**：thinking 阶段也算 streaming
7. **MessageList UI**：渲染 Thinking block（可折叠）、ToolCall block（名称+状态）

### P1 — 后续

- usage_update 显示
- node_log 显示

## TDD 执行计划

### Step 1: DmEventMapper 扩展（tester → reviewer → coder）
- 测试：`agent_thought_chunk` 映射为 `AgentThoughtChunk`
- 测试：`tool_call` 映射为 `ToolCall`
- 测试：未知类型仍返回 Ignore

### Step 2: ContentBlock 模型 + StreamingAccumulator（tester → reviewer → coder）
- 测试：thinking chunk 累积到 Thinking block
- 测试：text → thinking → text 产生三个 block
- 测试：tool_call 隐式关闭 thinking block
- 测试：end 时 flush 所有 block 到完整消息

### Step 3: ChatRoute + ChatViewModel（tester → reviewer → coder）
- 测试：thinking chunk 期间 isStreaming=true
- 测试：streamingText 在 thinking 阶段有内容

### Step 4: UI 渲染（coder → reviewer）
- MessageList 渲染 Thinking block
- MessageList 渲染 ToolCall block

## 文件变更预估

| 文件 | 变更 |
|---|---|
| DmMappedEvent.kt | 新增 AgentThoughtChunk, AgentThoughtEnd, ToolCall, ToolCallUpdate |
| DmEventMapper.kt | 新增 4 个 case 映射 |
| DmMessage.kt | 新增 ContentBlock sealed class |
| StreamingAccumulator.kt | 支持多 block 累积 |
| DmEventProcessor.kt | 处理新事件类型 |
| ChatRoute.kt | 累积 thinking 文本 |
| ChatViewModel.kt | thinking 阶段 isStreaming=true |
| MessageList.kt | 渲染 Thinking/ToolCall block |
| 测试文件 | DmEventMapperTest, StreamingAccumulatorTest, ChatViewModelTest 等 |
