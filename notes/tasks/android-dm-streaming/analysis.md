# Android DM 流式显示 — 根因分析

## Q1: server 端 agent_message_chunk 的 content 结构

**结论：content 是单个 ContentBlock 对象 `{type:"text", text:"..."}`，不是数组。**

ACP schema 定义：
- `agent_message_chunk` 的 content 字段类型是 `ContentBlock`（单个对象）
- `ContentBlock` 是 discriminated union（type 字段区分），最常见的是 `{type:"text", text:"..."}`
- 完整消息（如 prompt response）的 content 是 `ContentBlock[]` 数组

server 端 DM capture 也按对象处理（node-pool.ts:301）：
```js
const chunkText = (params as any)?.update?.content?.text;
```

## Q2: ChatRoute 文本提取是否会丢数据

**结论：ChatRoute 不会丢数据，但缺乏健壮性。**

ChatRoute（第60-65行）：
```kotlin
update["content"]?.jsonObject?.get("text")?.jsonPrimitive?.content
```

这能正确解析 `{type:"text", text:"..."}` — 因为 chunk 的 content 就是单个对象。

DmEventMapper.extractText() 同时处理对象和数组两种格式，更健壮，但对于 chunk 事件来说两者等价。

**潜在风险**：如果未来 ACP 改成数组格式，ChatRoute 会丢数据而 DmEventMapper 不会。建议统一用 extractText() 逻辑。

## Q3: UI 层是否渲染 streamingText

**结论：是的，正确渲染。**

数据流：
1. ChatRoute 监听 events → 累积 streamingText（String state）
2. ChatRoute 传递 `streamingText` 给 ChatScreen
3. ChatScreen 传递 `streamingText` + `isStreaming` 给 MessageList
4. MessageList（第53-77行）：
   - `isStreaming && streamingText.isNotEmpty()` → 显示临时 MessageBubble（role=ASSISTANT）
   - `isStreaming && streamingText.isEmpty()` → 显示 "Assistant is typing..."
5. `agent_message_end` 时 streamingText 清空，DmEventProcessor 将完整消息写入 DmStore

**无问题。**

## Q4: isStreaming 状态在 UI 的表现

**结论：有 typing indicator。**

- ChatViewModel.streamingJob 监听事件设置 `isStreaming`：
  - `agent_message_start` / `agent_message_chunk` → `isStreaming = true`
  - `agent_message_end` / node idle → `isStreaming = false`
- MessageList 在 `isStreaming=true && streamingText=""` 时显示 "Assistant is typing..."
- TopBar 也接收 state.isStreaming（可能用于显示状态）

## Q5: agent_thought_chunk 的 content 结构

**结论：与 agent_message_chunk 相同，都是 ContentChunk。**

ACP schema 中 `agent_thought_chunk` 和 `agent_message_chunk` 都引用同一个 `$ref: ContentChunk`，content 字段都是单个 `ContentBlock` 对象。

---

## Android vs TUI 事件处理对比

### TUI 处理的 sessionUpdate 类型（完整列表）

| sessionUpdate 类型 | TUI 处理 | Android 处理 | 差距 |
|---|---|---|---|
| agent_message_start | 初始化流式消息，设 is_responding=true | ChatRoute 清空 streamingText；ChatViewModel 设 isStreaming=true | **基本对齐** |
| agent_message_chunk | 累积到 Text block | ChatRoute 累积 streamingText；DmEventMapper 提取文本 | **基本对齐** |
| agent_message_end | 持久化完整消息，清 is_responding | 清空 streamingText；DmEventProcessor 写入 DmStore | **基本对齐** |
| agent_thought_chunk | 累积到 Thinking block，显示 elapsed timer | **未处理** | **缺失** |
| agent_thought_end | 标记 thinking 完成 | **未处理** | **缺失** |
| tool_call | 创建 ToolCall block，sidebar 显示工具名+计时 | **未处理** | **缺失** |
| tool_call_update | 更新 ToolCall 状态，显示结果 | **未处理** | **缺失** |
| user_message | 回放到 dm_history | DmEventMapper 映射为 UserMessage | **对齐** |
| usage_update | sidebar 显示 token 用量 | **未处理** | **缺失** |
| node_log | 追加为系统消息 | **未处理** | **缺失** |

### TUI 流式消息架构（Android 需对齐）

TUI 用 ContentBlock 模型表示消息内部结构：

```
Message {
  blocks: Vec<ContentBlock> {
    Text { text }           // agent_message_chunk 累积
    Thinking { text, started_at, finished_at }  // agent_thought_chunk 累积
    ToolCall { id, name, input, status }        // tool_call 创建
    ToolResult { text, is_error }               // tool_call_update 产生
  }
}
```

关键设计：
- **同类 block 合并**：连续 text chunk 追加到同一 Text block；连续 thinking chunk 追加到同一 Thinking block
- **异类 block 新建**：tool_call 后的 text chunk 创建新 Text block（而非追加到 tool_call 前的文本）
- **隐式关闭**：tool_call 会隐式关闭未完成的 Thinking block
- **持久化时机**：agent_message_end 时从 streaming map 取出完整 Message，转为 DmMessage 持久化

### Android 需要补齐的能力（按优先级）

1. **P0 — agent_thought_chunk/end**：显示 "thinking..." 状态和思考内容（可折叠）
2. **P0 — tool_call / tool_call_update**：显示工具调用名称、状态、结果
3. **P1 — usage_update**：显示 token 用量（可在 TopBar 或设置页）
4. **P2 — node_log**：显示诊断日志（开发者模式）

### Android 当前架构评估

- ChatRoute 和 ChatViewModel **各自独立监听事件**，ChatRoute 管 streamingText，ChatViewModel 管 isStreaming + DmEventProcessor
- 这种分离对于纯文本流式足够，但加入 thinking/tool_call 后会变复杂
- 建议参考 TUI 的 ContentBlock 模型，在 Android 侧也引入 block 概念，让 MessageList 能渲染多种 block 类型
