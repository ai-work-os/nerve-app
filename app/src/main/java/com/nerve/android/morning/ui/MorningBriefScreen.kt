package com.nerve.android.morning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nerve.android.morning.MorningBriefPresentation
import com.nerve.android.morning.MorningBriefSection
import com.nerve.android.ui.theme.AmberPrimary
import com.nerve.android.ui.theme.StoneMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorningBriefScreen(vm: MorningBriefViewModel) {
    LaunchedEffect(Unit) {
        if (vm.brief == null) vm.refresh()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Brief", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val brief = vm.brief
            val presentation = MorningBriefPresentation.from(brief = brief, loading = vm.loading, error = vm.error)
            if (brief == null) {
                Spacer(Modifier.height(48.dp))
                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
                presentation.statusMessage?.let {
                    StatusText(it)
                }
                Button(onClick = { vm.refresh() }) {
                    Text("Refresh")
                }
            } else {
                Text(
                    text = presentation.sourceLabel.ifBlank { brief.date },
                    style = MaterialTheme.typography.labelLarge,
                    color = StoneMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
                if (presentation.summary.isNotBlank()) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Text(
                            text = presentation.summary,
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                presentation.statusMessage?.let {
                    StatusText(it)
                }
                presentation.sections.forEach { section ->
                    BriefSectionCard(section)
                }
            }
            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        color = StoneMuted,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun BriefSectionCard(section: MorningBriefSection) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            section.items.forEach { item ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("•", color = AmberPrimary, fontWeight = FontWeight.Black)
                    Text(
                        text = item,
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
