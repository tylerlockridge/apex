package com.healthplatform.sync.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthplatform.sync.data.db.FoodCacheEntity
import com.healthplatform.sync.data.db.FoodEntryCacheEntity
import com.healthplatform.sync.data.db.NutritionSyncState
import com.healthplatform.sync.service.PatchField
import com.healthplatform.sync.ui.theme.*
import com.healthplatform.sync.ui.util.rememberApexHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(
    viewModel: NutritionViewModel = viewModel(),
    onNavigateToTargets: () -> Unit,
    onNavigateToHydration: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = rememberApexHaptic()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.click()
                    viewModel.showAddEntry()
                },
                containerColor = ApexNutritionAccent,
                contentColor = Color.White,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Log food")
            }
        }
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Nutrition",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = ApexOnBackground,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        haptic.click()
                        onNavigateToHydration()
                    }) {
                        Icon(Icons.Rounded.WaterDrop, "Hydration", tint = ApexHydrationAccent)
                    }
                    IconButton(onClick = {
                        haptic.click()
                        onNavigateToTargets()
                    }) {
                        Icon(Icons.Rounded.TrackChanges, "Targets", tint = ApexNutritionAccent)
                    }
                }
            }

            // Macro summary card
            MacroSummaryCard(state)

            // Pending sync indicator
            if (state.pendingWriteCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.CloudQueue, null, tint = ApexOnSurfaceVariant, modifier = Modifier.size(14.dp))
                    Text(
                        "${state.pendingWriteCount} pending sync",
                        style = MaterialTheme.typography.labelSmall,
                        color = ApexOnSurfaceVariant,
                    )
                }
            }

            // Food entries by meal
            if (state.entries.isEmpty() && !state.isLoading) {
                NutritionEmptyState()
            } else {
                val grouped = state.entries.groupBy { it.mealType ?: "other" }
                val mealOrder = listOf("breakfast", "lunch", "dinner", "snack", "other")
                val mealLabels = mapOf(
                    "breakfast" to "Breakfast",
                    "lunch" to "Lunch",
                    "dinner" to "Dinner",
                    "snack" to "Snacks",
                    "other" to "Other",
                )
                for (meal in mealOrder) {
                    val entries = grouped[meal] ?: continue
                    MealSection(
                        label = mealLabels[meal] ?: meal,
                        entries = entries,
                        onEdit = { viewModel.startEditEntry(it) },
                        onDelete = { viewModel.deleteFoodEntry(it) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }

    // Add entry bottom sheet
    if (state.showAddEntrySheet) {
        AddFoodEntrySheet(
            state = state,
            onSearch = { viewModel.searchFoods(it) },
            onSelectFood = { viewModel.selectFood(it) },
            onAddEntry = { food, servings, mealType -> viewModel.addFoodEntry(food, servings, mealType) },
            onCreateCustom = { viewModel.showCreateFoodDialog() },
            onDismiss = { viewModel.hideAddEntry() },
        )
    }

    // Create custom food dialog
    if (state.showCreateFoodDialog) {
        CreateFoodDialog(
            onConfirm = { name, cal, protein, carbs, fat, brand ->
                viewModel.createCustomFood(name, cal, protein, carbs, fat, brand)
            },
            onDismiss = { viewModel.hideCreateFoodDialog() },
        )
    }

    // Edit food entry dialog
    val editingEntry = state.editingEntry
    if (editingEntry != null) {
        EditFoodEntryDialog(
            entry = editingEntry,
            onSave = { servings, mealType -> viewModel.saveEditEntry(servings, mealType) },
            onDismiss = { viewModel.cancelEditEntry() },
        )
    }
}

@Composable
private fun MacroSummaryCard(state: NutritionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(ApexNutritionAccent.copy(alpha = 0.9f), ApexNutritionAccent.copy(alpha = 0.3f))
                        )
                    )
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Calorie headline
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        Text("CALORIES", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
                        Text(
                            "${state.totalCalories.toInt()}",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = ApexNutritionAccent,
                        )
                    }
                    if (state.target != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("/ ${state.target.calories}", style = MaterialTheme.typography.titleMedium, color = ApexOnSurfaceVariant)
                            val remaining = state.target.calories - state.totalCalories.toInt()
                            Text(
                                if (remaining >= 0) "$remaining left" else "${-remaining} over",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (remaining >= 0) ApexStatusGreen else ApexStatusRed,
                            )
                        }
                    }
                }

                // Calorie progress bar
                if (state.target != null && state.target.calories > 0) {
                    val progress = (state.totalCalories / state.target.calories).toFloat().coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ApexNutritionAccent,
                        trackColor = ApexOutline,
                    )
                }

                // Macro bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MacroBar("Protein", state.totalProtein, state.target?.proteinG, Color(0xFF60A5FA), Modifier.weight(1f))
                    MacroBar("Carbs", state.totalCarbs, state.target?.carbsG, Color(0xFFA78BFA), Modifier.weight(1f))
                    MacroBar("Fat", state.totalFat, state.target?.fatG, Color(0xFFFBBF24), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MacroBar(label: String, current: Double, target: Int?, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${current.toInt()}g", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = ApexOnSurface)
        if (target != null && target > 0) {
            Text("/ ${target}g", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            val progress = (current / target).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = ApexOutline,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
    }
}

@Composable
private fun MealSection(label: String, entries: List<FoodEntryCacheEntity>, onEdit: (FoodEntryCacheEntity) -> Unit, onDelete: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = ApexOnSurfaceVariant)
            Text(
                "${entries.sumOf { it.calories }.toInt()} kcal",
                style = MaterialTheme.typography.labelMedium,
                color = ApexOnSurfaceVariant,
            )
        }
        entries.forEach { entry ->
            FoodEntryRow(entry = entry, onEdit = { onEdit(entry) }, onDelete = { onDelete(entry.id) })
        }
    }
}

@Composable
private fun FoodEntryRow(entry: FoodEntryCacheEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    val haptic = rememberApexHaptic()
    val isPending = entry.syncState != NutritionSyncState.SYNCED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { haptic.tick(); onEdit() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        entry.foodName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = ApexOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isPending) {
                        Icon(Icons.Rounded.CloudQueue, null, tint = ApexOnSurfaceVariant, modifier = Modifier.size(12.dp))
                    }
                }
                Text(
                    "${entry.servings.let { if (it == 1.0) "" else "×${it} · " }}${entry.calories.toInt()} kcal" +
                        (entry.proteinG?.let { " · ${it.toInt()}g P" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexOnSurfaceVariant,
                )
            }
            IconButton(
                onClick = {
                    haptic.click()
                    onDelete()
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Rounded.Close, "Delete", tint = ApexOnSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun NutritionEmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Restaurant, null, tint = ApexOnSurfaceVariant, modifier = Modifier.size(40.dp))
            Text("No food logged today", style = MaterialTheme.typography.titleSmall, color = ApexOnSurfaceVariant)
            Text("Tap + to log your first meal", style = MaterialTheme.typography.bodySmall, color = ApexOnSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Add Food Entry Bottom Sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFoodEntrySheet(
    state: NutritionState,
    onSearch: (String) -> Unit,
    onSelectFood: (FoodCacheEntity) -> Unit,
    onAddEntry: (FoodCacheEntity, Double, String?) -> Unit,
    onCreateCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = rememberApexHaptic()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ApexSurface,
        contentColor = ApexOnSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .heightIn(min = 400.dp),
        ) {
            if (state.selectedFood != null) {
                // Confirm entry
                ConfirmFoodEntry(
                    food = state.selectedFood,
                    onConfirm = { servings, mealType -> onAddEntry(state.selectedFood, servings, mealType) },
                    onBack = onDismiss,
                )
            } else {
                // Search
                Text("Add Food", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = ApexOnSurface)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search foods...", color = ApexOnSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = ApexOnSurfaceVariant) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ApexNutritionAccent,
                        unfocusedBorderColor = ApexOutline,
                        cursorColor = ApexNutritionAccent,
                        focusedTextColor = ApexOnSurface,
                        unfocusedTextColor = ApexOnSurface,
                    ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Create custom food button
                TextButton(onClick = {
                    haptic.click()
                    onCreateCustom()
                }) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Create custom food", color = ApexNutritionAccent)
                }

                // Results
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.searchResults) { food ->
                        FoodSearchResult(food = food, onClick = {
                            haptic.click()
                            onSelectFood(food)
                        })
                    }
                    if (state.searchResults.isEmpty() && !state.isSearching) {
                        item {
                            Text(
                                if (state.searchQuery.isBlank()) "Recent foods will appear here"
                                else "No foods found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ApexOnSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodSearchResult(food: FoodCacheEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(food.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = ApexOnSurface)
            val subtitle = listOfNotNull(
                food.brand,
                food.servingSizeG?.let { "${it.toInt()}g serving" },
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ApexOnSurfaceVariant)
            }
        }
        Text("${food.calories.toInt()} kcal", style = MaterialTheme.typography.labelMedium, color = ApexNutritionAccent)
    }
}

@Composable
private fun ConfirmFoodEntry(
    food: FoodCacheEntity,
    onConfirm: (Double, String?) -> Unit,
    onBack: () -> Unit,
) {
    var servings by remember { mutableStateOf("1") }
    var selectedMeal by remember { mutableStateOf<String?>(null) }
    val haptic = rememberApexHaptic()
    val servingsNum = servings.toDoubleOrNull() ?: 1.0

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = ApexOnSurface)
            }
            Text(food.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = ApexOnSurface)
        }

        // Macro preview
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MacroChip("Cal", (food.calories * servingsNum).toInt().toString(), ApexNutritionAccent)
            MacroChip("P", "${((food.proteinG ?: 0.0) * servingsNum).toInt()}g", Color(0xFF60A5FA))
            MacroChip("C", "${((food.carbsG ?: 0.0) * servingsNum).toInt()}g", Color(0xFFA78BFA))
            MacroChip("F", "${((food.fatG ?: 0.0) * servingsNum).toInt()}g", Color(0xFFFBBF24))
        }

        // Servings
        OutlinedTextField(
            value = servings,
            onValueChange = { servings = it },
            label = { Text("Servings") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ApexNutritionAccent,
                unfocusedBorderColor = ApexOutline,
                cursorColor = ApexNutritionAccent,
                focusedTextColor = ApexOnSurface,
                unfocusedTextColor = ApexOnSurface,
                focusedLabelColor = ApexNutritionAccent,
                unfocusedLabelColor = ApexOnSurfaceVariant,
            ),
        )

        // Meal type
        Text("Meal", style = MaterialTheme.typography.labelMedium, color = ApexOnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("breakfast", "lunch", "dinner", "snack").forEach { meal ->
                FilterChip(
                    selected = selectedMeal == meal,
                    onClick = {
                        haptic.tick()
                        selectedMeal = if (selectedMeal == meal) null else meal
                    },
                    label = { Text(meal.replaceFirstChar { it.uppercase() }) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ApexNutritionAccent.copy(alpha = 0.2f),
                        selectedLabelColor = ApexNutritionAccent,
                    ),
                )
            }
        }

        Button(
            onClick = {
                haptic.click()
                onConfirm(servingsNum, selectedMeal)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ApexNutritionAccent),
        ) {
            Text("Log Food", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun MacroChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Create Custom Food Dialog
// ---------------------------------------------------------------------------

@Composable
private fun CreateFoodDialog(
    onConfirm: (name: String, calories: Double, protein: Double?, carbs: Double?, fat: Double?, brand: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ApexSurface,
        titleContentColor = ApexOnSurface,
        textContentColor = ApexOnSurface,
        title = { Text("Create Custom Food") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ApexNutritionAccent,
                    unfocusedBorderColor = ApexOutline,
                    cursorColor = ApexNutritionAccent,
                    focusedTextColor = ApexOnSurface,
                    unfocusedTextColor = ApexOnSurface,
                    focusedLabelColor = ApexNutritionAccent,
                    unfocusedLabelColor = ApexOnSurfaceVariant,
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Brand") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                OutlinedTextField(value = calories, onValueChange = { calories = it }, label = { Text("Calories *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = protein, onValueChange = { protein = it }, label = { Text("Protein (g)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = fieldColors)
                    OutlinedTextField(value = carbs, onValueChange = { carbs = it }, label = { Text("Carbs (g)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = fieldColors)
                    OutlinedTextField(value = fat, onValueChange = { fat = it }, label = { Text("Fat (g)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = fieldColors)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cal = calories.toDoubleOrNull() ?: return@Button
                    if (name.isBlank()) return@Button
                    onConfirm(
                        name.trim(),
                        cal,
                        protein.toDoubleOrNull(),
                        carbs.toDoubleOrNull(),
                        fat.toDoubleOrNull(),
                        brand.ifBlank { null },
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ApexNutritionAccent),
                enabled = name.isNotBlank() && calories.toDoubleOrNull() != null,
            ) {
                Text("Create", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ApexOnSurfaceVariant) }
        },
    )
}

// ---------------------------------------------------------------------------
// Edit Food Entry Dialog
// ---------------------------------------------------------------------------

@Composable
private fun EditFoodEntryDialog(
    entry: FoodEntryCacheEntity,
    onSave: (servings: Double, mealType: PatchField<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var servings by remember { mutableStateOf(entry.servings.toString()) }
    var selectedMeal by remember { mutableStateOf(entry.mealType) }
    val haptic = rememberApexHaptic()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ApexNutritionAccent,
        unfocusedBorderColor = ApexOutline,
        cursorColor = ApexNutritionAccent,
        focusedTextColor = ApexOnSurface,
        unfocusedTextColor = ApexOnSurface,
        focusedLabelColor = ApexNutritionAccent,
        unfocusedLabelColor = ApexOnSurfaceVariant,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ApexSurface,
        titleContentColor = ApexOnSurface,
        textContentColor = ApexOnSurface,
        title = { Text("Edit Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(entry.foodName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = ApexOnSurface)

                OutlinedTextField(
                    value = servings,
                    onValueChange = { servings = it },
                    label = { Text("Servings") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )

                Text("Meal", style = MaterialTheme.typography.labelMedium, color = ApexOnSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("breakfast", "lunch", "dinner", "snack").forEach { meal ->
                        FilterChip(
                            selected = selectedMeal == meal,
                            onClick = {
                                haptic.tick()
                                selectedMeal = if (selectedMeal == meal) null else meal
                            },
                            label = { Text(meal.replaceFirstChar { it.uppercase() }) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ApexNutritionAccent.copy(alpha = 0.2f),
                                selectedLabelColor = ApexNutritionAccent,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = servings.toDoubleOrNull() ?: return@Button
                    if (s <= 0) return@Button
                    // Build tri-state meal type
                    val mealPatch = when {
                        selectedMeal == entry.mealType -> PatchField.Unchanged
                        selectedMeal == null -> PatchField.SetNull
                        else -> PatchField.SetValue(selectedMeal!!)
                    }
                    haptic.click()
                    onSave(s, mealPatch)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ApexNutritionAccent),
                enabled = servings.toDoubleOrNull()?.let { it > 0 } ?: false,
            ) {
                Text("Save", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ApexOnSurfaceVariant) }
        },
    )
}
