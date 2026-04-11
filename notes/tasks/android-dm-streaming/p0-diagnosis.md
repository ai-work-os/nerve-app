# P0 根因诊断

## 问题 1：AI 消息流式中可见，结束后消失

### 根因：`ensureState` 创建的隐式状态 nodeName 为空 → `buildAssistantMessage` 生成的消息 nodeId/nodeName 不正确

**完整数据流跟踪：**

#### 场景 A：正常流程（有 agent_message_start）

1. `agent_message_start` → DmEventMapper 生成 `AgentMessageStart(nodeId, nodeName, messageId, timestamp)`
2. DmEventProcessor 调用 `accumulator.onStart(key, nodeId, nodeName, messageId, timestamp)`
3. StreamingAccumulator 创建 `DmStreamingState(messageId, nodeId, nodeName, ...)`
4. `agent_message_chunk` → `accumulator.onChunk(key, text, timestamp)` → 追加到 state.text
5. `agent_message_end` → `accumulator.onEnd()` → `buildAssistantMessage(state, content, timestamp)` → 生成 DmMessage → store.appendMessage

**这条路径正常工作。**

#### 场景 B：thought/tool_call 先于 agent_message_start 到达

1. `agent_thought_chunk` → DmEventProcessor 调用 `accumulator.onThoughtChunk(key, text, timestamp)`
2. StreamingAccumulator.ensureState 创建隐式状态：
   ```kotlin
   DmStreamingState("implicit:${key.value}:$timestamp", key.value, "", timestamp, timestamp)
   //                                                     ^^^^^^^^  ^^
   //                                                     nodeId=key.value (如 "server1:node1")  nodeName=""
   ```
3. 后续 `agent_message_start` → `onStart()` **覆盖**整个 state（第 26 行 `activeStreams[key] = ...`），**丢弃已累积的 thought blocks**
4. 或者：如果没有 `agent_message_start`，隐式状态的 nodeId 是 `key.value`（如 "server1:node1"）而非真实 nodeId

**但这不是消息消失的主因。**

#### 真正的根因：`buildAssistantMessage` 只保留 Text blocks

StreamingAccumulator.kt 第 94-105 行：

```kotlin
private fun buildAssistantMessage(state: DmStreamingState, content: String, timestamp: Long): DmMessage? {
    if (content.isBlank()) return null  // ← BUG 1: content 来自 state.text，但 thought/tool 不写 state.text
    return DmMessage(
        id = buildAssistantMessageId(state.nodeId, timestamp, content),
        role = DmRole.ASSISTANT,
        content = content,
        timestamp = timestamp,
        nodeId = state.nodeId,
        nodeName = state.nodeName,
        blocks = state.blocks.filterIsInstance<ContentBlock.Text>(),  // ← BUG 2: 丢弃 Thinking 和 ToolCall blocks
    )
}
```

**BUG 1（致命）：`content` 取自 `state.text`（StringBuilder），但只有 `onChunk` 写入 `state.text`，`onThoughtChunk` 和 `onToolCall` 不写。如果 AI 回复只有 thinking + tool_call 没有 text chunk（很常见），`state.text` 为空 → `content.isBlank()` = true → 返回 null → 消息不持久化 → 消失。**

**BUG 2（次要）：`blocks = state.blocks.filterIsInstance<ContentBlock.Text>()` 丢弃了 Thinking 和 ToolCall blocks，即使持久化了也没有完整信息。**

#### 消息消失的完整因果链

1. AI 开始响应 → `agent_message_start` → ChatRoute 清空 streamingText
2. AI 先 thinking → `agent_thought_chunk` → accumulator 记录 blocks（但不写 state.text）
3. AI 调用工具 → `tool_call` → accumulator 记录 blocks（但不写 state.text）
4. AI 写文本回复 → `agent_message_chunk` → **ChatRoute 累积 streamingText**（UI 可见）同时 accumulator 写 state.text
5. AI 结束 → `agent_message_end` → ChatRoute 清空 streamingText（流式气泡消失）→ accumulator.onEnd() 生成 DmMessage → store.appendMessage
6. **如果步骤 4 的文本量极少或为空**（AI 只用工具没有文字回复）→ state.text 为空 → buildAssistantMessage 返回 null → **store 没有收到消息 → UI 消息列表无新项 → 流式气泡消失后无持久气泡 → 消息消失**

### DmStore seenIds 去重风险

**低风险但存在。** `buildAssistantMessageId` 使用 `(nodeId, timestamp, content)` 生成 ID：

```kotlin
fun buildAssistantMessageId(nodeId: String, timestamp: Long, content: String): String =
    "assistant:$nodeId:$timestamp:${stableHash(content)}"
```

如果两条消息 nodeId + timestamp + content 完全相同（极低概率），会被去重。隐式状态下 nodeId 是 `key.value`（如 "server1:node1"）而非真实 nodeId，不影响去重但会导致 ID 格式异常。

---

## 问题 2：isStreaming 不 reset

### 根因：`tool_call` 和 `tool_call_update` 事件不在 streamingJob 的 when 分支中

ChatViewModel.kt 第 103-106 行：

```kotlin
when (updateKind) {
    "agent_message_start", "agent_message_chunk", "agent_thought_chunk" -> setStreaming(key.value, true)
    "agent_message_end" -> setStreaming(key.value, false)
}
```

**问题分析：**

1. **`agent_thought_chunk` 设置 `isStreaming = true`** — 正确，需要显示 thinking 状态
2. **但 `agent_thought_end` 没有处理** — thinking 结束后不会 reset isStreaming
3. **`tool_call` 没有处理** — 工具执行期间无状态变化
4. **`tool_call_update` 没有处理** — 工具完成后无状态变化

#### 典型故障时序

```
agent_message_start    → isStreaming = true   ✓
agent_thought_chunk    → isStreaming = true   ✓ (保持)
agent_thought_chunk    → isStreaming = true   ✓ (保持)
tool_call              → (未处理)             isStreaming 仍 = true
tool_call_update       → (未处理)             isStreaming 仍 = true
agent_message_chunk    → isStreaming = true   ✓ (保持)
agent_message_end      → isStreaming = false  ✓ (正确 reset)
```

**正常情况下 `agent_message_end` 能最终 reset。** 但以下场景会卡住：

**场景 A：agent_message_end 丢失**
- WebSocket 断线重连时可能丢事件
- 此时只有 `node.statusChanged(idle)` 能 reset

**场景 B：ChatRoute 中 streamingText 清空但 isStreaming 未清**
- ChatRoute 第 70 行：`"agent_message_end" -> streamingText = ""`
- ChatRoute 第 74 行：`node idle -> streamingText = ""`
- 但 isStreaming 由 ChatViewModel 控制，不在 ChatRoute 中
- **时序问题**：ChatRoute 和 ChatViewModel 是两个独立的事件监听者，存在竞态

**场景 C（最可能）：`agent_thought_chunk` 持续到达**
- 如果 AI 的 thinking 事件在 `agent_message_end` 之后到达（乱序），会重新设置 `isStreaming = true`
- 此时 `agent_message_end` 已过，只有 node idle 能救

**isStreaming 不 reset 的表现**：MessageList 持续显示流式气泡或 "Assistant is typing..."，即使 AI 已经停止响应。

---

## 修复方案

### 问题 1 修复

**StreamingAccumulator.buildAssistantMessage**：

1. `content` 应从所有 blocks 提取文本，不只是 `state.text`
2. 或者：当 `state.text` 为空但 `state.blocks` 非空时，从 blocks 构建 content
3. `blocks` 参数不应 filter，保留全部 blocks

```kotlin
private fun buildAssistantMessage(state: DmStreamingState, content: String, timestamp: Long): DmMessage? {
    val finalContent = content.ifBlank {
        state.blocks.joinToString("\n") { block ->
            when (block) {
                is ContentBlock.Text -> block.text
                is ContentBlock.Thinking -> "[thinking] ${block.text}"
                is ContentBlock.ToolCall -> "[tool: ${block.toolName}]"
            }
        }.trim()
    }
    if (finalContent.isBlank()) return null
    return DmMessage(
        id = buildAssistantMessageId(state.nodeId, timestamp, finalContent),
        role = DmRole.ASSISTANT,
        content = finalContent,
        timestamp = timestamp,
        nodeId = state.nodeId,
        nodeName = state.nodeName,
        blocks = state.blocks.toList(),  // 保留全部 blocks
    )
}
```

### 问题 2 修复

**ChatViewModel.streamingJob**：补齐事件处理

```kotlin
when (updateKind) {
    "agent_message_start", "agent_message_chunk", "agent_thought_chunk" -> setStreaming(key.value, true)
    "agent_message_end" -> setStreaming(key.value, false)
    // tool_call 期间保持 streaming = true（不变）
    // tool_call_update 不影响 streaming（工具结束后会有 message_chunk 或 message_end）
}
```

当前逻辑其实对 tool_call 期间的行为是正确的（保持 true）。问题不在这里。

**真正需要的修复**：确保 `agent_message_end` 一定能执行。加防御性 timeout：

```kotlin
// 在 setStreaming(true) 时启动 timeout
// 如果 30s 内没有收到 end，自动 reset
```

或更简单：确保 `node.statusChanged(idle)` 路径一定能 reset（当前代码已有，第 108-111 行）。验证 idle 事件是否真的到达即可。
