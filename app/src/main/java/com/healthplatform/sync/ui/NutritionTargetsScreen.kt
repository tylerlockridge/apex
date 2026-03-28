package com.healthplatform.sync.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthplatform.sync.data.NutritionRepository
import com.healthplatform.sync.service.NutritionSyncWorker
import com.healthplatform.sync.ui.theme.*
import com.healthplatform.sync.ui.util.rememberApexHaptic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NutritionTargetsState(
    val isLoading: Boolean = true,
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val isSaved: Boolean = false,
)

class NutritionTargetsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = NutritionRepository(application)
    private val _state = MutableStateFlow(NutritionTargetsState())
    val state: StateFlow<NutritionTargetsState> = _state

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val target = repo.getNutritionTarget()
            repo.refreshNutritionTarget()
            val refreshed = repo.getNutritionTarget()
            val t = refreshed ?: target
            _state.update {
                it.copy(
                    isLoading = false,
                    calories = t?.calories?.toString() ?: "",
                    protein = t?.proteinG?.toString() ?: "",
                    carbs = t?.carbsG?.toString() ?: "",
                    fat = t?.fatG?.toString() ?: "",
                )
            }
        }
    }

    fun save() {
        val s = _state.value
        val cal = s.calories.toIntOrNull() ?: return
        val p = s.protein.toIntOrNull() ?: return
        val c = s.carbs.toIntOrNull() ?: return
        val f = s.fat.toIntOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.setNutritionTarget(cal, p, c, f)
            NutritionSyncWorker.runOnce(getApplication())
            _state.update { it.copy(isSaved = true) }
        }
    }

    fun updateCalories(v: String) { _state.update { it.copy(calories = v, isSaved = false) } }
    fun updateProtein(v: String) { _state.update { it.copy(protein = v, isSaved = false) } }
    fun updateCarbs(v: String) { _state.update { it.copy(carbs = v, isSaved = false) } }
    fun updateFat(v: String) { _state.update { it.copy(fat = v, isSaved = false) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionTargetsScreen(
    viewModel: NutritionTargetsViewModel = viewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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

    val isValid = state.calories.toIntOrNull() != null &&
        state.protein.toIntOrNull() != null &&
        state.carbs.toIntOrNull() != null &&
        state.fat.toIntOrNull() != null

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Nutrition Targets", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = ApexOnBackground,
                    navigationIconContentColor = ApexOnBackground,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Set your daily calorie and macro targets. These apply from today forward until you change them.",
                style = MaterialTheme.typography.bodyMedium,
                color = ApexOnSurfaceVariant,
            )

            OutlinedTextField(
                value = state.calories, onValueChange = { viewModel.updateCalories(it) },
                label = { Text("Calories (kcal)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            )
            OutlinedTextField(
                value = state.protein, onValueChange = { viewModel.updateProtein(it) },
                label = { Text("Protein (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            )
            OutlinedTextField(
                value = state.carbs, onValueChange = { viewModel.updateCarbs(it) },
                label = { Text("Carbs (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            )
            OutlinedTextField(
                value = state.fat, onValueChange = { viewModel.updateFat(it) },
                label = { Text("Fat (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            )

            Button(
                onClick = {
                    haptic.click()
                    viewModel.save()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = isValid && !state.isSaved,
                colors = ButtonDefaults.buttonColors(containerColor = ApexNutritionAccent),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (state.isSaved) {
                    Icon(Icons.Rounded.Check, null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saved", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Text("Save Targets", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
