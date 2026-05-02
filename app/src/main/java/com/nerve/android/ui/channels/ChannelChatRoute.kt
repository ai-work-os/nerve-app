package com.nerve.android.ui.channels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.nerve.android.presentation.channels.ChannelsViewModel
import com.nerve.android.util.Logger
import kotlinx.coroutines.launch

@Composable
fun ChannelChatRoute(
    viewModel: ChannelsViewModel,
    serverId: String,
    channelId: String,
    channelName: String,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(serverId, channelId) {
        Logger.debug(
            "ChannelChatRoute",
            "route_enter",
            mapOf("serverId" to serverId, "channelId" to channelId, "route" to "channel"),
        )
        viewModel.enterChannel(serverId, channelId, channelName)
        viewModel.joinChannel(serverId, channelId)
    }

    ChannelChatScreen(
        channelName = state.currentChannelName ?: channelName,
        serverName = state.channels.firstOrNull { it.serverId == serverId }?.serverName ?: serverId,
        messages = state.messages,
        isPosting = state.isPosting,
        errorMessage = state.errorMessage,
        onSend = { text ->
            Logger.debug(
                "ChannelChatRoute",
                "channel_post_begin",
                mapOf("serverId" to serverId, "channelId" to channelId, "len" to text.length),
            )
            scope.launch { viewModel.sendMessage(text) }
        },
        onBack = onBack,
    )
}
