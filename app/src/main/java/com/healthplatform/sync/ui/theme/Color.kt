package com.healthplatform.sync.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Background layers — deep blue-navy dark theme
// ---------------------------------------------------------------------------
val ApexBackground     = Color(0xFF0C1320)
val ApexSurface        = Color(0xFF141E30)
val ApexSurfaceVariant = Color(0xFF1B2640)

// ---------------------------------------------------------------------------
// Primary accent — steel blue
// ---------------------------------------------------------------------------
val ApexPrimary   = Color(0xFF5B9BD5)   // vibrant steel blue
val ApexOnPrimary = Color(0xFFFFFFFF)   // white text/icons on primary

// ---------------------------------------------------------------------------
// Secondary accent — deeper steel blue (charts, secondary elements)
// ---------------------------------------------------------------------------
val ApexSecondary   = Color(0xFF2E6DB8)
val ApexOnSecondary = Color(0xFFFFFFFF)

// ---------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------
val ApexOnBackground       = Color(0xFFE5EEF7)  // slightly blue-tinted white
val ApexOnSurface          = Color(0xFFE5EEF7)
val ApexOnSurfaceVariant   = Color(0xFF7A90B0)  // blue-grey secondary text

// ---------------------------------------------------------------------------
// Utility
// ---------------------------------------------------------------------------
val ApexError   = Color(0xFFFF6B6B)
val ApexOnError = Color(0xFFFFFFFF)
val ApexOutline = Color(0xFF253552)   // dark blue-grey borders/dividers

// ---------------------------------------------------------------------------
// Status indicators
// ---------------------------------------------------------------------------
val ApexStatusGreen  = Color(0xFF4ADE80)  // bright green — pops on navy
val ApexStatusYellow = Color(0xFFFBBF24)  // amber
val ApexStatusRed    = Color(0xFFFF6B6B)  // coral red

// ---------------------------------------------------------------------------
// Sleep-stage chart color (replaces hardcoded 0xFF3D4451)
// ---------------------------------------------------------------------------
val ApexSleepLight = Color(0xFF3A5A80)   // blue-tinted mid-tone for light sleep

// ---------------------------------------------------------------------------
// Card accent bar (matches primary)
// ---------------------------------------------------------------------------
val ApexCardAccent = ApexPrimary
