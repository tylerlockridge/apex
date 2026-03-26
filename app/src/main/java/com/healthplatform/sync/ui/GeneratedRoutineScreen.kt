package com.healthplatform.sync.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthplatform.sync.service.GeneratedRoutineExerciseResponse
import com.healthplatform.sync.ui.theme.*
import com.healthplatform.sync.ui.util.rememberApexHaptic

@Composable
fun GeneratedRoutineScreen(
    viewModel: GeneratedRoutineViewModel = viewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = rememberApexHaptic()

    // Trigger generation on first entry — builds readiness payload from SharedPrefs
    LaunchedEffect(Unit) {
        viewModel.generateOnEntry()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { haptic.click(); onBack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = ApexPrimary
                )
            }
            Text(
                text = "Generated Workout",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp
                ),
                color = ApexOnBackground,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isGenerating -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = ApexPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Generating your workout...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ApexOnSurfaceVariant
                        )
                    }
                }
                state.error != null && state.routine == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = ApexStatusRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ApexStatusRed
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { haptic.click(); onBack() },
                            border = androidx.compose.foundation.BorderStroke(1.dp, ApexPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexPrimary)
                        ) {
                            Text("Back to Training")
                        }
                    }
                }
                state.routine != null -> {
                    RoutineContent(
                        state = state,
                        onAccept = { haptic.confirm(); viewModel.acceptRoutine() },
                        onReject = { haptic.reject(); viewModel.rejectRoutine() },
                        onBack = onBack,
                        haptic = haptic
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineContent(
    state: GeneratedRoutineState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onBack: () -> Unit,
    haptic: com.healthplatform.sync.ui.util.ApexHaptic
) {
    val routine = state.routine ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title + status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = ApexSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = routine.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ApexPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (!routine.reasoningSummary.isNullOrBlank()) {
                    Text(
                        text = routine.reasoningSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ApexOnSurface
                    )
                }
                if (routine.readiness != null && routine.readiness.aggregateScore != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            tint = ApexPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Readiness: ${routine.readiness.aggregateScore.toInt()} — ${routine.readiness.label ?: ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ApexOnSurfaceVariant
                        )
                    }
                }
            }
        }

        // Warnings
        if (routine.warnings.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ApexSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = ApexHrvAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Warnings",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ApexHrvAccent
                        )
                    }
                    routine.warnings.forEach { warning ->
                        Text(
                            text = "• $warning",
                            style = MaterialTheme.typography.bodySmall,
                            color = ApexOnSurfaceVariant,
                            modifier = Modifier.padding(start = 26.dp, top = 4.dp)
                        )
                    }
                }
            }
        }

        // Section label
        SectionLabel("EXERCISES")

        // Exercises
        routine.exercises.forEach { exercise ->
            ExerciseCard(exercise)
        }

        // Decision section
        Spacer(modifier = Modifier.height(8.dp))

        if (state.decisionMade != null) {
            DecisionResult(
                decision = state.decisionMade,
                onBack = onBack,
                haptic = haptic
            )
        } else {
            DecisionButtons(
                isDeciding = state.isDeciding,
                onAccept = onAccept,
                onReject = onReject
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ApexPrimary)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = ApexOnSurfaceVariant
        )
    }
}

@Composable
private fun ExerciseCard(exercise: GeneratedRoutineExerciseResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ApexSurfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        if (exercise.flags.contains("two_for_two_increase"))
                            ApexWeightAccent else ApexPrimary
                    )
            )
            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                // Exercise name + ordering
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = exercise.exerciseName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = ApexOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "#${exercise.ordering}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ApexOnSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Targets row — chips with background for scannability
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetChip("${exercise.targetSets} sets")
                    exercise.targetReps?.let { TargetChip("$it reps") }
                    exercise.targetWeightKg?.let { TargetChip("${"%.1f".format(it)} kg") }
                    exercise.targetRpe?.let { TargetChip("RPE ${"%.0f".format(it)}") }
                }

                // Flags
                if (exercise.flags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        exercise.flags.forEach { flag ->
                            val label = when (flag) {
                                "two_for_two_increase" -> "↑ Weight increase"
                                else -> flag.replace("_", " ")
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ApexWeightAccent
                            )
                        }
                    }
                }

                // Reasoning
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = ApexOutline)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = exercise.reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexOnSurfaceVariant
                )

                // Muscle tags
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MuscleTag(exercise.resolvedPrimary, primary = true)
                    exercise.resolvedSecondaries.forEach { MuscleTag(it, primary = false) }
                }
            }
        }
    }
}

@Composable
private fun TargetChip(text: String) {
    Box(
        modifier = Modifier
            .background(
                ApexPrimary.copy(alpha = 0.12f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = ApexPrimary
        )
    }
}

@Composable
private fun MuscleTag(name: String, primary: Boolean) {
    val label = name.replace("_", " ").replaceFirstChar { it.uppercaseChar() }
    Text(
        text = if (primary) label else label,
        style = MaterialTheme.typography.labelSmall,
        color = if (primary) ApexPrimary else ApexOnSurfaceVariant
    )
}

@Composable
private fun DecisionButtons(
    isDeciding: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onAccept,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !isDeciding,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ApexWeightAccent,
                contentColor = ApexOnPrimary
            )
        ) {
            if (isDeciding) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = ApexOnPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Accept Workout",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
        OutlinedButton(
            onClick = onReject,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = !isDeciding,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ApexOnSurfaceVariant.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexOnSurfaceVariant)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reject & Regenerate")
        }
    }
}

@Composable
private fun DecisionResult(
    decision: String,
    onBack: () -> Unit,
    haptic: com.healthplatform.sync.ui.util.ApexHaptic
) {
    val isAccepted = decision == "accepted"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAccepted) ApexWeightAccent.copy(alpha = 0.12f)
            else ApexSurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isAccepted) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                contentDescription = null,
                tint = if (isAccepted) ApexWeightAccent else ApexOnSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isAccepted) "Workout Accepted" else "Workout Rejected",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isAccepted) ApexWeightAccent else ApexOnSurfaceVariant
            )

            if (isAccepted) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = ApexOutline)
                Spacer(modifier = Modifier.height(12.dp))
                // Path B: Manual execution instructions
                Text(
                    text = "Start this workout manually in Hevy",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ApexOnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Apex will use your actual logged performance from Hevy as the source of truth for future recommendations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexOnSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { haptic.click(); onBack() },
                border = androidx.compose.foundation.BorderStroke(1.dp, ApexPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexPrimary)
            ) {
                Text("Back to Training")
            }
        }
    }
}
