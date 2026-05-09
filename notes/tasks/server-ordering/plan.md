# Server Ordering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android 端用户可自定义服务器顺序，Servers / Agents / Spawn 三个页面统一遵循该顺序。

**Architecture:** 利用现有 `ServerConfigStore.saveAll(List<ServerConfig>)` 用 List 顺序天然持久化的能力 — 不引入新存储字段。新增 `ServerRegistry.reorderServers(orderedIds)` 作为单一入口；UI 层提供 ↑↓ 按钮 + 长按拖拽两种交互；修复 Agents 页按字母序覆盖真实顺序的 bug。

**Tech Stack:** Kotlin + Jetpack Compose + StateFlow + JUnit5 + `sh.calvin.reorderable:reorderable` (新增依赖)

参考设计文档：`notes/tasks/server-ordering/design.md`

---

## File Structure

| 路径 | 状态 | 责任 |
|------|------|------|
| `app/build.gradle.kts` | 修改 | 加 reorderable 依赖 |
| `app/src/main/java/com/nerve/android/domain/server/ServerRegistry.kt` | 修改 | 接口加 `reorderServers`，`RealServerRegistry` 实现 |
| `app/src/main/java/com/nerve/android/presentation/server/ServerViewModel.kt` | 修改 | 加 `reorderServers` 委托 |
| `app/src/main/java/com/nerve/android/ui/server/ServersScreen.kt` | 修改 | 行内 ↑↓ 按钮 + 长按拖拽 + `onReorder` 回调 |
| `app/src/main/java/com/nerve/android/ui/nodes/NodesScreen.kt` | 修改 | 用 `groupNodesByServer` 替换字母序排序 |
| `app/src/main/java/com/nerve/android/ui/nodes/NodeGrouping.kt` | 新建 | `groupNodesByServer` 纯函数 |
| `app/src/main/java/com/nerve/android/ui/app/AppRoute.kt` | 修改 | 接 `onReorder` |
| `app/src/test/java/com/nerve/android/presentation/FakePresentationDoubles.kt` | 修改 | `FakeServerRegistry` 加 `reorderServerCalls` |
| `app/src/test/java/com/nerve/android/domain/server/ServerRegistryReorderTest.kt` | 新建 | reorder 行为测试 |
| `app/src/test/java/com/nerve/android/domain/server/SharedPrefsServerConfigStoreTest.kt` | 修改 | 加显式顺序保留测试 |
| `app/src/test/java/com/nerve/android/presentation/server/ServerViewModelTest.kt` | 修改 | 加 reorder 委托测试 |
| `app/src/test/java/com/nerve/android/ui/nodes/NodeGroupingTest.kt` | 新建 | 分组纯函数测试 |

---

## Task 1: 加 reorderable 依赖

**Files:**
- Modify: `app/build.gradle.kts:65-71`

- [ ] **Step 1: 加依赖**

修改 `app/build.gradle.kts`，在 `implementation("io.noties.markwon:ext-strikethrough:4.6.2")` 之后加一行：

```kotlin
    implementation("sh.calvin.reorderable:reorderable:2.4.3")
```

- [ ] **Step 2: 同步 Gradle 验证可解析**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:dependencies --configuration releaseRuntimeClasspath 2>&1 | grep -i "reorderable"
```

Expected: 输出包含 `sh.calvin.reorderable:reorderable:2.4.3`

- [ ] **Step 3: Commit**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && git add app/build.gradle.kts && git commit -m "deps(android): 加 sh.calvin.reorderable 用于服务器列表拖拽"
```

---

## Task 2: SharedPrefsServerConfigStore 顺序保留测试（红 → 绿）

> 现有 `saveAll` 已用 JSON 列表顺序持久化，这一步是补显式覆盖测试，防止未来回归。

**Files:**
- Test: `app/src/test/java/com/nerve/android/domain/server/SharedPrefsServerConfigStoreTest.kt`

- [ ] **Step 1: 加测试**

在 `SharedPrefsServerConfigStoreTest` 类内追加：

```kotlin
    @Test
    fun `saveAll preserves explicit order across reload`() = runTest {
        val prefs = InMemorySharedPreferences()
        val store = SharedPrefsServerConfigStore(prefs)
        val ordered = listOf(
            ServerConfig(id = "home", name = "Home Server", address = "100.75.43.90:4800"),
            ServerConfig(id = "mac", name = "Mac", address = "100.109.126.37:4800"),
            ServerConfig(id = "mac-test", name = "Mac (test 4801)", address = "100.109.126.37:4801"),
        )

        store.saveAll(ordered)

        assertEquals(ordered, SharedPrefsServerConfigStore(prefs).load())
    }
```

- [ ] **Step 2: 跑测试，应该直接通过（已有功能）**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:testDebugUnitTest --tests "com.nerve.android.domain.server.SharedPrefsServerConfigStoreTest"
```

Expected: BUILD SUCCESSFUL，4 个测试全过

- [ ] **Step 3: Commit**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && git add app/src/test/java/com/nerve/android/domain/server/SharedPrefsServerConfigStoreTest.kt && git commit -m "test(server): 显式覆盖 saveAll 保留列表顺序"
```

---

## Task 3: ServerRegistry.reorderServers — 接口 + 测试（红）

**Files:**
- Modify: `app/src/main/java/com/nerve/android/domain/server/ServerRegistry.kt:22-35`
- Modify: `app/src/test/java/com/nerve/android/presentation/FakePresentationDoubles.kt`
- Test: `app/src/test/java/com/nerve/android/domain/server/ServerRegistryReorderTest.kt` (new)

- [ ] **Step 1: 接口加方法**

修改 `ServerRegistry.kt:22-35`，在 `interface ServerRegistry` 内 `fun triggerReconnectAll()` 之前加：

```kotlin
    suspend fun reorderServers(orderedIds: List<String>)
```

- [ ] **Step 2: 临时 stub 让编译通过（等会儿真实现）**

在 `RealServerRegistry` 类末尾、`private fun publishConnections` 之前加：

```kotlin
    override suspend fun reorderServers(orderedIds: List<String>) {
        TODO("implemented in next step")
    }
```

- [ ] **Step 3: FakeServerRegistry 加方法**

修改 `app/src/test/java/com/nerve/android/presentation/FakePresentationDoubles.kt` 的 `FakeServerRegistry`：

在 `var triggerReconnectAllCalls = 0` 之后加：

```kotlin
    val reorderServerCalls = mutableListOf<List<String>>()
```

在 `override fun triggerReconnectAll()` 之前加：

```kotlin
    override suspend fun reorderServers(orderedIds: List<String>) {
        reorderServerCalls += orderedIds
        servers.value = orderedIds.mapNotNull { id -> servers.value.firstOrNull { it.id == id } } +
            servers.value.filterNot { config -> orderedIds.contains(config.id) }
    }
```

- [ ] **Step 4: 写新测试文件**

创建 `app/src/test/java/com/nerve/android/domain/server/ServerRegistryReorderTest.kt`：

```kotlin
package com.nerve.android.domain.server

import com.nerve.android.transport.ClientRegistration
import com.nerve.android.transport.ServerConfig
import com.nerve.android.transport.model.ChannelInfo
import com.nerve.android.transport.model.NodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ServerRegistryReorderTest {
    @Test
    fun `reorderServers rearranges servers by orderedIds`() = runTest {
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("a", "A", "10.0.0.1:4800"),
                ServerConfig("b", "B", "10.0.0.2:4800"),
                ServerConfig("c", "C", "10.0.0.3:4800"),
            ),
        )
        val registry = startedRegistry(store)

        registry.reorderServers(listOf("c", "a", "b"))

        assertEquals(listOf("c", "a", "b"), registry.servers.value.map { it.id })
    }

    @Test
    fun `reorderServers persists new order to store`() = runTest {
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("a", "A", "10.0.0.1:4800"),
                ServerConfig("b", "B", "10.0.0.2:4800"),
            ),
        )
        val registry = startedRegistry(store)

        registry.reorderServers(listOf("b", "a"))

        assertEquals(listOf("b", "a"), store.load().map { it.id })
    }

    @Test
    fun `reorderServers ignores unknown ids`() = runTest {
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("a", "A", "10.0.0.1:4800"),
                ServerConfig("b", "B", "10.0.0.2:4800"),
            ),
        )
        val registry = startedRegistry(store)

        registry.reorderServers(listOf("ghost", "b", "a"))

        assertEquals(listOf("b", "a"), registry.servers.value.map { it.id })
    }

    @Test
    fun `reorderServers appends missing ids in original order`() = runTest {
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("a", "A", "10.0.0.1:4800"),
                ServerConfig("b", "B", "10.0.0.2:4800"),
                ServerConfig("c", "C", "10.0.0.3:4800"),
            ),
        )
        val registry = startedRegistry(store)

        registry.reorderServers(listOf("c"))

        assertEquals(listOf("c", "a", "b"), registry.servers.value.map { it.id })
    }

    @Test
    fun `reorderServers republishes nodes in new order`() = runTest {
        val clientA = FakeNerveClient().apply {
            nodesResult = Result.success(listOf(NodeInfo("n-a", "bot-a")))
            channelsResult = Result.success(listOf(ChannelInfo("c-a", "ch-a")))
        }
        val clientB = FakeNerveClient().apply {
            nodesResult = Result.success(listOf(NodeInfo("n-b", "bot-b")))
            channelsResult = Result.success(listOf(ChannelInfo("c-b", "ch-b")))
        }
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("a", "A", "10.0.0.1:4800"),
                ServerConfig("b", "B", "10.0.0.2:4800"),
            ),
        )
        val factory = FakeNerveClientFactory(
            mutableMapOf(
                "a" to ArrayDeque(listOf(clientA)),
                "b" to ArrayDeque(listOf(clientB)),
            ),
        )
        val registry = RealServerRegistry(store, factory, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        registry.start(ClientRegistration(name = "android-ui"))

        registry.reorderServers(listOf("b", "a"))

        assertEquals(listOf("b:n-b", "a:n-a"), registry.nodes.value.map { "${it.serverId}:${it.node.id}" })
    }

    private suspend fun startedRegistry(store: FakeServerConfigStore): RealServerRegistry {
        val factory = FakeNerveClientFactory(
            store.load().associate { config ->
                config.id to ArrayDeque(listOf(FakeNerveClient()))
            }.toMutableMap(),
        )
        val registry = RealServerRegistry(store, factory, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        registry.start(ClientRegistration(name = "android-ui"))
        return registry
    }
}
```

- [ ] **Step 5: 跑测试，应该红（TODO 抛错）**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:testDebugUnitTest --tests "com.nerve.android.domain.server.ServerRegistryReorderTest"
```

Expected: 5 个测试全部失败，原因是 `kotlin.NotImplementedError: An operation is not implemented: implemented in next step`

---

## Task 4: ServerRegistry.reorderServers — 实现（绿）

**Files:**
- Modify: `app/src/main/java/com/nerve/android/domain/server/ServerRegistry.kt`

- [ ] **Step 1: 替换 stub 为真实实现**

把 Task 3 加的 `TODO` 实现替换为：

```kotlin
    override suspend fun reorderServers(orderedIds: List<String>) {
        Logger.debug(
            "ServerRegistry",
            "registry_reorder_begin",
            mapOf("orderedIds" to orderedIds.joinToString(",")),
        )
        val current = _servers.value
        val byId = current.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { id -> byId[id] }
        val missing = current.filterNot { config -> orderedIds.contains(config.id) }
        val finalOrder = reordered + missing
        if (finalOrder.map { it.id } == current.map { it.id }) {
            Logger.debug("ServerRegistry", "registry_reorder_skip_noop")
            return
        }
        configStore.saveAll(finalOrder)
        _servers.value = finalOrder
        publishSnapshots()
        Logger.debug(
            "ServerRegistry",
            "registry_reorder_success",
            mapOf("finalOrder" to finalOrder.joinToString(",") { it.id }),
        )
    }
```

- [ ] **Step 2: 跑测试，应该全绿**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:testDebugUnitTest --tests "com.nerve.android.domain.server.ServerRegistryReorderTest"
```

Expected: BUILD SUCCESSFUL，5 个测试全过

- [ ] **Step 3: 跑全部 server domain 测试确保没破坏现有功能**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:testDebugUnitTest --tests "com.nerve.android.domain.server.*"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && git add app/src/main/java/com/nerve/android/domain/server/ServerRegistry.kt app/src/test/java/com/nerve/android/domain/server/ServerRegistryReorderTest.kt app/src/test/java/com/nerve/android/presentation/FakePresentationDoubles.kt && git commit -m "feat(server): ServerRegistry.reorderServers 持久化新顺序并重发 nodes/channels"
```

---

## Task 5: ServerViewModel.reorderServers — 测试 + 实现

**Files:**
- Modify: `app/src/main/java/com/nerve/android/presentation/server/ServerViewModel.kt`
- Modify: `app/src/test/java/com/nerve/android/presentation/server/ServerViewModelTest.kt`

- [ ] **Step 1: 加测试**

在 `ServerViewModelTest` 类内追加：

```kotlin
    @Test
    fun `reorderServers delegates to registry`() = runTest {
        val registry = FakeServerRegistry()
        registry.servers.value = listOf(
            ServerConfig("s1", "Home", "10.0.0.1:4800"),
            ServerConfig("s2", "Lab", "10.0.0.2:4800"),
        )
        val vm = ServerViewModel(registry, Dispatchers.Unconfined)

        vm.reorderServers(listOf("s2", "s1"))

        assertEquals(listOf(listOf("s2", "s1")), registry.reorderServerCalls)
    }
```

- [ ] **Step 2: 跑测试，应该红（方法不存在）**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:testDebugUnitTest --tests "com.nerve.android.presentation.server.ServerViewModelTest.reorderServers delegates to registry"
```

Expected: 编译失败，`Unresolved reference: reorderServers`

- [ ] **Step 3: 实现**

修改 `ServerViewModel.kt`，在 `suspend fun refreshServer` 之后加：

```kotlin
    suspend fun reorderServers(orderedIds: List<String>) {
        Logger.debug(
            "ServerViewModel",
            "server_reorder_begin",
            mapOf("orderedIds" to orderedIds.joinToString(",")),
        )
        runCatching { serverRegistry.reorderServers(orderedIds) }
            .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) }
    }
```

- [ ] **Step 4: 跑测试，应该绿**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:testDebugUnitTest --tests "com.nerve.android.presentation.server.ServerViewModelTest"
```

Expected: BUILD SUCCESSFUL，所有 ServerViewModelTest 通过

- [ ] **Step 5: Commit**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && git add app/src/main/java/com/nerve/android/presentation/server/ServerViewModel.kt app/src/test/java/com/nerve/android/presentation/server/ServerViewModelTest.kt && git commit -m "feat(server): ServerViewModel.reorderServers 委托给 registry"
```

---

## Task 6: groupNodesByServer 纯函数 — 测试 + 实现

**Files:**
- Create: `app/src/main/java/com/nerve/android/ui/nodes/NodeGrouping.kt`
- Test: `app/src/test/java/com/nerve/android/ui/nodes/NodeGroupingTest.kt` (new)

- [ ] **Step 1: 写测试**

创建 `app/src/test/java/com/nerve/android/ui/nodes/NodeGroupingTest.kt`：

```kotlin
package com.nerve.android.ui.nodes

import com.nerve.android.presentation.nodes.NodeItemUi
import com.nerve.android.transport.ServerConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NodeGroupingTest {
    @Test
    fun `groups nodes in servers order, not alphabetical`() {
        val servers = listOf(
            ServerConfig("home", "Home Server", "100.75.43.90:4800"),
            ServerConfig("mac", "Mac", "100.109.126.37:4800"),
        )
        val items = listOf(
            NodeItemUi(serverId = "mac", serverName = "Mac", nodeId = "n2", nodeName = "bot-mac", status = "idle"),
            NodeItemUi(serverId = "home", serverName = "Home Server", nodeId = "n1", nodeName = "bot-home", status = "idle"),
        )

        val grouped = groupNodesByServer(items, servers)

        assertEquals(listOf("home", "mac"), grouped.map { it.first.id })
    }

    @Test
    fun `nodes within a server are sorted by name`() {
        val servers = listOf(ServerConfig("s1", "S1", "10.0.0.1:4800"))
        val items = listOf(
            NodeItemUi("s1", "S1", "n2", "zeta", "idle"),
            NodeItemUi("s1", "S1", "n1", "alpha", "idle"),
        )

        val grouped = groupNodesByServer(items, servers)

        assertEquals(listOf("alpha", "zeta"), grouped.single().second.map { it.nodeName })
    }

    @Test
    fun `servers without nodes are omitted`() {
        val servers = listOf(
            ServerConfig("s1", "S1", "10.0.0.1:4800"),
            ServerConfig("s2", "S2", "10.0.0.2:4800"),
        )
        val items = listOf(
            NodeItemUi("s1", "S1", "n1", "bot", "idle"),
        )

        val grouped = groupNodesByServer(items, servers)

        assertEquals(listOf("s1"), grouped.map { it.first.id })
    }

    @Test
    fun `nodes referencing unknown server ids are dropped`() {
        val servers = listOf(ServerConfig("s1", "S1", "10.0.0.1:4800"))
        val items = listOf(
            NodeItemUi("s1", "S1", "n1", "bot", "idle"),
            NodeItemUi("ghost", "Ghost", "n2", "bot", "idle"),
        )

        val grouped = groupNodesByServer(items, servers)

        assertEquals(listOf("s1"), grouped.map { it.first.id })
        assertEquals(listOf("n1"), grouped.single().second.map { it.nodeId })
    }
}
```

- [ ] **Step 2: 跑测试，应该红（函数不存在）**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:testDebugUnitTest --tests "com.nerve.android.ui.nodes.NodeGroupingTest"
```

Expected: 编译失败，`Unresolved reference: groupNodesByServer`

- [ ] **Step 3: 写实现**

创建 `app/src/main/java/com/nerve/android/ui/nodes/NodeGrouping.kt`：

```kotlin
package com.nerve.android.ui.nodes

import com.nerve.android.presentation.nodes.NodeItemUi
import com.nerve.android.transport.ServerConfig

internal fun groupNodesByServer(
    items: List<NodeItemUi>,
    servers: List<ServerConfig>,
): List<Pair<ServerConfig, List<NodeItemUi>>> {
    val byServerId = items.groupBy { it.serverId }
    return servers.mapNotNull { server ->
        val serverNodes = byServerId[server.id] ?: return@mapNotNull null
        if (serverNodes.isEmpty()) return@mapNotNull null
        server to serverNodes.sortedBy { it.nodeName }
    }
}
```

- [ ] **Step 4: 跑测试，应该绿**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:testDebugUnitTest --tests "com.nerve.android.ui.nodes.NodeGroupingTest"
```

Expected: BUILD SUCCESSFUL，4 个测试全过

- [ ] **Step 5: Commit**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && git add app/src/main/java/com/nerve/android/ui/nodes/NodeGrouping.kt app/src/test/java/com/nerve/android/ui/nodes/NodeGroupingTest.kt && git commit -m "feat(nodes): groupNodesByServer 纯函数按 servers 顺序分组"
```

---

## Task 7: NodesScreen 用 groupNodesByServer 替换字母序

**Files:**
- Modify: `app/src/main/java/com/nerve/android/ui/nodes/NodesScreen.kt:138-166`

- [ ] **Step 1: 替换 grouped 段**

修改 `NodesScreen.kt:138-166`，把：

```kotlin
                val grouped = state.items
                    .sortedWith(compareBy<NodeItemUi> { it.serverName }.thenBy { it.nodeName })
                    .groupBy { it.serverName }

                LazyColumn {
                    grouped.forEach { (serverName, nodes) ->
                        item(key = "header:$serverName") {
                            Text(
                                text = serverName.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(items = nodes, key = { "${it.serverId}:${it.nodeId}" }) { item ->
                            NodeRow(
                                item = item,
                                onOpenChat = onOpenChat,
                                onStop = onStop,
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 38.dp),
                            )
                        }
                    }
                }
```

替换为：

```kotlin
                val grouped = groupNodesByServer(state.items, servers)

                LazyColumn {
                    grouped.forEach { (server, nodes) ->
                        item(key = "header:${server.id}") {
                            Text(
                                text = server.name.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(items = nodes, key = { "${it.serverId}:${it.nodeId}" }) { item ->
                            NodeRow(
                                item = item,
                                onOpenChat = onOpenChat,
                                onStop = onStop,
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 38.dp),
                            )
                        }
                    }
                }
```

同时把现在不再使用的 import 删掉（`NodeItemUi` 仍在 `NodeRow` 用）— 确认 `NodeItemUi` 仍被引用即可。

- [ ] **Step 2: 编译并跑全部测试**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && git add app/src/main/java/com/nerve/android/ui/nodes/NodesScreen.kt && git commit -m "fix(nodes): Agents 页分组按 servers 顺序而非字母序"
```

---

## Task 8: ServersScreen ↑↓ 按钮 + onReorder 回调

**Files:**
- Modify: `app/src/main/java/com/nerve/android/ui/server/ServersScreen.kt`

- [ ] **Step 1: 加 onReorder 参数到函数签名**

修改 `ServersScreen` 函数签名，在 `onRemoveServer: (serverId: String) -> Unit,` 之后加：

```kotlin
    onReorder: (orderedIds: List<String>) -> Unit,
```

- [ ] **Step 2: 在每行末尾加 ↑↓ 按钮**

在 `IconButton(onClick = { onRefreshServer(server.id) })` 之前加（即 ↑↓ 在 Refresh 之前）：

```kotlin
                                val index = state.servers.indexOfFirst { it.id == server.id }
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val ids = state.servers.map { it.id }.toMutableList()
                                            val tmp = ids.removeAt(index)
                                            ids.add(index - 1, tmp)
                                            onReorder(ids)
                                        }
                                    },
                                    enabled = index > 0,
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Move ${server.name} up",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (index in 0 until state.servers.lastIndex) {
                                            val ids = state.servers.map { it.id }.toMutableList()
                                            val tmp = ids.removeAt(index)
                                            ids.add(index + 1, tmp)
                                            onReorder(ids)
                                        }
                                    },
                                    enabled = index >= 0 && index < state.servers.lastIndex,
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Move ${server.name} down",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
```

- [ ] **Step 3: 加 import**

在文件头部 import 区加：

```kotlin
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
```

- [ ] **Step 4: 编译验证**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL（AppRoute 调用处会编译错，下一步修）

如果只编译这一个文件错，先继续 Step 5；若有 AppRoute 错，先做 Task 9 再回来。

实际上必须把 AppRoute 也补 callback 编译才过 — 跳到 Task 9 完成 wire-up，再回 Step 5 commit。

- [ ] **Step 5: Commit（在 Task 9 完成后）**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && git add app/src/main/java/com/nerve/android/ui/server/ServersScreen.kt app/src/main/java/com/nerve/android/ui/app/AppRoute.kt && git commit -m "feat(server-ui): Servers 页加 ↑↓ 按钮触发 reorder"
```

---

## Task 9: AppRoute 接 onReorder 回调

**Files:**
- Modify: `app/src/main/java/com/nerve/android/ui/app/AppRoute.kt:142-156`

- [ ] **Step 1: 在 ServersScreen 调用末尾加 onReorder**

在 `onRemoveServer = { serverId -> ... }` 之后加：

```kotlin
                                    onReorder = { orderedIds ->
                                        scope.launch { serverViewModel.reorderServers(orderedIds) }
                                    },
```

- [ ] **Step 2: 编译验证**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 跑全部测试**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 现在回到 Task 8 Step 5 commit（包含两个文件）**

---

## Task 10: ServersScreen 长按拖拽（reorderable 集成）

**Files:**
- Modify: `app/src/main/java/com/nerve/android/ui/server/ServersScreen.kt`

- [ ] **Step 1: 加必要 import**

在 import 区加：

```kotlin
import androidx.compose.foundation.lazy.rememberLazyListState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
```

- [ ] **Step 2: 改 LazyColumn 为可拖拽**

把当前 `LazyColumn` 段（`LazyColumn(contentPadding = ...) { items(items = state.servers, key = { it.id }) { server -> ... } }`）整段替换为：

```kotlin
                val lazyListState = rememberLazyListState()
                val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    val ids = state.servers.map { it.id }.toMutableList()
                    val moved = ids.removeAt(from.index)
                    ids.add(to.index, moved)
                    onReorder(ids)
                }

                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = state.servers,
                        key = { it.id },
                    ) { server ->
                        ReorderableItem(reorderableState, key = server.id) { isDragging ->
                            val connection = state.connections.firstOrNull { it.serverId == server.id }?.state
                                ?: ConnectionState.DISCONNECTED
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = if (isDragging) 8.dp else 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .longPressDraggableHandle(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (connection == ConnectionState.CONNECTED) StatusIdle else StatusError,
                                        modifier = Modifier.size(10.dp),
                                    ) {}
                                    Column(
                                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                                    ) {
                                        Text(
                                            server.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                        )
                                        Text(
                                            server.address,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                    val index = state.servers.indexOfFirst { it.id == server.id }
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val ids = state.servers.map { it.id }.toMutableList()
                                                val tmp = ids.removeAt(index)
                                                ids.add(index - 1, tmp)
                                                onReorder(ids)
                                            }
                                        },
                                        enabled = index > 0,
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move ${server.name} up",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index in 0 until state.servers.lastIndex) {
                                                val ids = state.servers.map { it.id }.toMutableList()
                                                val tmp = ids.removeAt(index)
                                                ids.add(index + 1, tmp)
                                                onReorder(ids)
                                            }
                                        },
                                        enabled = index >= 0 && index < state.servers.lastIndex,
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move ${server.name} down",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRefreshServer(server.id) },
                                        enabled = !state.isRefreshing,
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Refresh ${server.name}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { onRemoveServer(server.id) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove ${server.name}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
```

> 注意：reorderable 库通过 `Modifier.longPressDraggableHandle()` 在该 Surface 上挂长按拖拽手势；`reorderableState` 的 lambda 在拖拽完成时调用 onReorder。

- [ ] **Step 3: 编译并跑测试**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && git add app/src/main/java/com/nerve/android/ui/server/ServersScreen.kt && git commit -m "feat(server-ui): Servers 页支持长按拖拽重排"
```

---

## Task 11: 真机/模拟器人工验收

**Files:** 无代码改动

- [ ] **Step 1: 装到设备**

```bash
cd ~/work/ai-work-os/nerve-app && nerve-server build android && nerve-server install android
```

或 worktree 直接装：

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:installDebug
```

Expected: APK 装到设备

- [ ] **Step 2: 验收清单**

- [ ] Servers 页显示 4 个服务器（默认 mac/mac-test/home + 之前加的 mac 4802）
- [ ] 第一行的 ↑ 是禁用态；最后一行的 ↓ 是禁用态
- [ ] 点 ↑↓ 能换行
- [ ] 长按某行能拖到任意位置
- [ ] 关掉 app 重启，顺序保留
- [ ] 切到 Agents 页，分组顺序与 Servers 一致
- [ ] 在 Agents 页点 + 打开 Spawn 对话框，下拉里的服务器顺序与 Servers 一致

如发现 bug，回到对应 Task 修复并重新跑测试。

- [ ] **Step 3: 把验收结果记到 design.md 末尾**

在 `notes/tasks/server-ordering/design.md` 末尾追加：

```markdown
## 验收结果

- 日期：YYYY-MM-DD
- 设备：<device model>
- 全部清单 ✅ / 失败项及修复 commit
```

- [ ] **Step 4: Commit 验收记录**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && git add notes/tasks/server-ordering/design.md && git commit -m "docs(server-ordering): 真机验收通过"
```

---

## 全量测试 & 双 reviewer 关卡

- [ ] **跑全部单元测试**

```bash
cd ~/work/worktree/ai-work-os/nerve-app && ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL，所有用例通过

- [ ] **拉双 reviewer**（按 `feedback_dual_reviewer.md`）：claude + codex 两个 reviewer 对当前 dev 分支 diff 做 code review。两份 review 都通过才算结束。

- [ ] **可选：合回主仓库 + push**

只在用户明确要求时合回 main 分支并 push（参考 `feedback_worktree_path.md` 流程）。
