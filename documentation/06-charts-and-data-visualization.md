# Feature: Charts & Data Visualization

*Created: 2026-03-02 | Updated: 2026-03-02 | Project: Apex*

---

## Feature Overview

**What it does:**
Three custom Compose chart components handle all data visualization in Apex. `LineChart.kt` is the primary trend chart, `StackedBarChart.kt` handles sleep stage breakdowns, and `SparklineChart.kt` provides compact dashboard summaries. All charts handle empty data gracefully.

**What it does NOT do:**
- Does not use any third-party charting library (all charts are custom-drawn Compose Canvas)
- Does not support pinch-to-zoom or horizontal scrolling on charts
- Does not export chart images

---

## Chart Components

### LineChart.kt

Used for: Blood pressure trend (Trends screen), overlay charts

| Feature | Detail |
|---------|--------|
| Reveal animation | Left-to-right, 900ms duration |
| Interaction | Tap to show tooltip with timestamp + value |
| Baseline bands | Optional colored regions (normal / elevated / high ranges) |
| Grid | Horizontal grid lines drawn |
| Y-axis | Labels rendered on left axis |
| Empty state | "No data available" message shown |

Baseline bands are used for blood pressure to visually mark normal (<120/80), elevated (120-129/<80), and high (>=130/80) regions.

### StackedBarChart.kt

Used for: Sleep stages (Trends screen — Sleep sub-tab)

| Feature | Detail |
|---------|--------|
| Stacking | Deep / REM / Light / Awake stacked per night |
| Overlay | Sleep score rendered as a line on top of bars |
| Pagination | Handles paginated sleep data |
| Colors | Material 3 theme colors (distinct per stage) |
| Empty state | "No data available" message shown |

### SparklineChart.kt

Used for: Dashboard summary cards (BP card, Sleep card, HRV card)

| Feature | Detail |
|---------|--------|
| Style | Compact 7-day sparkline, no axes, no labels |
| Purpose | At-a-glance trend indicator; detail is in Trends screen |
| Empty state | Empty state handled gracefully |

---

## Tooltip Behavior

Tap gesture on `LineChart` or `StackedBarChart` shows a floating overlay with:
- ISO 8601 timestamp (displayed in device local timezone via `ZoneId.systemDefault()`)
- Metric value with unit

Tooltip dismisses on tap outside or tap on another data point.

---

## Time Range Selector

Located on the Trends screen. Options: 7 / 30 / 90 days. Selecting a range re-fetches data from the server via `ServerApiClient` with the corresponding `days` parameter.

---

## Empty Data Handling

All three chart components check for empty input before rendering. Empty state shows a centered "No data available" string. No crash or blank canvas.

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| LineChart animated reveal | ✅ PASS | 900ms left-to-right |
| LineChart tap-to-tooltip | ✅ PASS | Timestamp + value overlay |
| LineChart baseline bands | ✅ PASS | Used for BP normal/elevated/high |
| LineChart empty state | ✅ PASS | |
| StackedBarChart sleep stages | ✅ PASS | 4 stage colors + score overlay |
| StackedBarChart empty state | ✅ PASS | |
| SparklineChart dashboard | ✅ PASS | 7-day compact, no axes |
| SparklineChart empty state | ✅ PASS | |
| Time range selector (7/30/90d) | ✅ PASS | Re-fetches from server |
| Third-party chart library | 🔲 TODO | Not used; all custom Canvas |
