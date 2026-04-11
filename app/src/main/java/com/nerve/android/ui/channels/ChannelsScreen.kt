package com.nerve.android.ui.channels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nerve.android.domain.server.ServerChannel

@Composable
fun ChannelsScreen(
    channels: List<ServerChannel>,
    onOpenChannel: (serverId: String, channelId: String, channelName: String) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        if (channels.isEmpty()) {
            Text("No channels", style = MaterialTheme.typography.bodyLarge)
        } else {
            val grouped = channels.groupBy { it.serverName }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                grouped.forEach { (serverName, serverChannels) ->
                    item(key = "header:$serverName") {
                        Text(
                            text = serverName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Text(
                text = "#",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(8.dp),
            )
        }
        Column {
            Text(
                text = channel.channel.name.ifBlank { channel.channel.id.take(8) },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = channel.serverName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
