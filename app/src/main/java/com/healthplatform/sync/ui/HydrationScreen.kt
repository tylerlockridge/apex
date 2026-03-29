package com.healthplatform.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthplatform.sync.data.db.NutritionSyncState
import com.healthplatform.sync.ui.theme.*
import com.healthplatform.sync.ui.util.rememberApexHaptic
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydrationScreen(
    viewModel: HydrationViewModel = viewModel(),
    onNavigateToTargets: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = rememberApexHaptic()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Hydration", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic.click()
                        onNavigateToTargets()
                    }) {
                        Icon(Icons.Rounded.TrackChanges, "Targets", tint = ApexHydrationAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = ApexOnBackground,
                    navigationIconContentColor = ApexOnBackground,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Progress card
            HydrationProgressCard(state)

            // Quick-add buttons
            Text("QUICK ADD", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickAddButton("250 ml", 250, Modifier.weight(1f)) {
                    haptic.click()
                    viewModel.quickAdd(250)
                }
                QuickAddButton("500 ml", 500, Modifier.weight(1f)) {
                    haptic.click()
                    viewModel.quickAdd(500)
                }
                QuickAddButton("750 ml", 750, Modifier.weight(1f)) {
                    haptic.click()
                    viewModel.quickAdd(750)
                }
            }

            // Custom amount
            var customAmount by remember { mutableStateOf("") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = customAmount,
                    onValueChange = { customAmount = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Custom ml...", color = ApexOnSurfaceVariant) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ApexHydrationAccent,
                        unfocusedBorderColor = ApexOutline,
                        cursorColor = ApexHydrationAccent,
                        focusedTextColor = ApexOnSurface,
                        unfocusedTextColor = ApexOnSurface,
                    ),
                )
                Button(
                    onClick = {
                        val ml = customAmount.toIntOrNull() ?: return@Button
                        if (ml in 1..10000) {
                            haptic.click()
                            viewModel.quickAdd(ml)
                            customAmount = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ApexHydrationAccent),
                    enabled = customAmount.toIntOrNull()?.let { it in 1..10000 } ?: false,
                ) {
                    Text("Add", color = Color.White)
                }
            }

            // Entry list
            if (state.entries.isNotEmpty()) {
                Text("TODAY'S LOG", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
                state.entries.forEach { entry ->
                    WaterEntryRow(entry = entry, onDelete = { viewModel.deleteEntry(entry.id) })
                }
            }
        }
    }
}

@Composable
private fun HydrationProgressCard(state: HydrationState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(ApexHydrationAccent.copy(alpha = 0.9f), ApexHydrationAccent.copy(alpha = 0.3f))
                        )
                    )
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Total
                Text("${state.totalMl} ml", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 36.sp), color = ApexHydrationAccent)
                if (state.target != null) {
                    Text(
                        "of ${state.target.targetMl} ml goal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ApexOnSurfaceVariant,
                    )
                    val progress = (state.totalMl.toFloat() / state.target.targetMl).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ApexHydrationAccent,
                        trackColor = ApexOutline,
                    )
                    val remaining = state.target.targetMl - state.totalMl
                    Text(
                        if (remaining > 0) "${remaining} ml remaining" else "Goal reached!",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (remaining > 0) ApexOnSurfaceVariant else ApexStatusGreen,
                    )
                } else {
                    Text("No target set", style = MaterialTheme.typography.bodySmall, color = ApexOnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun QuickAddButton(label: String, @Suppress("UNUSED_PARAMETER") amountMl: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexHydrationAccent),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
            brush = Brush.horizontalGradient(listOf(ApexHydrationAccent.copy(alpha = 0.5f), ApexHydrationAccent.copy(alpha = 0.2f)))
        ),
    ) {
        Icon(Icons.Rounded.WaterDrop, null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WaterEntryRow(entry: com.healthplatform.sync.data.db.WaterEntryCacheEntity, onDelete: () -> Unit) {
    val haptic = rememberApexHaptic()
    val isPending = entry.syncState != NutritionSyncState.SYNCED

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ApexHydrationAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.WaterDrop, null, tint = ApexHydrationAccent, modifier = Modifier.size(16.dp))
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${entry.amountMl} ml", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = ApexOnSurface)
                        if (isPending) {
                            Icon(Icons.Rounded.CloudQueue, null, tint = ApexOnSurfaceVariant, modifier = Modifier.size(12.dp))
                        }
                    }
                    Text(
                        formatWaterTime(entry.loggedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexOnSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { haptic.click(); onDelete() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, "Delete", tint = ApexOnSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun formatWaterTime(iso: String): String {
    return try {
        val instant = Instant.parse(iso)
        val local = instant.atZone(ZoneId.systemDefault())
        local.format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (_: Exception) {
        iso.take(16)
    }
}
