package com.nerve.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nerve.android.domain.dm.DmMessage
import com.nerve.android.domain.dm.DmRole

@Composable
fun MessageBubble(message: DmMessage, testTag: String? = null) {
    val arrangement = when (message.role) {
        DmRole.USER -> Arrangement.End
        DmRole.ASSISTANT -> Arrangement.Start
        DmRole.SYSTEM -> Arrangement.Center
    }
    val background = when (message.role) {
        DmRole.USER -> MaterialTheme.colorScheme.primaryContainer
        DmRole.ASSISTANT -> MaterialTheme.colorScheme.surfaceVariant
        DmRole.SYSTEM -> MaterialTheme.colorScheme.tertiaryContainer
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = arrangement,
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .background(background, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(message.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
