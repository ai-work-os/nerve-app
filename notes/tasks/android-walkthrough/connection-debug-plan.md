# Connection Debug Plan

## 问题

nerve-app 装真机后：Servers 页没绿点，Agents 页空白。网络通但连不上。

## 已确认原因

### P0: Logger 用 println()，Android 上无 logcat 输出

`util/Logger.kt` 用 `println()` 而非 `android.util.Log`。Android 上 println 输出到 System.out，不走 logcat，导致完全无法观测。

**影响范围**：26+ 个文件使用 Logger，所有日志全部丢失。

**修复方案**：Logger 改为可插拔后端。
- 接口 `LogBackend`，两个实现：`AndroidLogBackend`（android.util.Log）、`PrintlnLogBackend`（println，JVM 测试用）
- Logger 初始化时自动检测环境：有 android.util.Log 类就用 Android 后端，否则 println
- 不改调用方，26+ 个文件零修改

### P1: 连接问题（待 Logger 修复后排查）

没日志无法确认根因。可能的方向：
1. WebSocket URL 构造错误
2. OkHttpClient 缺少超时配置（旧版有 10s connect/write, 15s ping）
3. node.register 请求超时或被拒绝
4. 网络权限缺失

## 执行步骤

1. **TDD 修 Logger**：写测试 → 红 → 改代码 → 绿
2. **构建装机**：`./gradlew assembleDebug && adb -s fc5a1b30 install`
3. **拉 logcat**：`adb -s fc5a1b30 logcat -s NerveClient ServerRegistry`
4. **根据日志排查 P1**
