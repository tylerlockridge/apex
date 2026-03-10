package com.healthplatform.sync.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthplatform.sync.ui.charts.SparklineChart
import com.healthplatform.sync.ui.theme.*
import com.healthplatform.sync.ui.util.rememberApexHaptic
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = rememberApexHaptic()
    var isRefreshing by remember { mutableStateOf(false) }

    // ViewModel loads on init; reload whenever the screen becomes active after
    // returning from another tab so cached SharedPrefs stay fresh.
    LaunchedEffect(Unit) {
        viewModel.loadFromPrefs()
    }

    // Dismiss PTR as soon as prefs have been reloaded (no hardcoded delay).
    LaunchedEffect(state.isLoadingPrefs) {
        if (!state.isLoadingPrefs) isRefreshing = false
    }

    // Card visibility for staggered animation — rememberSaveable so the entrance
    // animation fires once per app session rather than replaying on every tab switch.
    var card0Visible by rememberSaveable { mutableStateOf(false) }
    var card1Visible by rememberSaveable { mutableStateOf(false) }
    var card2Visible by rememberSaveable { mutableStateOf(false) }
    var card3Visible by rememberSaveable { mutableStateOf(false) }

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
        containerColor = Color.Transparent,
        floatingActionButton = {
            SyncFab(
                isSyncing = state.isSyncing,
                onSync = { haptic.confirm(); viewModel.triggerSync() }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; viewModel.loadFromPrefs() },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardHeader()
                SyncStatusCard(
                    state = state,
                    onSyncNow = { haptic.click(); viewModel.triggerSync() }
                )

                // Readiness card — only shown once we have enough data
                val readinessLabel = state.readinessLabel
                if (readinessLabel != null) {
                    ReadinessCard(
                        label = readinessLabel,
                        reason = state.readinessReason ?: ""
                    )
                }

                SectionLabel(text = "Health Metrics")

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 4.dp, end = 20.dp)
                ) {
                    item {
                        AnimatedVisibility(
                            visible = card0Visible,
                            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 3 }
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
                            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 3 }
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
                            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 3 }
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
                            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 3 }
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
                    onRequestPermissions = { haptic.click(); onRequestPermissions() }
                )
                QuickActionsRow(
                    isSyncing = state.isSyncing,
                    onSyncBp = { haptic.click(); viewModel.triggerBpSync() },
                    onSyncSleep = { haptic.click(); viewModel.triggerSleepSync() }
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
    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else      -> "Good evening"
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = greeting.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = ApexOnSurfaceVariant
            )
            Text(
                text = "Apex",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 38.sp
                ),
                color = ApexOnBackground
            )
        }
        // Date chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(ApexSurfaceVariant)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = remember {
                    java.time.LocalDate.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("MMM d")
                    )
                },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ApexOnSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Section label with left accent mark
// ---------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ApexPrimary)
        )
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = ApexOnSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// B — Readiness Score Card
// ---------------------------------------------------------------------------

@Composable
private fun ReadinessCard(label: String, reason: String) {
    val (icon, color) = when (label) {
        "Good to go"   -> Icons.Rounded.CheckCircle to ApexStatusGreen
        "Take it easy" -> Icons.Rounded.Info        to ApexStatusYellow
        else           -> Icons.Rounded.Warning     to ApexStatusRed
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = color
                )
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexOnSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// C — Sync Status Card
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
                // L-3: compound indicator — color + icon so error state is accessible
                // to colorblind users without relying on color alone.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PulsingDot(color = dotColor)
                    if (state.syncError != null) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "Sync error",
                            tint = ApexStatusRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
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
    // animateFloatAsState settles when isSyncing becomes false — no wasted GPU work
    // at rest, unlike an infiniteRepeatable that loops between 1f and 1f.
    val fabScale by animateFloatAsState(
        targetValue = if (isSyncing) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
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
    accentColor: Color = ApexPrimary,
    sparklinePoints: List<Float> = emptyList(),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.width(168.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurfaceVariant),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = accentColor.copy(alpha = 0.18f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Metric color accent bar at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.9f),
                                accentColor.copy(alpha = 0.3f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .padding(top = 12.dp, bottom = 14.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.8.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = ApexOnSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                content()
                if (sparklinePoints.size >= 2) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SparklineChart(
                        points = sparklinePoints,
                        color = accentColor.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
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
        icon = Icons.Rounded.Favorite,
        accentColor = ApexBpAccent
    ) {
        if (systolic != null && diastolic != null) {
            Text(
                text = "$systolic/$diastolic",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp
                ),
                color = ApexOnSurface
            )
            Text(
                text = "mmHg",
                style = MaterialTheme.typography.labelSmall,
                color = ApexOnSurfaceVariant
            )
            if (time != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatIsoTime(time),
                    style = MaterialTheme.typography.labelSmall,
                    color = ApexOnSurfaceVariant
                )
            }
        } else {
            MetricEmptyState()
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
        icon = Icons.Rounded.Bedtime,
        accentColor = ApexSleepAccent
    ) {
        if (durationMin != null) {
            val score = sleepScore(durationMin, deepMin ?: 0, remMin ?: 0)
            SleepArcGauge(score = score, accentColor = ApexSleepAccent, modifier = Modifier.size(68.dp))
            Spacer(modifier = Modifier.height(6.dp))
            val h = durationMin / 60
            val m = durationMin % 60
            Text(
                text = "${h}h ${m}m",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                ),
                color = ApexOnSurface
            )
            if (time != null) {
                Text(text = formatIsoTime(time), style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            }
        } else {
            MetricEmptyState()
        }
    }
}

/** Arc gauge spanning 240° showing sleep quality score (0–100). */
@Composable
private fun SleepArcGauge(score: Int, accentColor: Color = ApexSleepAccent, modifier: Modifier = Modifier) {
    val arcColor = when {
        score >= 80 -> ApexStatusGreen
        score >= 60 -> ApexStatusYellow
        else        -> ApexStatusRed
    }
    val sweepAngle by animateFloatAsState(
        targetValue = 240f * score / 100f,
        animationSpec = tween(durationMillis = 900),
        label = "sleepArc"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round)
            val inset  = stroke.width / 2
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val startAngle = 150f  // bottom-left
            // Track (background arc)
            drawArc(
                color = ApexOutline,
                startAngle = startAngle,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
            // Progress arc
            drawArc(
                color = arcColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
        }
        Text(
            text = "$score",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = arcColor
        )
    }
}

private fun sleepScore(durationMin: Int, deepMin: Int, remMin: Int): Int {
    val durationScore = when {
        durationMin in 420..540 -> 75   // 7–9 h ideal
        durationMin >= 360      -> 55   // 6–7 h ok
        durationMin >= 300      -> 35   // 5–6 h poor
        else                    -> 20
    }
    val qualityBonus = if (durationMin > 0) {
        val ratio = (deepMin + remMin) * 100 / durationMin
        when {
            ratio >= 40 -> 20
            ratio >= 25 -> 10
            else        -> 0
        }
    } else 0
    return (durationScore + qualityBonus).coerceIn(0, 100)
}

@Composable
private fun WeightCard(
    weightKg: Double?,
    time: String?
) {
    MetricCard(
        title = "Weight",
        icon = Icons.Rounded.Scale,
        accentColor = ApexWeightAccent
    ) {
        if (weightKg != null) {
            Text(
                text = "%.1f".format(weightKg),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp
                ),
                color = ApexOnSurface
            )
            Text(text = "kg", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            if (time != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = formatIsoTime(time), style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            }
        } else {
            MetricEmptyState()
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
        icon = Icons.Rounded.MonitorHeart,
        accentColor = ApexHrvAccent
    ) {
        if (hrvMs != null) {
            Text(
                text = "%.0f".format(hrvMs),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp
                ),
                color = ApexOnSurface
            )
            Text(text = "ms", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            if (time != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = formatIsoTime(time), style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            }
        } else {
            MetricEmptyState()
        }
    }
}

// Empty state for metric cards — consistent across all four metrics
@Composable
private fun MetricEmptyState() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "—",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
            color = ApexOnSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text = "No data",
            style = MaterialTheme.typography.labelSmall,
            color = ApexOnSurfaceVariant.copy(alpha = 0.6f)
        )
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
    isSyncing: Boolean,
    onSyncBp: () -> Unit,
    onSyncSleep: () -> Unit
) {
    SectionLabel(text = "Quick Actions")
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickActionChip(
            label = "Sync BP",
            icon = Icons.Rounded.Favorite,
            accentColor = ApexBpAccent,
            enabled = !isSyncing,
            onClick = onSyncBp,
            modifier = Modifier.weight(1f)
        )
        QuickActionChip(
            label = "Sync Sleep",
            icon = Icons.Rounded.Bedtime,
            accentColor = ApexSleepAccent,
            enabled = !isSyncing,
            onClick = onSyncSleep,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (enabled) accentColor.copy(alpha = 0.12f) else ApexSurfaceVariant.copy(alpha = 0.5f)
    val contentColor   = if (enabled) accentColor else ApexOnSurfaceVariant.copy(alpha = 0.4f)
    val borderColor    = if (enabled) accentColor.copy(alpha = 0.35f) else ApexOutline.copy(alpha = 0.3f)
    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor
            )
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
