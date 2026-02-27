package com.healthplatform.sync.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthplatform.sync.ui.charts.SparklineChart
import com.healthplatform.sync.ui.theme.*
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onRequestPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isRefreshing by remember { mutableStateOf(false) }

    // Load prefs each time the screen becomes active
    LaunchedEffect(Unit) {
        viewModel.loadFromPrefs(context)
    }

    // Handle pull-to-refresh
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.loadFromPrefs(context)
            kotlinx.coroutines.delay(800)
            isRefreshing = false
        }
    }

    // Card visibility for staggered animation
    var card0Visible by remember { mutableStateOf(false) }
    var card1Visible by remember { mutableStateOf(false) }
    var card2Visible by remember { mutableStateOf(false) }
    var card3Visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        card0Visible = true
        kotlinx.coroutines.delay(100)
        card1Visible = true
        kotlinx.coroutines.delay(100)
        card2Visible = true
        kotlinx.coroutines.delay(100)
        card3Visible = true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ApexBackground,
        floatingActionButton = {
            SyncFab(
                isSyncing = state.isSyncing,
                onSync = { viewModel.triggerSync(context) }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ApexBackground)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardHeader()
                SyncStatusCard(
                    state = state,
                    onSyncNow = { viewModel.triggerSync(context) }
                )

                Text(
                    text = "Health Metrics",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ApexPrimary
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    item {
                        AnimatedVisibility(
                            visible = card0Visible,
                            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 }
                        ) {
                            BloodPressureCard(
                                systolic = state.lastBpSystolic,
                                diastolic = state.lastBpDiastolic,
                                time = state.lastBpTime
                            )
                        }
                    }
                    item {
                        AnimatedVisibility(
                            visible = card1Visible,
                            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 }
                        ) {
                            SleepCard(
                                durationMin = state.lastSleepDurationMin,
                                deepMin = state.lastSleepDeepMin,
                                remMin = state.lastSleepRemMin,
                                time = state.lastSleepTime
                            )
                        }
                    }
                    item {
                        AnimatedVisibility(
                            visible = card2Visible,
                            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 }
                        ) {
                            WeightCard(
                                weightKg = state.lastWeightKg,
                                time = state.lastWeightTime
                            )
                        }
                    }
                    item {
                        AnimatedVisibility(
                            visible = card3Visible,
                            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 }
                        ) {
                            HrvCard(
                                hrvMs = state.lastHrvMs,
                                time = state.lastHrvTime
                            )
                        }
                    }
                }

                // Recent workout snippet — capture to local val for smart cast
                val workoutTitle = state.lastWorkoutTitle
                if (workoutTitle != null) {
                    RecentWorkoutSnippet(
                        title = workoutTitle,
                        date = state.lastWorkoutDate
                    )
                }

                HealthConnectStatusCard(
                    state = state,
                    onRequestPermissions = onRequestPermissions
                )
                QuickActionsRow(
                    onSyncBp = { viewModel.triggerBpSync(context) },
                    onSyncSleep = { viewModel.triggerSleepSync(context) }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// A — Header
// ---------------------------------------------------------------------------

@Composable
private fun DashboardHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Apex",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 38.sp
            ),
            color = ApexOnBackground
        )
        Text(
            text = "Peak health, always.",
            style = MaterialTheme.typography.bodyLarge,
            color = ApexPrimary
        )
    }
}

// ---------------------------------------------------------------------------
// B — Sync Status Card
// ---------------------------------------------------------------------------

@Composable
private fun SyncStatusCard(
    state: DashboardState,
    onSyncNow: () -> Unit
) {
    val nowMs = System.currentTimeMillis()
    val ageMs = if (state.lastSyncMs > 0) nowMs - state.lastSyncMs else Long.MAX_VALUE
    val ageMinutes = TimeUnit.MILLISECONDS.toMinutes(ageMs)
    val ageHours = TimeUnit.MILLISECONDS.toHours(ageMs)

    val dotColor = when {
        state.lastSyncMs == 0L -> ApexStatusRed
        ageHours < 1 -> ApexStatusGreen
        ageHours < 24 -> ApexStatusYellow
        else -> ApexStatusRed
    }

    val syncLabel = when {
        state.lastSyncMs == 0L -> "Never synced"
        ageMinutes < 1 -> "Just now"
        ageMinutes < 60 -> "Last synced: ${ageMinutes}m ago"
        else -> "Last synced: ${ageHours}h ago"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PulsingDot(color = dotColor)
                Column {
                    Text(
                        text = syncLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ApexOnSurface
                    )
                    if (state.isSyncing) {
                        Text(
                            text = "Sync in progress...",
                            style = MaterialTheme.typography.labelSmall,
                            color = ApexPrimary
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onSyncNow,
                enabled = !state.isSyncing,
                border = androidx.compose.foundation.BorderStroke(1.dp, ApexPrimary),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ApexPrimary,
                    disabledContentColor = ApexOnSurfaceVariant
                )
            ) {
                if (state.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ApexPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Syncing")
                } else {
                    Text("Sync Now")
                }
            }
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

// ---------------------------------------------------------------------------
// C — Sync FAB
// ---------------------------------------------------------------------------

@Composable
private fun SyncFab(isSyncing: Boolean, onSync: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val fabScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSyncing) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_scale"
    )

    FloatingActionButton(
        onClick = { if (!isSyncing) onSync() },
        modifier = Modifier.scale(fabScale),
        containerColor = ApexPrimary,
        contentColor = ApexOnPrimary
    ) {
        if (isSyncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = ApexOnPrimary
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Sync,
                contentDescription = "Sync now"
            )
        }
    }
}

// ---------------------------------------------------------------------------
// D — Metric Cards (horizontal scroll)
// ---------------------------------------------------------------------------

@Composable
private fun MetricCard(
    title: String,
    icon: ImageVector,
    sparklinePoints: List<Float> = emptyList(),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Teal left border
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(ApexPrimary)
            )
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = ApexPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = ApexOnSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                content()
                if (sparklinePoints.size >= 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SparklineChart(
                        points = sparklinePoints,
                        color = ApexPrimary.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BloodPressureCard(
    systolic: Int?,
    diastolic: Int?,
    time: String?
) {
    MetricCard(
        title = "Blood Pressure",
        icon = Icons.Rounded.Favorite
    ) {
        if (systolic != null && diastolic != null) {
            Text(
                text = "$systolic/$diastolic",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = ApexOnSurface
            )
            Text(
                text = "mmHg",
                style = MaterialTheme.typography.labelSmall,
                color = ApexOnSurfaceVariant
            )
            if (time != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatIsoTime(time),
                    style = MaterialTheme.typography.labelSmall,
                    color = ApexOnSurfaceVariant
                )
            }
        } else {
            Text(text = "—", style = MaterialTheme.typography.headlineSmall, color = ApexOnSurfaceVariant)
            Text(text = "No data", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
        }
    }
}

@Composable
private fun SleepCard(
    durationMin: Int?,
    deepMin: Int?,
    remMin: Int?,
    time: String?
) {
    MetricCard(
        title = "Sleep",
        icon = Icons.Rounded.Bedtime
    ) {
        if (durationMin != null) {
            val h = durationMin / 60
            val m = durationMin % 60
            Text(
                text = "${h}h ${m}m",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = ApexOnSurface
            )
            if (deepMin != null && remMin != null && durationMin > 0) {
                val qualityPct = ((deepMin + remMin) * 100 / durationMin).coerceIn(0, 100)
                val badge = when {
                    qualityPct >= 40 -> "High quality"
                    qualityPct >= 20 -> "Moderate"
                    else -> "Low quality"
                }
                Text(text = badge, style = MaterialTheme.typography.labelSmall, color = ApexPrimary)
            }
            if (time != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = formatIsoTime(time), style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            }
        } else {
            Text(text = "—", style = MaterialTheme.typography.headlineSmall, color = ApexOnSurfaceVariant)
            Text(text = "No data", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
        }
    }
}

@Composable
private fun WeightCard(
    weightKg: Double?,
    time: String?
) {
    MetricCard(
        title = "Weight",
        icon = Icons.Rounded.Scale
    ) {
        if (weightKg != null) {
            Text(
                text = "%.1f".format(weightKg),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = ApexOnSurface
            )
            Text(text = "kg", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            if (time != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = formatIsoTime(time), style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            }
        } else {
            Text(text = "—", style = MaterialTheme.typography.headlineSmall, color = ApexOnSurfaceVariant)
            Text(text = "No data", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
        }
    }
}

@Composable
private fun HrvCard(
    hrvMs: Double?,
    time: String?
) {
    MetricCard(
        title = "HRV",
        icon = Icons.Rounded.MonitorHeart
    ) {
        if (hrvMs != null) {
            Text(
                text = "%.0f".format(hrvMs),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = ApexOnSurface
            )
            Text(text = "ms", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            if (time != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = formatIsoTime(time), style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            }
        } else {
            Text(text = "—", style = MaterialTheme.typography.headlineSmall, color = ApexOnSurfaceVariant)
            Text(text = "No data", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// E — Recent Workout Snippet
// ---------------------------------------------------------------------------

@Composable
private fun RecentWorkoutSnippet(title: String, date: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.FitnessCenter,
                contentDescription = null,
                tint = ApexPrimary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Last Workout",
                    style = MaterialTheme.typography.labelSmall,
                    color = ApexOnSurfaceVariant
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ApexOnSurface
                )
                if (date != null) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelSmall,
                        color = ApexOnSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// F — Health Connect Status Card
// ---------------------------------------------------------------------------

@Composable
private fun HealthConnectStatusCard(
    state: DashboardState,
    onRequestPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Health Connect",
                style = MaterialTheme.typography.titleMedium,
                color = ApexOnSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            when {
                !state.isHealthConnectAvailable -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.ErrorOutline, contentDescription = null, tint = ApexStatusRed, modifier = Modifier.size(20.dp))
                        Text(text = "Health Connect not available on this device", style = MaterialTheme.typography.bodyMedium, color = ApexOnSurfaceVariant)
                    }
                }
                state.hasAllPermissions -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = null, tint = ApexStatusGreen, modifier = Modifier.size(20.dp))
                        Text(text = "All permissions granted", style = MaterialTheme.typography.bodyMedium, color = ApexOnSurface)
                    }
                }
                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Warning, contentDescription = null, tint = ApexStatusYellow, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Permissions required to read health data",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ApexOnSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onRequestPermissions,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ApexPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexPrimary)
                    ) {
                        Text("Grant Permissions")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// G — Quick Actions Row
// ---------------------------------------------------------------------------

@Composable
private fun QuickActionsRow(
    onSyncBp: () -> Unit,
    onSyncSleep: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onSyncBp,
            modifier = Modifier.weight(1f),
            border = androidx.compose.foundation.BorderStroke(1.dp, ApexPrimary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexPrimary)
        ) {
            Icon(imageVector = Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Sync BP")
        }
        OutlinedButton(
            onClick = onSyncSleep,
            modifier = Modifier.weight(1f),
            border = androidx.compose.foundation.BorderStroke(1.dp, ApexPrimary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexPrimary)
        ) {
            Icon(imageVector = Icons.Rounded.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Sync Sleep")
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun formatIsoTime(iso: String): String {
    return try {
        val instant = java.time.Instant.parse(iso)
        val local = java.time.ZoneId.systemDefault().let { instant.atZone(it) }
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")
        local.format(formatter)
    } catch (e: Exception) {
        iso.take(16)
    }
}
