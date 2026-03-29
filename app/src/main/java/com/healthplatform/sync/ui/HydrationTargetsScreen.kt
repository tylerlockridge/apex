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

data class HydrationTargetsState(
    val isLoading: Boolean = true,
    val targetMl: String = "",
    val isSaved: Boolean = false,
)

class HydrationTargetsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = NutritionRepository(application)
    private val _state = MutableStateFlow(HydrationTargetsState())
    val state: StateFlow<HydrationTargetsState> = _state

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val target = repo.getHydrationTarget()
            repo.refreshHydrationTarget()
            val refreshed = repo.getHydrationTarget()
            val t = refreshed ?: target
            _state.update {
                it.copy(
                    isLoading = false,
                    targetMl = t?.targetMl?.toString() ?: "2500",
                )
            }
        }
    }

    fun save() {
        val ml = _state.value.targetMl.toIntOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.setHydrationTarget(ml)
            NutritionSyncWorker.runOnce(getApplication())
            _state.update { it.copy(isSaved = true) }
        }
    }

    fun updateTarget(v: String) { _state.update { it.copy(targetMl = v, isSaved = false) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydrationTargetsScreen(
    viewModel: HydrationTargetsViewModel = viewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = rememberApexHaptic()

    val isValid = state.targetMl.toIntOrNull()?.let { it > 0 } ?: false

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Hydration Target", fontWeight = FontWeight.Bold) },
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
                "Set your daily water intake target in milliliters. A common guideline is 2000-3000 ml per day.",
                style = MaterialTheme.typography.bodyMedium,
                color = ApexOnSurfaceVariant,
            )

            OutlinedTextField(
                value = state.targetMl, onValueChange = { viewModel.updateTarget(it) },
                label = { Text("Daily target (ml)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ApexHydrationAccent,
                    unfocusedBorderColor = ApexOutline,
                    cursorColor = ApexHydrationAccent,
                    focusedTextColor = ApexOnSurface,
                    unfocusedTextColor = ApexOnSurface,
                    focusedLabelColor = ApexHydrationAccent,
                    unfocusedLabelColor = ApexOnSurfaceVariant,
                ),
            )

            // Quick presets
            Text("PRESETS", style = MaterialTheme.typography.labelSmall, color = ApexOnSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2000, 2500, 3000, 3500).forEach { preset ->
                    OutlinedButton(
                        onClick = {
                            haptic.tick()
                            viewModel.updateTarget(preset.toString())
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexHydrationAccent),
                    ) {
                        Text("${preset}ml")
                    }
                }
            }

            Button(
                onClick = {
                    haptic.click()
                    viewModel.save()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = isValid && !state.isSaved,
                colors = ButtonDefaults.buttonColors(containerColor = ApexHydrationAccent),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (state.isSaved) {
                    Icon(Icons.Rounded.Check, null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saved", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Text("Save Target", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
