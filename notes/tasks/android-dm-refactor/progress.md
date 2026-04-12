# Android DM 历史消息体验优化

## 子任务

### 1. Summary Mode
- [x] DmMessage.textContent 属性 — 过滤 Thinking/ToolCall，只保留 Text block
- [x] MessageList 历史消息渲染使用 textContent（P2 fix: 预过滤避免 phantom items）
- [x] Streaming 保持显示全部 block
- [x] 单元测试（5 个 textContent 测试）

### 2. Batch Replay
- [x] ChatViewModel.enterDm 接入 beginBatch/endBatch（P1 fix: 用 onStart 确保 collect 就绪后再 subscribe）
- [x] subscribe 期间 replay 事件攒批，完成后一次性更新 UI
- [x] 单元测试

### 3. UI 优化
- [x] 用户消息气泡右对齐贴右边缘
- [x] AI 左侧圆形头像（首字母），用户右侧圆形头像
- [x] Close 按钮增加 AlertDialog 确认，防止误触
- [x] 消息长按复制到剪贴板（AI + 用户消息）
- [x] 全量 unit test 通过

## 进度

| 时间 | 步骤 | 状态 |
|------|------|------|
| - | 写测试（红） | 完成 |
| - | 实现代码（绿） | 完成 |
| - | reviewer review | 完成 |
| - | P1/P2 修复 + Close确认 + 长按复制 | 完成 |
| - | 全量 unit test 通过 | 完成 |

## 改动文件

| 文件 | 改动 |
|------|------|
| `DmMessage.kt` | +textContent 属性 |
| `MessageList.kt` | remember 预过滤 visibleMessages，避免 phantom items |
| `MessageBubble.kt` | +Avatar, 右对齐, combinedClickable 长按复制 |
| `ChatViewModel.kt` | onStart{beginBatch→subscribe→endBatch}，保证时序 |
| `TopBar.kt` | Close 按钮 → AlertDialog 确认 |
| `DmSessionManagerTest.kt` | +5 个 textContent 测试 |
| `ChatViewModelTest.kt` | +1 个 batch replay 测试 |
