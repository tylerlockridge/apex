package com.healthplatform.sync.widget

import android.app.KeyguardManager
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
import com.healthplatform.sync.SyncPrefsKeys
import com.healthplatform.sync.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Glance Widget
// ---------------------------------------------------------------------------

class HealthGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences(SyncPrefsKeys.FILE_NAME, Context.MODE_PRIVATE)

        val bpSystolic     = if (prefs.contains(SyncPrefsKeys.LAST_BP_SYSTOLIC)) prefs.getInt(SyncPrefsKeys.LAST_BP_SYSTOLIC, 0) else null
        val bpDiastolic    = if (prefs.contains(SyncPrefsKeys.LAST_BP_DIASTOLIC)) prefs.getInt(SyncPrefsKeys.LAST_BP_DIASTOLIC, 0) else null
        val sleepDurationMin = if (prefs.contains(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN)) prefs.getInt(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN, 0) else null
        val hrvMs          = if (prefs.contains(SyncPrefsKeys.LAST_HRV_MS)) prefs.getFloat(SyncPrefsKeys.LAST_HRV_MS, 0f).toDouble() else null
        val lastSyncMs     = prefs.getLong(SyncPrefsKeys.LAST_SYNC, 0L)

        val lastSyncLabel = if (lastSyncMs > 0) {
            val syncDate = Calendar.getInstance().apply { timeInMillis = lastSyncMs }
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
            val timeStr = timeFmt.format(Date(lastSyncMs))
            val prefix = when {
                syncDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                        syncDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
                syncDate.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                        syncDate.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
                else -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(lastSyncMs))
            }
            "Synced $prefix $timeStr"
        } else {
            "Never synced"
        }

        // S-3: Hide raw health values when the device is locked to prevent PHI
        // exposure on the lock screen. FLAG_SECURE on MainActivity doesn't cover
        // Glance widgets since they render in the launcher process.
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = keyguard.isDeviceLocked

        val bpLabel = if (isLocked) "***" else if (bpSystolic != null && bpDiastolic != null) "$bpSystolic/$bpDiastolic" else "—"
        val sleepLabel = if (isLocked) "***" else if (sleepDurationMin != null) {
            val h = sleepDurationMin / 60
            val m = sleepDurationMin % 60
            "${h}h ${m}m"
        } else "—"
        val hrvLabel = if (isLocked) "***" else if (hrvMs != null) "%.0f ms".format(hrvMs) else "—"

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

private val WidgetBackground = Color(0xFF0C1320)  // matches ApexBackground
private val WidgetSurface    = Color(0xFF141E30)  // matches ApexSurface
private val WidgetAccent     = Color(0xFF5B9BD5)  // matches ApexPrimary (steel blue)
private val WidgetText       = Color(0xFFE6EDF3)
private val WidgetMuted      = Color(0xFF8B949E)

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
                    color = ColorProvider(WidgetAccent),
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
                    style = TextStyle(color = ColorProvider(WidgetAccent), fontSize = 10.sp)
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
