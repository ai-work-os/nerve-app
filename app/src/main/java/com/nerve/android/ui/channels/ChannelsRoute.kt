package com.nerve.android.ui.channels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nerve.android.presentation.channels.ChannelsViewModel

@Composable
fun ChannelsRoute(
    viewModel: ChannelsViewModel,
    onOpenChannel: (serverId: String, channelId: String, channelName: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    ChannelsScreen(
        channels = state.channels,
        onOpenChannel = onOpenChannel,
    )
}
