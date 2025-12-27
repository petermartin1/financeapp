package com.financeapp.ui.components.forms

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financeapp.ui.animations.slideInAnimation
import com.financeapp.ui.animations.slideOutAnimation
import com.financeapp.ui.animations.SlideDirection

/**
 * Step in a multi-step wizard
 */
data class WizardStep(
    val title: String,
    val isComplete: (Any?) -> Boolean = { true },
    val content: @Composable (Any?, (Any?) -> Unit) -> Unit
)

/**
 * Multi-step wizard component
 *
 * @param steps List of wizard steps
 * @param onComplete Callback when wizard is completed
 * @param onDismiss Callback when wizard is dismissed
 * @param modifier Modifier for styling
 * @param title Wizard title
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MultiStepWizard(
    steps: List<WizardStep>,
    onComplete: (List<Any?>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Wizard"
) {
    var currentStepIndex by remember { mutableStateOf(0) }
    val stepData = remember { mutableStateMapOf<Int, Any?>() }

    val currentStep = steps[currentStepIndex]
    val isFirstStep = currentStepIndex == 0
    val isLastStep = currentStepIndex == steps.size - 1
    val canProceed = currentStep.isComplete(stepData[currentStepIndex])

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Progress indicator
                WizardProgressIndicator(
                    currentStep = currentStepIndex,
                    totalSteps = steps.size,
                    stepTitles = steps.map { it.title }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Step content with animation
                AnimatedContent(
                    targetState = currentStepIndex,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInAnimation(SlideDirection.Left, 300) togetherWith
                                    slideOutAnimation(SlideDirection.Left, 300)
                        } else {
                            slideInAnimation(SlideDirection.Right, 300) togetherWith
                                    slideOutAnimation(SlideDirection.Right, 300)
                        }
                    },
                    label = "wizard_step"
                ) { stepIndex ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp)
                    ) {
                        steps[stepIndex].content(stepData[stepIndex]) { newData ->
                            stepData[stepIndex] = newData
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Navigation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back button
                    if (!isFirstStep) {
                        TextButton(
                            onClick = { currentStepIndex-- }
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back")
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                    }

                    // Next/Finish button
                    Button(
                        onClick = {
                            if (isLastStep) {
                                val allData = steps.indices.map { stepData[it] }
                                onComplete(allData)
                            } else {
                                currentStepIndex++
                            }
                        },
                        enabled = canProceed
                    ) {
                        Text(if (isLastStep) "Finish" else "Next")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            if (isLastStep) Icons.Default.Check else Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Progress indicator showing current step
 */
@Composable
private fun WizardProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    stepTitles: List<String>
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Progress bar
        LinearProgressIndicator(
            progress = { (currentStep + 1) / totalSteps.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Step indicator text
        Text(
            text = "Step ${currentStep + 1} of $totalSteps: ${stepTitles[currentStep]}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Step dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalSteps) { index ->
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = when {
                        index < currentStep -> MaterialTheme.colorScheme.primary
                        index == currentStep -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {}

                if (index < totalSteps - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }
}
