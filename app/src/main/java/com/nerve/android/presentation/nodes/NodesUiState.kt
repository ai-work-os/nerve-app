package com.nerve.android.presentation.nodes

data class NodeItemUi(
    val serverId: String,
    val serverName: String,
    val nodeId: String,
    val nodeName: String,
    val status: String,
    val adapter: String? = null,
    val cwd: String? = null,
) {
    val shortId: String get() = nodeId.take(8)
    val cwdLabel: String? get() = cwd?.substringAfterLast('/') ?: adapter
}

data class NodesUiState(
    val items: List<NodeItemUi> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val connectedCount: Int = 0,
    val totalCount: Int = 0,
)
