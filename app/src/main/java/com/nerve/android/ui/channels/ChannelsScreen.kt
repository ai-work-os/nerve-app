package com.nerve.android.ui.channels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nerve.android.domain.server.ServerChannel
import com.nerve.android.ui.theme.AmberPrimary

@Composable
fun ChannelsScreen(
    channels: List<ServerChannel>,
    onOpenChannel: (serverId: String, channelId: String, channelName: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text(
            text = "Comms",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        if (channels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No channels discovered", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val grouped = channels.groupBy { it.serverName }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                grouped.forEach { (serverName, serverChannels) ->
                    item(key = "header:$serverName") {
                        Text(
                            text = serverName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(
                        items = serverChannels,
                        key = { "${it.serverId}:${it.channel.id}" },
                    ) { ch ->
                        ChannelRow(
                            channel = ch,
                            onClick = { onOpenChannel(ch.serverId, ch.channel.id, ch.channel.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: ServerChannel,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "#",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = AmberPrimary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.channel.name.ifBlank { channel.channel.id.take(8) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = channel.serverName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}
