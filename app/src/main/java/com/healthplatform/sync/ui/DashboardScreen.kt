package com.healthplatform.sync.ui

import com.healthplatform.sync.readiness.ReadinessInputResult
import com.healthplatform.sync.readiness.ReadinessInputStatus

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
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

    LaunchedEffect(state.isLoadingPrefs) {
        if (!state.isLoadingPrefs) isRefreshing = false
    }

    // Staggered entrance — fires once per app session
    var heroVisible by rememberSaveable { mutableStateOf(false) }
    var gridVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        heroVisible = true
        kotlinx.coroutines.delay(120)
        gridVisible = true
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
                HomeHeader()

                // Hero: Readiness + Sync status combined
                AnimatedVisibility(
                    visible = heroVisible,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                        slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 4 }
                ) {
                    HeroCard(
                        state = state,
                        onSyncNow = { haptic.click(); viewModel.triggerSync() }
                    )
                }

                // Permissions banner — only when HC is available but permissions missing
                if (!state.hasAllPermissions && state.isHealthConnectAvailable) {
                    PermissionsBanner(
                        onRequestPermissions = { haptic.click(); onRequestPermissions() }
                    )
                } else if (!state.isHealthConnectAvailable) {
                    HcUnavailableBanner()
                }

                // Health Metrics — 2×2 visible grid (replaces horizontal scroll)
                SectionLabel(text = "Health Metrics")

                AnimatedVisibility(
                    visible = gridVisible,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                        slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 4 }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BloodPressureTile(
                                systolic = state.lastBpSystolic,
                                diastolic = state.lastBpDiastolic,
                                time = state.lastBpTime,
                                modifier = Modifier.weight(1f)
                            )
                            SleepTile(
                                durationMin = state.lastSleepDurationMin,
                                deepMin = state.lastSleepDeepMin,
                                remMin = state.lastSleepRemMin,
                                time = state.lastSleepTime,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            WeightTile(
                                weightKg = state.lastWeightKg,
                                time = state.lastWeightTime,
                                modifier = Modifier.weight(1f)
                            )
                            HrvTile(
                                hrvMs = state.lastHrvMs,
                                time = state.lastHrvTime,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Recent workout snippet
                val workoutTitle = state.lastWorkoutTitle
                if (workoutTitle != null) {
                    RecentWorkoutSnippet(
                        title = workoutTitle,
                        date = state.lastWorkoutDate
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun HomeHeader() {
    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when {
        currentHour < 12 -> "Good morning"
        currentHour < 17 -> "Good afternoon"
        else             -> "Good evening"
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
// Hero Card — Readiness + Sync combined
// ---------------------------------------------------------------------------

@Composable
private fun HeroCard(
    state: DashboardState,
    onSyncNow: () -> Unit
) {
    val readinessResult = state.readinessResult
    val readinessLabel = state.readinessLabel

    val nowMs = System.currentTimeMillis()
    val ageMs = if (state.lastSyncMs > 0) nowMs - state.lastSyncMs else Long.MAX_VALUE
    val ageMinutes = TimeUnit.MILLISECONDS.toMinutes(ageMs)
    val ageHours = TimeUnit.MILLISECONDS.toHours(ageMs)

    val syncDotColor = when {
        state.lastSyncMs == 0L -> ApexStatusRed
        ageHours < 1 -> ApexStatusGreen
        ageHours < 24 -> ApexStatusYellow
        else -> ApexStatusRed
    }

    val syncLabel = when {
        state.lastSyncMs == 0L -> "Never synced"
        ageMinutes < 1 -> "Just now"
        ageMinutes < 60 -> "${ageMinutes}m ago"
        else -> "${ageHours}h ago"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Gradient accent bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                ApexPrimary.copy(alpha = 0.85f),
                                ApexPrimary.copy(alpha = 0.15f)
                            )
                        )
                    )
            )

            Column(modifier = Modifier.padding(20.dp)) {
                // Readiness section
                if (readinessLabel != null && readinessResult != null && readinessResult.aggregateScore != null) {
                    val readinessColor = when (readinessLabel) {
                        "Good to go"   -> ApexStatusGreen
                        "Take it easy" -> ApexStatusYellow
                        else           -> ApexStatusRed
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TODAY'S READINESS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = ApexOnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = readinessLabel,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = readinessColor
                            )
                            if (!state.readinessReason.isNullOrBlank()) {
                                Text(
                                    text = state.readinessReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ApexOnSurfaceVariant
                                )
                            }
                        }

                        // Large readiness score
                        ReadinessGauge(
                            score = readinessResult.aggregateScore,
                            color = readinessColor,
                            modifier = Modifier.size(72.dp)
                        )
                    }

                    // Per-input breakdown (compact)
                    val visibleInputs = readinessResult.inputs.filter {
                        it.status != ReadinessInputStatus.EXCLUDED
                    }
                    if (visibleInputs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            visibleInputs.forEach { input ->
                                val statusColor = when (input.status) {
                                    ReadinessInputStatus.FRESH    -> ApexStatusGreen
                                    ReadinessInputStatus.DEGRADED -> ApexStatusYellow
                                    ReadinessInputStatus.MISSING  -> ApexOnSurfaceVariant
                                    ReadinessInputStatus.EXCLUDED -> ApexOnSurfaceVariant
                                }
                                val inputLabel = when (input.id) {
                                    com.healthplatform.sync.readiness.ReadinessInputId.SLEEP -> "Sleep"
                                    com.healthplatform.sync.readiness.ReadinessInputId.BLOOD_PRESSURE -> "BP"
                                    com.healthplatform.sync.readiness.ReadinessInputId.HRV -> "HRV"
                                    com.healthplatform.sync.readiness.ReadinessInputId.SUBJECTIVE -> "Feel"
                                    com.healthplatform.sync.readiness.ReadinessInputId.TRAINING_LOAD -> "Load"
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Canvas(modifier = Modifier.size(6.dp)) {
                                        drawCircle(color = statusColor)
                                    }
                                    Text(
                                        text = inputLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ApexOnSurfaceVariant
                                    )
                                    if (input.score != null) {
                                        Text(
                                            text = "${input.score}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = statusColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (readinessResult != null && readinessResult.aggregateScore == null) {
                    // Partial readiness data
                    Text(
                        text = "READINESS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = ApexOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = readinessResult.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ApexOnSurfaceVariant
                    )
                } else {
                    // No readiness data at all
                    Text(
                        text = "READINESS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = ApexOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sync health data to see your readiness score",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ApexOnSurfaceVariant
                    )
                }

                // Divider between readiness and sync
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = ApexOutline
                )

                // Sync status line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PulsingDot(color = syncDotColor)
                            if (state.syncError != null) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = "Sync error",
                                    tint = ApexStatusRed,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = syncLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ApexOnSurface
                        )
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = ApexPrimary
                            )
                        }
                    }

                    TextButton(
                        onClick = onSyncNow,
                        enabled = !state.isSyncing,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = ApexPrimary,
                            disabledContentColor = ApexOnSurfaceVariant
                        )
                    ) {
                        Text(
                            text = if (state.isSyncing) "Syncing..." else "Sync Now",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}

/** 240° arc gauge showing readiness score — reused for sleep too. */
@Composable
private fun ReadinessGauge(score: Int, color: Color, modifier: Modifier = Modifier) {
    val sweepAngle by animateFloatAsState(
        targetValue = 240f * score / 100f,
        animationSpec = tween(durationMillis = 900),
        label = "readinessArc"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = size.minDimension * 0.10f, cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(color = ApexOutline, startAngle = 150f, sweepAngle = 240f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
            drawArc(color = color, startAngle = 150f, sweepAngle = sweepAngle, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
        }
        Text(
            text = "$score",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = color
        )
    }
}

// ---------------------------------------------------------------------------
// Permissions banners
// ---------------------------------------------------------------------------

@Composable
private fun PermissionsBanner(onRequestPermissions: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ApexStatusYellow.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, ApexStatusYellow.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = ApexStatusYellow,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Health Connect permissions needed",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ApexOnSurface
                )
                Text(
                    text = "Grant access to read your health data",
                    style = MaterialTheme.typography.labelSmall,
                    color = ApexOnSurfaceVariant
                )
            }
            TextButton(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.textButtonColors(contentColor = ApexStatusYellow)
            ) {
                Text("Grant", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HcUnavailableBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ApexStatusRed.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, ApexStatusRed.copy(alpha = 0.20f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = ApexStatusRed,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Health Connect not available on this device",
                style = MaterialTheme.typography.bodySmall,
                color = ApexOnSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Metric Tiles (2×2 grid)
// ---------------------------------------------------------------------------

@Composable
private fun MetricTile(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Accent bar
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
            }
        }
    }
}

@Composable
private fun BloodPressureTile(
    systolic: Int?,
    diastolic: Int?,
    time: String?,
    modifier: Modifier = Modifier
) {
    MetricTile(
        title = "Blood Pressure",
        icon = Icons.Rounded.Favorite,
        accentColor = ApexBpAccent,
        modifier = modifier
    ) {
        if (systolic != null && diastolic != null) {
            Text(
                text = "$systolic/$diastolic",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                ),
                color = ApexOnSurface
            )
            Text(text = "mmHg", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            if (time != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = formatIsoTime(time), style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            }
        } else {
            MetricEmptyState()
        }
    }
}

@Composable
private fun SleepTile(
    durationMin: Int?,
    deepMin: Int?,
    remMin: Int?,
    time: String?,
    modifier: Modifier = Modifier
) {
    MetricTile(
        title = "Sleep",
        icon = Icons.Rounded.Bedtime,
        accentColor = ApexSleepAccent,
        modifier = modifier
    ) {
        if (durationMin != null) {
            val score = sleepScore(durationMin, deepMin ?: 0, remMin ?: 0)
            SleepArcGauge(score = score, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(4.dp))
            val h = durationMin / 60
            val m = durationMin % 60
            Text(
                text = "${h}h ${m}m",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
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

@Composable
private fun WeightTile(
    weightKg: Double?,
    time: String?,
    modifier: Modifier = Modifier
) {
    MetricTile(
        title = "Weight",
        icon = Icons.Rounded.Scale,
        accentColor = ApexWeightAccent,
        modifier = modifier
    ) {
        if (weightKg != null) {
            Text(
                text = "%.1f".format(weightKg),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                ),
                color = ApexOnSurface
            )
            Text(text = "kg", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            if (time != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = formatIsoTime(time), style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            }
        } else {
            MetricEmptyState()
        }
    }
}

@Composable
private fun HrvTile(
    hrvMs: Double?,
    time: String?,
    modifier: Modifier = Modifier
) {
    MetricTile(
        title = "HRV",
        icon = Icons.Rounded.MonitorHeart,
        accentColor = ApexHrvAccent,
        modifier = modifier
    ) {
        if (hrvMs != null) {
            Text(
                text = "%.0f".format(hrvMs),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                ),
                color = ApexOnSurface
            )
            Text(text = "ms", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            if (time != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = formatIsoTime(time), style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            }
        } else {
            MetricEmptyState()
        }
    }
}

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
// Sleep Arc Gauge
// ---------------------------------------------------------------------------

@Composable
private fun SleepArcGauge(score: Int, modifier: Modifier = Modifier) {
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
            val inset = stroke.width / 2
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(color = ApexOutline, startAngle = 150f, sweepAngle = 240f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
            drawArc(color = arcColor, startAngle = 150f, sweepAngle = sweepAngle, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
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
        durationMin in 420..540 -> 75
        durationMin >= 360      -> 55
        durationMin >= 300      -> 35
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

// ---------------------------------------------------------------------------
// Recent Workout Snippet
// ---------------------------------------------------------------------------

@Composable
private fun RecentWorkoutSnippet(title: String, date: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ApexPrimary.copy(alpha = 0.15f))
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
                    text = "LAST WORKOUT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.Medium
                    ),
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
// Shared components
// ---------------------------------------------------------------------------

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

@Composable
private fun SyncFab(isSyncing: Boolean, onSync: () -> Unit) {
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
