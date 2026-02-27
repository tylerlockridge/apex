package com.healthplatform.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthplatform.sync.ui.charts.LineChart
import com.healthplatform.sync.ui.charts.SleepBar
import com.healthplatform.sync.ui.charts.StackedBarChart
import com.healthplatform.sync.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

@Composable
fun TrendsScreen(
    viewModel: TrendsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ApexBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        Text(
            text = "Trends",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = ApexOnBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Sub-tab row: BP | Sleep | Body
        val tabLabels = listOf("BP", "Sleep", "Body")
        ScrollableTabRow(
            selectedTabIndex = state.selectedTab,
            containerColor = ApexSurface,
            contentColor = ApexPrimary,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[state.selectedTab]),
                    color = ApexPrimary
                )
            }
        ) {
            tabLabels.forEachIndexed { index, label ->
                Tab(
                    selected = state.selectedTab == index,
                    onClick = { viewModel.selectTab(index) },
                    text = {
                        Text(
                            text = label,
                            color = if (state.selectedTab == index) ApexPrimary else ApexOnSurfaceVariant
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content area scrolls
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = ApexPrimary
                    )
                }
                state.error != null -> {
                    ErrorCard(
                        message = state.error!!,
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (state.selectedTab) {
                            0 -> BpTabContent(state, viewModel)
                            1 -> SleepTabContent(state, viewModel)
                            2 -> BodyTabContent(state, viewModel)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Range selector
// ---------------------------------------------------------------------------

@Composable
private fun RangeSelector(
    selectedDays: Int,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7, 30, 90).forEach { days ->
            val selected = selectedDays == days
            FilterChip(
                selected = selected,
                onClick = { onSelect(days) },
                label = { Text(if (days == 90) "90d" else "${days}d") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ApexPrimary.copy(alpha = 0.2f),
                    selectedLabelColor = ApexPrimary,
                    containerColor = ApexSurface,
                    labelColor = ApexOnSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    selectedBorderColor = ApexPrimary,
                    borderColor = ApexOutline
                )
            )
        }
    }
}

// ---------------------------------------------------------------------------
// BP Tab
// ---------------------------------------------------------------------------

@Composable
private fun BpTabContent(state: TrendsState, viewModel: TrendsViewModel) {
    RangeSelector(state.selectedRange) { viewModel.selectRange(it) }

    val readings = state.bpReadings

    if (readings.isEmpty()) {
        EmptyState("No blood pressure data for this range")
        return
    }

    // Build data points for systolic and diastolic
    val systolicPoints = readings.mapNotNull { reading ->
        try {
            val ts = parseIsoToMillis(reading.measuredAt)
            ts to reading.systolic.toFloat()
        } catch (e: Exception) { null }
    }
    val diastolicPoints = readings.mapNotNull { reading ->
        try {
            val ts = parseIsoToMillis(reading.measuredAt)
            ts to reading.diastolic.toFloat()
        } catch (e: Exception) { null }
    }

    // BP normal/elevated/high baselines
    val bpBaselines = listOf(
        Triple(0f, 120f, ApexStatusGreen),       // normal systolic
        Triple(120f, 129f, ApexStatusYellow),     // elevated
        Triple(129f, 200f, ApexStatusRed)         // high
    )

    TrendsCard(title = "Systolic (mmHg)") {
        LineChart(
            dataPoints = systolicPoints,
            lineColor = ApexPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            yLabel = "mmHg",
            showBaselines = true,
            baselines = bpBaselines
        )
    }

    TrendsCard(title = "Diastolic (mmHg)") {
        LineChart(
            dataPoints = diastolicPoints,
            lineColor = ApexSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            yLabel = "mmHg"
        )
    }

    // Stats row
    val avgSys = readings.map { it.systolic }.average()
    val avgDia = readings.map { it.diastolic }.average()
    val minSys = readings.minOf { it.systolic }
    val maxSys = readings.maxOf { it.systolic }
    StatsRow(
        listOf(
            "Avg Sys" to "%.0f".format(avgSys),
            "Avg Dia" to "%.0f".format(avgDia),
            "Min Sys" to "$minSys",
            "Max Sys" to "$maxSys"
        )
    )

    // Anomaly list (systolic >= 130 or diastolic >= 80)
    val anomalies = readings.filter { it.systolic >= 130 || it.diastolic >= 80 }
    if (anomalies.isNotEmpty()) {
        TrendsCard(title = "Elevated Readings") {
            anomalies.take(5).forEach { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${r.systolic}/${r.diastolic} mmHg",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = ApexStatusRed
                    )
                    Text(
                        text = formatIsoShort(r.measuredAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = ApexOnSurfaceVariant
                    )
                }
                HorizontalDivider(color = ApexOutline.copy(alpha = 0.4f))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sleep Tab
// ---------------------------------------------------------------------------

@Composable
private fun SleepTabContent(state: TrendsState, viewModel: TrendsViewModel) {
    RangeSelector(state.selectedRange) { viewModel.selectRange(it) }

    val sessions = state.sleepSessions

    if (sessions.isEmpty()) {
        EmptyState("No sleep data for this range")
        return
    }

    // Build stacked bars — take last N sessions
    val bars = sessions.takeLast(state.selectedRange.coerceAtMost(30)).map { s ->
        val dateLabel = formatIsoDateShort(s.sleepStart)
        SleepBar(
            date = dateLabel,
            deepMin = s.deepSleepMinutes ?: 0,
            remMin = s.remSleepMinutes ?: 0,
            lightMin = s.lightSleepMinutes ?: 0,
            awakeMin = (s.durationMinutes - (s.deepSleepMinutes ?: 0) -
                    (s.remSleepMinutes ?: 0) - (s.lightSleepMinutes ?: 0)).coerceAtLeast(0)
        )
    }

    TrendsCard(title = "Sleep Stages per Night") {
        // Legend row
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot("Deep", ApexPrimary)
            LegendDot("REM", ApexSecondary)
            LegendDot("Light", Color(0xFF3D4451))
            LegendDot("Awake", ApexOutline)
        }
        Spacer(modifier = Modifier.height(8.dp))
        StackedBarChart(
            bars = bars,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )
    }

    // Stats
    val avgDurationH = sessions.map { it.durationMinutes }.average() / 60.0
    val avgDeepPct = sessions.mapNotNull { s ->
        if (s.durationMinutes > 0 && s.deepSleepMinutes != null)
            s.deepSleepMinutes * 100.0 / s.durationMinutes
        else null
    }.let { if (it.isEmpty()) null else it.average() }
    val avgRemPct = sessions.mapNotNull { s ->
        if (s.durationMinutes > 0 && s.remSleepMinutes != null)
            s.remSleepMinutes * 100.0 / s.durationMinutes
        else null
    }.let { if (it.isEmpty()) null else it.average() }

    StatsRow(
        listOf(
            "Avg Sleep" to "%.1fh".format(avgDurationH),
            "Avg Deep" to if (avgDeepPct != null) "%.0f%%".format(avgDeepPct) else "—",
            "Avg REM" to if (avgRemPct != null) "%.0f%%".format(avgRemPct) else "—"
        )
    )
}

// ---------------------------------------------------------------------------
// Body Tab
// ---------------------------------------------------------------------------

@Composable
private fun BodyTabContent(state: TrendsState, viewModel: TrendsViewModel) {
    RangeSelector(state.selectedRange) { viewModel.selectRange(it) }

    val measurements = state.bodyMeasurements

    if (measurements.isEmpty()) {
        EmptyState("No body measurement data for this range")
        return
    }

    val weightPoints = measurements.mapNotNull { m ->
        if (m.weightKg != null) {
            try {
                parseIsoToMillis(m.measuredAt) to m.weightKg.toFloat()
            } catch (e: Exception) { null }
        } else null
    }

    if (weightPoints.isNotEmpty()) {
        TrendsCard(title = "Weight (kg)") {
            LineChart(
                dataPoints = weightPoints,
                lineColor = ApexPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                yLabel = "kg"
            )
        }
    }

    val fatPoints = measurements.mapNotNull { m ->
        if (m.bodyFatPercent != null) {
            try {
                parseIsoToMillis(m.measuredAt) to m.bodyFatPercent.toFloat()
            } catch (e: Exception) { null }
        } else null
    }

    if (fatPoints.isNotEmpty()) {
        TrendsCard(title = "Body Fat (%)") {
            LineChart(
                dataPoints = fatPoints,
                lineColor = ApexSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                yLabel = "%"
            )
        }
    }

    // Stats
    val currentWeight = measurements.lastOrNull()?.weightKg
    val startWeight = measurements.firstOrNull()?.weightKg
    val change = if (currentWeight != null && startWeight != null) currentWeight - startWeight else null

    val dayCount = state.selectedRange.coerceAtLeast(1)
    val ratePerWeek = if (change != null) change / dayCount * 7 else null

    StatsRow(
        listOf(
            "Current" to if (currentWeight != null) "%.1f kg".format(currentWeight) else "—",
            "Start" to if (startWeight != null) "%.1f kg".format(startWeight) else "—",
            "Change" to if (change != null) "%+.1f kg".format(change) else "—",
            "Rate/wk" to if (ratePerWeek != null) "%+.2f kg".format(ratePerWeek) else "—"
        )
    )
}

// ---------------------------------------------------------------------------
// Shared composables
// ---------------------------------------------------------------------------

@Composable
private fun TrendsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = ApexPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StatsRow(items: List<Pair<String, String>>) {
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
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { (label, value) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "—", style = MaterialTheme.typography.headlineLarge, color = ApexOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = ApexOnSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = ApexStatusRed)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRetry,
                border = androidx.compose.foundation.BorderStroke(1.dp, ApexPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexPrimary)
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun parseIsoToMillis(iso: String): Long {
    return Instant.parse(iso).toEpochMilli()
}

private fun formatIsoShort(iso: String): String {
    return try {
        val instant = Instant.parse(iso)
        val formatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) { iso.take(10) }
}

private fun formatIsoDateShort(iso: String): String {
    return try {
        val instant = Instant.parse(iso)
        val formatter = DateTimeFormatter.ofPattern("MM-dd").withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) { iso.take(5) }
}
