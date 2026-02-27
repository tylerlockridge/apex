package com.healthplatform.sync.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthplatform.sync.ui.theme.*
import com.healthplatform.sync.ui.util.rememberApexHaptic
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = rememberApexHaptic()
    var isRefreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refresh()
            isRefreshing = false
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 24.dp)
        ) {
        Text(
            text = "Activity",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = ApexOnBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.workouts.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = ApexPrimary
                    )
                }
                state.error != null && state.workouts.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ApexStatusRed
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { haptic.reject(); viewModel.refresh() },
                            border = androidx.compose.foundation.BorderStroke(1.dp, ApexPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexPrimary)
                        ) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Weekly summary card
                        state.stats?.let { stats ->
                            WeeklySummaryCard(stats)
                        }

                        // Workout list
                        if (state.workouts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("—", style = MaterialTheme.typography.headlineLarge, color = ApexOnSurfaceVariant)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No workouts yet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ApexOnSurfaceVariant
                                    )
                                    Text(
                                        text = "Sync Hevy workouts to see them here",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ApexOnSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Recent Workouts",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = ApexPrimary
                            )
                            state.workouts.forEach { workout ->
                                WorkoutCard(workout)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        } // Column
    } // PullToRefreshBox
}

// ---------------------------------------------------------------------------
// Weekly summary card
// ---------------------------------------------------------------------------

@Composable
private fun WeeklySummaryCard(stats: WorkoutStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "30-Day Summary",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ApexPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryStatItem(
                    icon = Icons.Rounded.FitnessCenter,
                    value = "${stats.totalWorkouts}",
                    label = "Workouts"
                )
                SummaryStatItem(
                    icon = Icons.Rounded.Timer,
                    value = "${stats.avgDurationMin}m",
                    label = "Avg Duration"
                )
                SummaryStatItem(
                    icon = Icons.Rounded.MonitorWeight,
                    value = "%.0f kg".format(stats.totalVolumeKg),
                    label = "Total Volume"
                )
                SummaryStatItem(
                    icon = Icons.Rounded.Repeat,
                    value = "${stats.avgSetsPerWorkout}",
                    label = "Avg Sets"
                )
            }
        }
    }
}

@Composable
private fun SummaryStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = ApexPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = ApexOnSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ApexOnSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// Workout card (expandable)
// ---------------------------------------------------------------------------

@Composable
private fun WorkoutCard(workout: WorkoutSession) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = rememberApexHaptic()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { haptic.click(); expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Teal left accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(ApexPrimary)
            )
            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = workout.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ApexOnSurface
                        )
                        Text(
                            text = formatWorkoutDate(workout.startedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = ApexOnSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = ApexOnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick stats row
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (workout.durationMinutes != null) {
                        WorkoutChip(Icons.Rounded.Timer, "${workout.durationMinutes}m")
                    }
                    if (workout.totalSets != null) {
                        WorkoutChip(Icons.Rounded.Repeat, "${workout.totalSets} sets")
                    }
                    if (workout.totalVolumeKg != null) {
                        WorkoutChip(Icons.Rounded.MonitorWeight, "%.0f kg".format(workout.totalVolumeKg))
                    }
                }

                // Expanded detail area
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = ApexOutline)
                        Spacer(modifier = Modifier.height(8.dp))
                        // Extended info (reps if available)
                        if (workout.totalReps != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total Reps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ApexOnSurfaceVariant
                                )
                                Text(
                                    text = "${workout.totalReps}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ApexOnSurface
                                )
                            }
                        }
                        if (workout.totalVolumeKg != null && workout.totalSets != null && workout.totalSets > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Avg Volume / Set",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ApexOnSurfaceVariant
                                )
                                Text(
                                    text = "%.1f kg".format(workout.totalVolumeKg / workout.totalSets),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ApexOnSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ApexPrimary,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ApexOnSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun formatWorkoutDate(iso: String): String {
    return try {
        val instant = Instant.parse(iso)
        val formatter = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a").withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) { iso.take(16) }
}
