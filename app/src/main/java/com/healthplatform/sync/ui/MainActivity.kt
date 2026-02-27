package com.healthplatform.sync.ui

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.health.connect.client.PermissionController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.healthplatform.sync.data.HealthConnectReader
import com.healthplatform.sync.security.BiometricLockManager
import com.healthplatform.sync.ui.theme.*

// ---------------------------------------------------------------------------
// Navigation destinations
// ---------------------------------------------------------------------------

private sealed class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Dashboard : Destination("dashboard", "Dashboard", Icons.Rounded.FavoriteBorder)
    object Trends : Destination("trends", "Trends", Icons.Rounded.TrendingUp)
    object Activity : Destination("activity", "Activity", Icons.Rounded.FitnessCenter)
    object Settings : Destination("settings", "Settings", Icons.Rounded.Settings)
}

private val destinations = listOf(
    Destination.Dashboard,
    Destination.Trends,
    Destination.Activity,
    Destination.Settings
)

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------

class MainActivity : FragmentActivity() {

    private lateinit var healthConnectReader: HealthConnectReader
    private lateinit var biometricLockManager: BiometricLockManager

    private val requestPermissionsLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { /* permissions updated — dashboard will recheck on next load */ }

    // Track when the app was last paused to enforce re-auth after 5 minutes
    private var pausedAtElapsed: Long = 0L
    private val reauthThresholdMs = 5 * 60 * 1000L // 5 minutes

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        healthConnectReader = HealthConnectReader(this)
        biometricLockManager = BiometricLockManager(this)

        setContent {
            ApexTheme {
                var isAuthenticated by remember {
                    // If biometric is disabled, treat as authenticated
                    mutableStateOf(!biometricLockManager.isEnabled())
                }

                // Trigger initial auth prompt if biometric is enabled
                LaunchedEffect(Unit) {
                    if (biometricLockManager.isEnabled() && !isAuthenticated) {
                        biometricLockManager.authenticate(
                            activity = this@MainActivity,
                            onSuccess = { isAuthenticated = true },
                            onError = { /* user can tap unlock button */ }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !isAuthenticated,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LockScreen(
                        onAuthenticate = {
                            biometricLockManager.authenticate(
                                activity = this@MainActivity,
                                onSuccess = { isAuthenticated = true },
                                onError = { }
                            )
                        }
                    )
                }

                AnimatedVisibility(
                    visible = isAuthenticated,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ApexApp(onRequestPermissions = { requestHealthConnectPermissions() })
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        pausedAtElapsed = SystemClock.elapsedRealtime()
    }

    override fun onResume() {
        super.onResume()
        if (biometricLockManager.isEnabled() && pausedAtElapsed > 0) {
            val elapsed = SystemClock.elapsedRealtime() - pausedAtElapsed
            if (elapsed >= reauthThresholdMs) {
                // Re-auth required — reset content; Compose state will show LockScreen
                // We use a shared state approach via recreate on extended background
                recreate()
            }
        }
    }

    private fun requestHealthConnectPermissions() {
        try {
            requestPermissionsLauncher.launch(healthConnectReader.requiredPermissions)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to launch Health Connect permissions", e)
        }
    }
}

// ---------------------------------------------------------------------------
// Lock Screen
// ---------------------------------------------------------------------------

@Composable
private fun LockScreen(onAuthenticate: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ApexBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App name
            Text(
                text = "Apex",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 42.sp
                ),
                color = ApexPrimary
            )
            Text(
                text = "Peak health, always.",
                style = MaterialTheme.typography.bodyLarge,
                color = ApexOnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Fingerprint icon
            Icon(
                imageVector = Icons.Rounded.Fingerprint,
                contentDescription = "Authenticate",
                tint = ApexPrimary,
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onAuthenticate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ApexPrimary,
                    contentColor = ApexOnPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Unlock")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Root composable
// ---------------------------------------------------------------------------

@Composable
private fun ApexApp(onRequestPermissions: () -> Unit) {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ApexBackground,
        bottomBar = {
            NavigationBar(
                containerColor = ApexSurface,
                contentColor = ApexOnSurface,
                tonalElevation = 0.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                destinations.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.label
                            )
                        },
                        label = { Text(dest.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ApexPrimary,
                            selectedTextColor = ApexPrimary,
                            unselectedIconColor = ApexOnSurfaceVariant,
                            unselectedTextColor = ApexOnSurfaceVariant,
                            indicatorColor = ApexSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Destination.Dashboard.route) {
                DashboardScreen(onRequestPermissions = onRequestPermissions)
            }
            composable(Destination.Trends.route) {
                TrendsScreen()
            }
            composable(Destination.Activity.route) {
                ActivityScreen()
            }
            composable(Destination.Settings.route) {
                SettingsScreen(onRequestPermissions = onRequestPermissions)
            }
        }
    }
}
