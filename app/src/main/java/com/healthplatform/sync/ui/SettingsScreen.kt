package com.healthplatform.sync.ui

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthplatform.sync.BuildConfig
import com.healthplatform.sync.SyncPrefsKeys
import com.healthplatform.sync.security.BiometricLockManager
import com.healthplatform.sync.security.SecurePrefs
import com.healthplatform.sync.service.SyncWorker
import com.healthplatform.sync.ui.theme.*
import com.healthplatform.sync.ui.util.rememberApexHaptic
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

@Composable
fun SettingsScreen(
    onRequestPermissions: () -> Unit,
    onLock: (() -> Unit)? = null,
    onScanQr: (() -> Unit)? = null,
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val haptic = rememberApexHaptic()
    val prefs = remember { context.getSharedPreferences(SyncPrefsKeys.FILE_NAME, Context.MODE_PRIVATE) }
    val biometricManager = remember { BiometricLockManager(context) }

    var autoSyncEnabled by remember { mutableStateOf(prefs.getBoolean(SyncPrefsKeys.AUTO_SYNC, false)) }
    var lastSyncMs by remember { mutableStateOf(prefs.getLong(SyncPrefsKeys.LAST_SYNC, 0L)) }
    var biometricEnabled by remember { mutableStateOf(biometricManager.isEnabled()) }
    var apiKey by remember { mutableStateOf(SecurePrefs.getApiKey(context)) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    // Sync history: list of (timestampMs, success) pairs, newest first
    var syncHistory by remember { mutableStateOf<List<Pair<Long, Boolean>>>(emptyList()) }

    // "Clear all data" confirmation dialog
    var showClearDialog by remember { mutableStateOf(false) }

    // M-1: runtime POST_NOTIFICATIONS permission (required on Android 13+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled silently — notifications will work or OS blocks them */ }

    // M-4 / L-7: server status and per-type HC permissions come from SettingsViewModel
    // so the OkHttpClient is built once rather than on every screen open.
    val serverState by settingsViewModel.serverState.collectAsStateWithLifecycle()

    // M-3: register a SharedPreferences listener so sync history and last-sync timestamp
    // update live after a sync finishes, without the user needing to leave and re-open Settings.
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                SyncPrefsKeys.LAST_SYNC -> lastSyncMs = prefs.getLong(SyncPrefsKeys.LAST_SYNC, 0L)
                SyncPrefsKeys.SYNC_HISTORY -> {
                    val historyJson = prefs.getString(SyncPrefsKeys.SYNC_HISTORY, "[]") ?: "[]"
                    val arr = try { JSONArray(historyJson) } catch (e: Exception) { JSONArray() }
                    syncHistory = (0 until arr.length()).map { i ->
                        val obj = arr.getJSONObject(i)
                        Pair(obj.getLong("t"), obj.getBoolean("ok"))
                    }
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(Unit) {
        // Parse sync history from prefs
        val historyJson = prefs.getString(SyncPrefsKeys.SYNC_HISTORY, "[]") ?: "[]"
        val arr = try { JSONArray(historyJson) } catch (e: Exception) { JSONArray() }
        syncHistory = (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Pair(obj.getLong("t"), obj.getBoolean("ok"))
        }
        // Refresh server status and HC permissions each time settings opens.
        settingsViewModel.checkAll()
        // M-1: request POST_NOTIFICATIONS permission if not already granted (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = ApexSurfaceVariant,
                        contentColor = ApexOnSurface,
                        actionColor = ApexPrimary
                    )
                }
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 38.sp
                ),
                color = ApexOnBackground
            )

            // ----------------------------------------------------------------
            // A — Sync Section
            // ----------------------------------------------------------------
            SettingsCard(title = "Sync") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Auto-sync every 15 min", style = MaterialTheme.typography.bodyMedium, color = ApexOnSurface)
                        Text(text = "Requires network connection", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
                    }
                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = { enabled ->
                            haptic.tick()
                            autoSyncEnabled = enabled
                            prefs.edit().putBoolean(SyncPrefsKeys.AUTO_SYNC, enabled).apply()
                            if (enabled) SyncWorker.schedule(context) else SyncWorker.cancel(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ApexOnPrimary,
                            checkedTrackColor = ApexPrimary,
                            uncheckedThumbColor = ApexOnSurfaceVariant,
                            uncheckedTrackColor = ApexSurfaceVariant
                        )
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ApexOutline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Sync window", style = MaterialTheme.typography.bodyMedium, color = ApexOnSurface)
                        Text(text = "Reads last 30 days of Health Connect data", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ApexOutline)

                if (lastSyncMs > 0) {
                    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Last sync", style = MaterialTheme.typography.bodyMedium, color = ApexOnSurface)
                        Text(text = dateFormat.format(Date(lastSyncMs)), style = MaterialTheme.typography.bodySmall, color = ApexOnSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Sync history — last 10 events, newest first
                if (syncHistory.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = ApexOutline)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sync History",
                        style = MaterialTheme.typography.labelMedium,
                        color = ApexOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val historyFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    syncHistory.forEach { (ts, ok) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = historyFmt.format(Date(ts)),
                                style = MaterialTheme.typography.bodySmall,
                                color = ApexOnSurfaceVariant
                            )
                            Icon(
                                imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                contentDescription = if (ok) "Success" else "Failed",
                                tint = if (ok) ApexStatusGreen else ApexStatusRed,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        haptic.confirm()
                        SyncWorker.runOnce(context)
                        scope.launch { snackbarHostState.showSnackbar("Sync started") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ApexPrimary, contentColor = ApexOnPrimary)
                ) {
                    Icon(imageVector = Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync All Now")
                }
            }

            // ----------------------------------------------------------------
            // B — Security Section
            // ----------------------------------------------------------------
            SettingsCard(title = "Security") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Biometric Lock", style = MaterialTheme.typography.bodyMedium, color = ApexOnSurface)
                        Text(
                            text = "Require fingerprint/face to open app",
                            style = MaterialTheme.typography.labelSmall,
                            color = ApexOnSurfaceVariant
                        )
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { enabled ->
                            haptic.tick()
                            val success = biometricManager.setEnabled(enabled)
                            if (success) {
                                biometricEnabled = enabled
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (enabled) "Biometric lock enabled" else "Biometric lock disabled"
                                    )
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "No enrolled biometrics — enroll in system Settings first"
                                    )
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ApexOnPrimary,
                            checkedTrackColor = ApexPrimary,
                            uncheckedThumbColor = ApexOnSurfaceVariant,
                            uncheckedTrackColor = ApexSurfaceVariant
                        )
                    )
                }

                if (biometricEnabled && onLock != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ApexOutline)
                    OutlinedButton(
                        onClick = { haptic.click(); onLock() },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ApexPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexPrimary)
                    ) {
                        Icon(imageVector = Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lock App Now")
                    }
                }
            }

            // ----------------------------------------------------------------
            // C — Health Connect Section
            // ----------------------------------------------------------------
            SettingsCard(title = "Health Connect") {
                val dataTypes = listOf("Blood Pressure", "Sleep", "Weight", "Body Composition", "HRV")
                dataTypes.forEachIndexed { index, type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = type, style = MaterialTheme.typography.bodyMedium, color = ApexOnSurface)
                        // L-7: per-type granted/denied status rather than all-or-nothing
                        when {
                            !serverState.isHcAvailable ->
                                Icon(imageVector = Icons.Rounded.Cancel, contentDescription = "Unavailable", tint = ApexStatusRed, modifier = Modifier.size(18.dp))
                            serverState.hcPermissions[type] == true ->
                                Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = "Granted", tint = ApexStatusGreen, modifier = Modifier.size(18.dp))
                            else ->
                                Icon(imageVector = Icons.Rounded.Cancel, contentDescription = "Not granted", tint = ApexStatusRed, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (index < dataTypes.lastIndex) {
                        HorizontalDivider(color = ApexOutline.copy(alpha = 0.5f))
                    }
                }

                val allGranted = serverState.hcPermissions.values.all { it }
                if (!allGranted && serverState.isHcAvailable) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onRequestPermissions,
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ApexPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexPrimary)
                    ) {
                        Text("Manage Permissions")
                    }
                }
            }

            // ----------------------------------------------------------------
            // D — Server API Key
            // ----------------------------------------------------------------
            SettingsCard(title = "Server") {
                if (onScanQr != null) {
                    OutlinedButton(
                        onClick = { haptic.click(); onScanQr() },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ApexPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan QR Code")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ApexOutline)
                }
                Text(
                    text = "API Key",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ApexOnSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter API key", color = ApexOnSurfaceVariant) },
                    singleLine = true,
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                imageVector = if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (apiKeyVisible) "Hide key" else "Show key",
                                tint = ApexOnSurfaceVariant
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        SecurePrefs.setApiKey(context, apiKey)
                        scope.launch { snackbarHostState.showSnackbar("API key saved") }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ApexPrimary,
                        unfocusedBorderColor = ApexOutline,
                        focusedTextColor = ApexOnSurface,
                        unfocusedTextColor = ApexOnSurface,
                        cursorColor = ApexPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        haptic.click()
                        SecurePrefs.setApiKey(context, apiKey)
                        // M-4: ApiService.get() refreshes the key on next sync call — no restart needed.
                        // ViewModels with cached ServerApiClient will pick up the key after next recompose.
                        scope.launch { snackbarHostState.showSnackbar("API key saved") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ApexPrimary, contentColor = ApexOnPrimary)
                ) {
                    Text("Save API Key")
                }
            }

            // ----------------------------------------------------------------
            // E — Data Management
            // ----------------------------------------------------------------
            SettingsCard(title = "Data") {
                Column {
                    Text(
                        text = "Clear all data",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ApexOnSurface
                    )
                    Text(
                        text = "Removes your API key, biometric setting, sync history, and all cached health values from this device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ApexOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { haptic.click(); showClearDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ApexStatusRed),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexStatusRed)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All Data")
                    }
                }
            }

            // ----------------------------------------------------------------
            // F — About Section
            // ----------------------------------------------------------------
            SettingsCard(title = "About") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Version", style = MaterialTheme.typography.bodyMedium, color = ApexOnSurface)
                    Text(text = "v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = ApexOnSurfaceVariant)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ApexOutline)

                // Server connection status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Server Connection", style = MaterialTheme.typography.bodyMedium, color = ApexOnSurface)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                        ) {
                            drawCircle(
                                color = when (serverState.serverStatus) {
                                    true -> ApexStatusGreen
                                    false -> ApexStatusRed
                                    null -> ApexStatusYellow
                                }
                            )
                        }
                        Text(
                            text = when (serverState.serverStatus) {
                                true -> "Connected"
                                false -> "Unreachable"
                                null -> "Checking..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (serverState.serverStatus) {
                                true -> ApexStatusGreen
                                false -> ApexStatusRed
                                null -> ApexStatusYellow
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ApexOutline)

                if (serverState.serverVersionOk == false) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ApexStatusRed.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = "Outdated server",
                            tint = ApexStatusRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Server version is outdated. Update Health Platform Desktop for best results.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ApexStatusRed
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = "Apex v${BuildConfig.VERSION_NAME} — Peak health, always.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexOnSurfaceVariant
                )
            }
        }
    }

    // Confirmation dialog for destructive "Clear all data" action
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all data?", color = ApexOnSurface) },
            text = {
                Text(
                    "This will erase your API key, biometric setting, and all cached health data from this device. " +
                        "You will need to re-enter your API key.",
                    color = ApexOnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        haptic.reject()
                        prefs.edit().clear().commit()
                        SecurePrefs.clearAll(context)
                        apiKey = ""
                        autoSyncEnabled = false
                        biometricEnabled = false
                        lastSyncMs = 0L
                        syncHistory = emptyList()
                        scope.launch { snackbarHostState.showSnackbar("All data cleared") }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ApexStatusRed)
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = ApexOnSurfaceVariant)
                }
            },
            containerColor = ApexSurface,
            titleContentColor = ApexOnSurface,
            textContentColor = ApexOnSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// Reusable components
// ---------------------------------------------------------------------------

@Composable
private fun SettingsCard(
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
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = ApexPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}
