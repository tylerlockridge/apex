package com.healthplatform.sync.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.healthplatform.sync.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Glance Widget
// ---------------------------------------------------------------------------

class HealthGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("health_sync", Context.MODE_PRIVATE)

        val bpSystolic = if (prefs.contains("last_bp_systolic")) prefs.getInt("last_bp_systolic", 0) else null
        val bpDiastolic = if (prefs.contains("last_bp_diastolic")) prefs.getInt("last_bp_diastolic", 0) else null
        val sleepDurationMin = if (prefs.contains("last_sleep_duration_min")) prefs.getInt("last_sleep_duration_min", 0) else null
        val hrvMs = if (prefs.contains("last_hrv_ms")) prefs.getFloat("last_hrv_ms", 0f).toDouble() else null
        val lastSyncMs = prefs.getLong("last_sync", 0L)

        val lastSyncLabel = if (lastSyncMs > 0) {
            val df = SimpleDateFormat("h:mm a", Locale.getDefault())
            "Synced ${df.format(Date(lastSyncMs))}"
        } else {
            "Never synced"
        }

        val bpLabel = if (bpSystolic != null && bpDiastolic != null) "$bpSystolic/$bpDiastolic" else "—"
        val sleepLabel = if (sleepDurationMin != null) {
            val h = sleepDurationMin / 60
            val m = sleepDurationMin % 60
            "${h}h ${m}m"
        } else "—"
        val hrvLabel = if (hrvMs != null) "%.0f ms".format(hrvMs) else "—"

        provideContent {
            WidgetContent(
                bpLabel = bpLabel,
                sleepLabel = sleepLabel,
                hrvLabel = hrvLabel,
                lastSyncLabel = lastSyncLabel,
                context = context
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Widget UI
// ---------------------------------------------------------------------------

private val WidgetBackground = Color(0xFF0D1117)
private val WidgetSurface = Color(0xFF161B22)
private val WidgetTeal = Color(0xFF00D4AA)
private val WidgetText = Color(0xFFE6EDF3)
private val WidgetMuted = Color(0xFF8B949E)

@Composable
private fun WidgetContent(
    bpLabel: String,
    sleepLabel: String,
    hrvLabel: String,
    lastSyncLabel: String,
    context: Context
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        // App title row
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "Apex",
                style = TextStyle(
                    color = ColorProvider(WidgetTeal),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(GlanceModifier.height(8.dp))

        // Metrics grid (2x2)
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.Start
        ) {
            // BP (left)
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "BP",
                    style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 10.sp)
                )
                Text(
                    text = bpLabel,
                    style = TextStyle(
                        color = ColorProvider(WidgetText),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "mmHg",
                    style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 9.sp)
                )
            }

            // Sleep (right)
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "Sleep",
                    style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 10.sp)
                )
                Text(
                    text = sleepLabel,
                    style = TextStyle(
                        color = ColorProvider(WidgetText),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(GlanceModifier.height(8.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.Start
        ) {
            // HRV (left)
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "HRV",
                    style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 10.sp)
                )
                Text(
                    text = hrvLabel,
                    style = TextStyle(
                        color = ColorProvider(WidgetText),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Last sync (right)
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "Sync",
                    style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 10.sp)
                )
                Text(
                    text = lastSyncLabel,
                    style = TextStyle(color = ColorProvider(WidgetTeal), fontSize = 10.sp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Receiver
// ---------------------------------------------------------------------------

class HealthGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HealthGlanceWidget()
}
