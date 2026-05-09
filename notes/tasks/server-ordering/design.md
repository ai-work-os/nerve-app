# 服务器排序 — 设计文档

> 日期：2026-05-09
> 目标：让用户在 Android 端自定义服务器顺序，全局生效

## 需求

1. **Servers 页**（排序源头）：用户能调整服务器顺序，支持两种交互
   - 长按拖拽
   - 每行 ↑↓ 按钮
2. **Agents 页**：按服务器分组显示 agents，分组顺序遵循 Servers 页的顺序
3. **Spawn agent 对话框**：服务器下拉列表顺序遵循 Servers 页的顺序

## 架构 — 利用现有结构，不引入新存储

现状回顾：
- `ServerConfigStore.saveAll(List<ServerConfig>)` 已用 List 顺序持久化（SharedPreferences JSON 数组）
- `ServerRegistry.servers: StateFlow<List<ServerConfig>>` 是全局唯一来源
- Servers / Agents / Spawn 都订阅同一个 `servers` Flow

**结论：顺序天然存在于 List 顺序中，不需要新加 `order` 字段或新存储。**

只需要：
1. 在 `ServerRegistry` 暴露 `reorderServers(orderedIds)` 接口
2. 在 UI 触发该接口
3. 修复 Agents 页当前按 `serverName` 字母序排序的逻辑（覆盖了真实顺序）

## 改动点

### 1. domain/server/ServerRegistry.kt

新增接口方法：

```kotlin
suspend fun reorderServers(orderedIds: List<String>)
```

实现要点：
- 按 `orderedIds` 的顺序排列 `_servers.value`
- 不在 `orderedIds` 里的现有 server 保持原有相对顺序，追加在末尾（防御性：UI 数据可能短暂滞后）
- 调用 `configStore.saveAll(reordered)` 持久化
- 调用 `publishSnapshots()` 让 nodes/channels 视图也按新顺序重发
- 不影响 clients/connections/缓存

日志：`registry_reorder` with `orderedIds`, `finalCount`

### 2. presentation/server/ServerViewModel.kt

新增方法：

```kotlin
suspend fun reorderServers(orderedIds: List<String>)
```

委托给 `serverRegistry.reorderServers`。失败时写 `errorMessage`。

### 3. ui/server/ServersScreen.kt

每行新增：
- 上移按钮（↑）：第一行禁用
- 下移按钮（↓）：最后一行禁用
- 点击立即调用 `onReorder(newOrderedIds)`

容器层：用 `sh.calvin.reorderable:reorderable` 替换 `LazyColumn` 的标准实现，长按行触发拖拽。

新增 callback 入参：

```kotlin
onReorder: (orderedIds: List<String>) -> Unit
```

### 4. ui/nodes/NodesScreen.kt

**bug 修复**：当前 `state.items.sortedWith(compareBy { it.serverName }).groupBy { it.serverName }` 按字母序排，覆盖了真实顺序。

改为：
- 接收 `servers: List<ServerConfig>` 作为顺序来源（已经传入）
- 按 `servers` 的顺序遍历，每个 server 取自己的 nodes 子集（按 nodeName 内部排序）
- 分组 header 用 `server.name`

抽出纯函数便于测试：

```kotlin
internal fun groupNodesByServer(
    items: List<NodeItemUi>,
    servers: List<ServerConfig>,
): List<Pair<ServerConfig, List<NodeItemUi>>>
```

### 5. ui/nodes/NodesScreen.kt — Spawn 对话框

无需改动：`servers.forEach { ... }` 已直接按入参顺序展示。

### 6. build.gradle.kts

新增依赖：

```kotlin
implementation("sh.calvin.reorderable:reorderable:2.4.3")
```

## 数据流

```
User 拖拽/点 ↑↓
  → ServersScreen 计算 newOrderedIds
  → onReorder(newOrderedIds)
  → ServerViewModel.reorderServers
  → ServerRegistry.reorderServers
    → configStore.saveAll(reordered)  // 持久化
    → _servers.value = reordered      // 触发所有订阅者刷新
    → publishSnapshots()              // 触发 nodes/channels 重发
  ← Servers / Agents / Spawn 三个页面同步刷新
```

## 测试策略（TDD — 先写测试再写代码）

### 单元测试（jvm）

1. **`SharedPrefsServerConfigStoreTest`** — 新增用例：`saveAll preserves explicit order across reload`
2. **`RealServerRegistryReorderTest`** — 新建
   - `reorderServers([B, A, C]) 重排为 [B, A, C]`
   - `reorderServers 持久化到 configStore`
   - `reorderServers 包含未知 ID 时跳过该 ID`
   - `reorderServers 缺少现有 ID 时把它追加到末尾`
   - `reorderServers 不影响 clients map / connections cache`
3. **`ServerViewModelReorderTest`** — 新建
   - `reorderServers 调用 registry.reorderServers`
   - registry 抛错时写 errorMessage
4. **`NodesScreenGroupingTest`**（新建，纯函数测试）
   - `groupNodesByServer 按 servers 顺序分组`
   - `空 nodes 不出现在结果中`
   - `nodes 中的未知 serverId 被丢弃`

### UI 测试（androidTest）

5. **`ServersScreenReorderTest`**（新建）
   - 点击 ↑ 把第二行换到第一行
   - 第一行的 ↑ 禁用，最后一行的 ↓ 禁用
   - 长按拖拽暂时不写 UI 测试（reorderable 库自身有覆盖；手动验收即可）

## 不做（YAGNI）

- 不加 "order" 字段到 ServerConfig（List 顺序已经表达）
- 不做后端同步（只 Android 本地）
- 不做"重置到默认顺序"按钮（用户没要求）
- 长按拖拽不写自动化 UI 测试（成本高、收益低）

## 验收

- [ ] 新增/修改测试全部通过（`./gradlew test`）
- [ ] 装到 Android 设备：拖拽 + ↑↓ 可用
- [ ] Servers 页排好序后，杀掉 app 重启，顺序保留
- [ ] Agents 页分组顺序与 Servers 一致
- [ ] Spawn 对话框下拉顺序与 Servers 一致
