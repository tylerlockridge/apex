# Feature: Theme & Styling

*Created: 2026-03-02 | Updated: 2026-03-02 | Project: Apex*

---

## Feature Overview

**What it does:**
`Theme.kt` and `Color.kt` define the global `ApexTheme` composable using Material 3 design tokens. The color system uses a steel blue palette with dark surfaces. `HapticManager.kt` provides semantic haptic feedback patterns. `ShimmerEffect.kt` provides skeleton loading animations. `ApexWatermarkCanvas` renders a subtle app identity watermark behind all screens.

**What it does NOT do:**
- Does not support light mode (dark-only color system)
- Does not support dynamic color (Material You wallpaper-based theming)

---

## Color System

| Role | Value | Usage |
|------|-------|-------|
| Primary | `#5B9BD5` | Steel blue — buttons, active tab, highlights |
| Background (deep) | `#0D1117` | App background |
| Surface | `#161B22` | Card surfaces, bottom nav |
| No purple anywhere | — | Full steel blue palette; purple explicitly excluded |

All colors are Material 3 design tokens applied via `ApexTheme`.

---

## HapticManager.kt

Semantic haptic patterns using `VibrationEffect.Composition` (requires API 34+, which matches `minSdk = 34`):

| Method | Trigger | Effect |
|--------|---------|--------|
| `tick()` | Tab switches, toggle changes | Light tick |
| `click()` | Button presses | Standard click |
| `confirm()` | Sync complete | Success confirmation |
| `reject()` | Errors | Rejection feedback |

All haptic calls are safe to invoke from Compose composables via `LocalView.current.performHapticFeedback()` or `Vibrator` service.

---

## ShimmerEffect.kt

- Animated horizontal shimmer across skeleton placeholder elements
- Applied to cards and list items while data is loading (Trends screen, Activity screen)
- Uses `InfiniteTransition` with `LinearEasing`
- Integrates with Material 3 surface colors for consistent appearance

---

## ApexWatermarkCanvas

- Draws a chevron-A logo watermark behind all screens
- Opacity: 5% (subtle, non-intrusive)
- Implemented as a `Box` wrapper that all screens are placed inside
- Canvas-drawn; not an image asset

---

## Animation Inventory

| Animation | Location | Detail |
|-----------|----------|--------|
| Screen transitions | MainActivity | AnimatedContent, 200ms fade-in / 150ms fade-out |
| Card stagger reveal | Dashboard | Cards animate in with staggered delay |
| Tab content switch | Trends sub-tabs | AnimatedContent |
| Counter morphing | Stats values | Animated number increment on load |
| Chart data reveal | LineChart | Left-to-right reveal, 900ms |
| Shimmer skeleton | Trends / Activity | InfiniteTransition horizontal sweep |
| FAB pulse | Dashboard | Pulsing scale animation on sync FAB |

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| ApexTheme (Material 3) | ✅ PASS | Steel blue palette, dark surfaces |
| Color system | ✅ PASS | #5B9BD5 primary, #0D1117 / #161B22 surfaces |
| HapticManager (4 patterns) | ✅ PASS | API 34+ VibrationEffect.Composition |
| ShimmerEffect skeleton | ✅ PASS | InfiniteTransition, Trends + Activity |
| ApexWatermarkCanvas | ✅ PASS | 5% opacity chevron-A |
| Screen transition animations | ✅ PASS | AnimatedContent fade |
| Chart reveal animations | ✅ PASS | 900ms left-to-right |
| Light mode support | 🔲 TODO | Dark-only; not planned |
| Dynamic color (Material You) | 🔲 TODO | Not implemented |
