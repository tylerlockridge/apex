package com.healthplatform.sync.ui

import android.content.Context
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.healthplatform.sync.BuildConfig
import com.healthplatform.sync.data.HealthConnectReader
import com.healthplatform.sync.security.BiometricLockManager
import com.healthplatform.sync.service.SyncWorker
import com.healthplatform.sync.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

@Composable
fun SettingsScreen(
    onRequestPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val prefs = remember { context.getSharedPreferences("health_sync", Context.MODE_PRIVATE) }
    val biometricManager = remember { BiometricLockManager(context) }

    var autoSyncEnabled by remember { mutableStateOf(prefs.getBoolean("auto_sync", false)) }
    var lastSyncMs by remember { mutableStateOf(prefs.getLong("last_sync", 0L)) }
    var biometricEnabled by remember { mutableStateOf(biometricManager.isEnabled()) }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var apiKeyVisible by remember { mutableStateOf(false) }

    var isHealthConnectAvailable by remember { mutableStateOf(false) }
    var hasAllPermissions by remember { mutableStateOf(false) }

    // Server connection status
    var serverStatus by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        isHealthConnectAvailable = HealthConnectReader.isAvailable(context)
        if (isHealthConnectAvailable) {
            try {
                val reader = HealthConnectReader(context)
                hasAllPermissions = reader.hasAllPermissions()
            } catch (e: Exception) {
                hasAllPermissions = false
            }
        }

        // Ping server using stored API key
        try {
            val storedKey = prefs.getString("api_key", "") ?: ""
            val url = java.net.URL("${com.healthplatform.sync.Config.SERVER_URL}/api/bp?days=1")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $storedKey")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            serverStatus = conn.responseCode in 200..299
            conn.disconnect()
        } catch (e: Exception) {
            serverStatus = false
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
        containerColor = ApexBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 56.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
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
                            autoSyncEnabled = enabled
                            prefs.edit().putBoolean("auto_sync", enabled).apply()
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
                        Text(text = "Reads last 24 hours of Health Connect data", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
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

                Button(
                    onClick = {
                        SyncWorker.runOnce(context)
                        lastSyncMs = System.currentTimeMillis()
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
                            biometricEnabled = enabled
                            biometricManager.setEnabled(enabled)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (enabled) "Biometric lock enabled" else "Biometric lock disabled"
                                )
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

                if (biometricEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ApexOutline)
                    OutlinedButton(
                        onClick = {
                            val activity = context as? FragmentActivity
                            if (activity != null) {
                                biometricManager.authenticate(
                                    activity = activity,
                                    onSuccess = {
                                        scope.launch { snackbarHostState.showSnackbar("Authenticated successfully") }
                                    },
                                    onError = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ApexOutline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexOnSurface)
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
                        if (!isHealthConnectAvailable) {
                            Icon(imageVector = Icons.Rounded.Cancel, contentDescription = null, tint = ApexStatusRed, modifier = Modifier.size(18.dp))
                        } else if (hasAllPermissions) {
                            Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = null, tint = ApexStatusGreen, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(imageVector = Icons.Rounded.Cancel, contentDescription = null, tint = ApexStatusRed, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (index < dataTypes.lastIndex) {
                        HorizontalDivider(color = ApexOutline.copy(alpha = 0.5f))
                    }
                }

                if (!hasAllPermissions && isHealthConnectAvailable) {
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
                        prefs.edit().putString("api_key", apiKey).apply()
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
                        prefs.edit().putString("api_key", apiKey).apply()
                        scope.launch { snackbarHostState.showSnackbar("API key saved — restart app to apply") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ApexPrimary)
                ) {
                    Text("Save API Key", color = ApexBackground)
                }
            }

            // ----------------------------------------------------------------
            // E — About Section
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
                                color = when (serverStatus) {
                                    true -> ApexStatusGreen
                                    false -> ApexStatusRed
                                    null -> ApexStatusYellow
                                }
                            )
                        }
                        Text(
                            text = when (serverStatus) {
                                true -> "Connected"
                                false -> "Unreachable"
                                null -> "Checking..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (serverStatus) {
                                true -> ApexStatusGreen
                                false -> ApexStatusRed
                                null -> ApexStatusYellow
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ApexOutline)
                Text(
                    text = "Apex v${BuildConfig.VERSION_NAME} — Peak health, always.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexOnSurfaceVariant
                )
            }
        }
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
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = ApexPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}
