# node.update 原始 JSON 格式分析

## 核心发现

### 1. agent_message_start 是否由 server 发送？

**是。server 透传，不伪造。**

数据流：ACP agent (Claude Code) → stdio → acp-client.ts 拦截 `session/update` → 调用 `onUpdate(params)` → node-pool.ts 触发 `onEvent("node.update", node, params)` → server.ts 转发给 subscription-manager → WS 推送给订阅者。

server 不生成任何 sessionUpdate，完全透传 ACP agent 发出的原始事件。`agent_message_start` 是 ACP 协议标准事件，由 agent 在开始响应时发出。

### 2. TUI 的隐式 start 逻辑

**TUI 不依赖 agent_message_start。** dm_view.rs 第 225-228 行：

```rust
pub fn apply_streaming_event(&mut self, agent_name: &str, kind: &str, update: &Value) -> bool {
    if !self.streaming_messages.contains_key(agent_name) {
        self.start_streaming_message(agent_name);  // 隐式创建
    }
    // ...
}
```

收到任何 streaming 事件（chunk/thought/tool_call）时，如果还没有 streaming message，自动创建。这是防御性设计——即使 `agent_message_start` 丢失或到达顺序不对，也不影响显示。

**Android 也应采用这种隐式 start 策略。**

### 3. node.update 的完整 JSON 结构

server 发送的 WebSocket 消息格式（subscription-manager.ts 第 64-69 行）：

```json
{
  "jsonrpc": "2.0",
  "method": "node.update",
  "params": {
    "nodeId": "xxxx",
    "name": "agent-name",
    "update": { ... }    // ← ACP SessionNotification.update 原样透传
  }
}
```

`params.update` 就是 ACP 原始 update 对象，以 `sessionUpdate` 字段区分类型。

### 4. 各事件类型的 update 字段结构

#### agent_message_start

```json
{
  "sessionUpdate": "agent_message_start"
}
```

无 content 字段。仅作为信号。

#### agent_message_chunk

```json
{
  "sessionUpdate": "agent_message_chunk",
  "content": {
    "type": "text",
    "text": "Hello world"
  }
}
```

content 是单个 ContentBlock 对象（非数组）。

#### agent_message_end

```json
{
  "sessionUpdate": "agent_message_end",
  "stopReason": "end_turn"
}
```

可能包含 stopReason。

#### agent_thought_chunk

```json
{
  "sessionUpdate": "agent_thought_chunk",
  "content": {
    "type": "text",
    "text": "Let me think about..."
  }
}
```

与 agent_message_chunk 相同结构（同为 ContentChunk）。

#### agent_thought_end

```json
{
  "sessionUpdate": "agent_thought_end"
}
```

#### tool_call（ACP flat 格式，Claude Code 使用）

```json
{
  "sessionUpdate": "tool_call",
  "toolCallId": "toolu_xxx",
  "title": "Read file.txt",
  "kind": "tool_use",
  "_meta": {
    "claudeCode": {
      "toolName": "Read"
    }
  },
  "rawInput": "{\"file_path\": \"/path/to/file\"}"
}
```

关键字段：
- `toolCallId`：唯一 ID，用于匹配 tool_call_update
- `_meta.claudeCode.toolName`：真实工具名（Read/Edit/Bash 等）
- `title`：人类可读描述
- `rawInput`：工具输入（JSON 字符串）

#### tool_call（Legacy nested 格式）

```json
{
  "sessionUpdate": "tool_call",
  "toolCall": {
    "id": "xxx",
    "name": "Read",
    "input": { "file_path": "/path" }
  }
}
```

TUI 两种格式都支持。Android 也应支持。

#### tool_call_update（ACP flat 格式）

```json
{
  "sessionUpdate": "tool_call_update",
  "toolCallId": "toolu_xxx",
  "status": "completed",
  "content": [
    {
      "type": "content",
      "content": {
        "type": "text",
        "text": "file contents here..."
      }
    }
  ]
}
```

关键字段：
- `toolCallId`：匹配对应 tool_call
- `status`：`"pending"` | `"in_progress"` / `"running"` | `"completed"` | `"failed"`
- `content`：数组格式，每个元素的文本在 `content[].content.text`

#### tool_call_update（Legacy nested 格式）

```json
{
  "sessionUpdate": "tool_call_update",
  "toolCallUpdate": {
    "id": "xxx",
    "status": "completed",
    "content": "result text"
  }
}
```

Legacy 的 content 是直接字符串。

#### usage_update

```json
{
  "sessionUpdate": "usage_update",
  "used": 15000,
  "size": 200000,
  "cost": {
    "amount": 0.05,
    "currency": "USD"
  }
}
```

注意：server 会在 node.ts pushUpdate 中对 size 做 normalize（用 model-registry 覆盖）。

#### user_message（server 构造）

```json
{
  "sessionUpdate": "user_message",
  "content": {
    "type": "text",
    "text": "user input here"
  }
}
```

这是 **唯一一个 server 构造的事件**（node-pool.ts 第 490 行），用于将用户 prompt 作为事件推送给订阅者。还会附带 `from` 字段：

```json
{
  "update": { ... },
  "from": { "nodeId": "xxx", "name": "user-node" }
}
```

#### node_log（server 构造）

```json
{
  "sessionUpdate": "node_log",
  "entries": [
    { "timestamp": 1234567890, "level": "info", "text": "log message" }
  ]
}
```

由 server.ts 第 392 行构造，来自 agent 进程的 stderr 输出。

### 5. Android RpcSerializer 当前解析逻辑

RpcSerializer.parseNotification 对 `node.update` 的处理（第 27-41 行）：

```kotlin
"node.update" -> {
    val nodeId = params.string("nodeId") ?: return null
    val name = params.string("name") ?: return null
    NerveEvent.NodeUpdate(
        nodeId = nodeId,
        name = name,
        detail = buildJsonObject {
            params.forEach { (key, value) ->
                if (key != "nodeId" && key != "name") put(key, value)
            }
        },
    )
}
```

它将 `nodeId`/`name` 以外的所有字段放入 `detail`，所以 `detail["update"]` 就是完整的 ACP update 对象。**解析层没问题，所有字段都保留了。**

问题在下游——ChatRoute 和 DmEventMapper 只处理了 agent_message_start/chunk/end 和 user_message，忽略了 thought/tool_call/tool_call_update/usage_update/node_log。

### 6. 关于临时日志

在 RpcSerializer.parseNotification 的 `"node.update"` 分支加如下日志即可抓完整 JSON：

```kotlin
"node.update" -> {
    Logger.d("RpcRaw", "node.update params=${params}")
    // ... 原有逻辑
}
```

这会打印 server 推送的完整 params JSON，包含 update 内的所有字段。
