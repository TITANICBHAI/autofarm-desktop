package com.autofarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autofarm.core.StepStatus
import com.autofarm.engine.SerializableStepResult
import com.autofarm.engine.StoredRun
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Side-by-side diff of two runs.
 * Highlights steps whose status differs between runs in yellow/red.
 */
@Composable
fun RunDiffScreen(
    runA: StoredRun,
    runB: StoredRun
) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss") }

    val stepsA = remember(runA) {
        runCatching { json.decodeFromString<List<SerializableStepResult>>(runA.stepResultsJson) }.getOrElse { emptyList() }
    }
    val stepsB = remember(runB) {
        runCatching { json.decodeFromString<List<SerializableStepResult>>(runB.stepResultsJson) }.getOrElse { emptyList() }
    }
    val maxSteps = maxOf(stepsA.size, stepsB.size)

    // Summary: count changed steps
    var changed = 0
    for (i in 0 until maxSteps) {
        val a = stepsA.getOrNull(i)
        val b = stepsB.getOrNull(i)
        if (a?.status != b?.status) changed++
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Run Diff", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)

        // Summary bar
        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RunSummaryChip("Run A", runA, fmt)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CompareArrows, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Text(
                        if (changed == 0) "Identical" else "$changed step(s) differ",
                        color = if (changed == 0) Color(0xFF6EE7B7) else Color(0xFFFCD34D),
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace
                    )
                }
                RunSummaryChip("Run B", runB, fmt)
            }
        }

        // Step table
        AppCard(Modifier.fillMaxWidth().weight(1f)) {
            SectionHeader("STEP-BY-STEP DIFF  —  yellow = status differs  |  red = fail in either")

            // Column headers
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("#", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(24.dp))
                Text("Step", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(1.5f))
                Text("Run A", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("Run B", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(1f))
            }
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                itemsIndexed((0 until maxSteps).toList()) { _, i ->
                    val a = stepsA.getOrNull(i)
                    val b = stepsB.getOrNull(i)
                    val aStatus = runCatching { StepStatus.valueOf(a?.status ?: "PENDING") }.getOrElse { StepStatus.PENDING }
                    val bStatus = runCatching { StepStatus.valueOf(b?.status ?: "PENDING") }.getOrElse { StepStatus.PENDING }
                    val statusDiffers = aStatus != bStatus
                    val hasFail = aStatus == StepStatus.FAIL || bStatus == StepStatus.FAIL

                    val rowBg = when {
                        hasFail && statusDiffers -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f)
                        statusDiffers           -> Color(0xFFFCD34D).copy(alpha = 0.12f)
                        else                    -> Color.Transparent
                    }

                    Row(
                        Modifier.fillMaxWidth().background(rowBg).padding(vertical = 5.dp, horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${i + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(24.dp))
                        Text(
                            a?.stepDescription ?: b?.stepDescription ?: "—",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1.5f),
                            maxLines = 2
                        )
                        DiffStatusCell(a, aStatus, Modifier.weight(1f))
                        DiffStatusCell(b, bStatus, Modifier.weight(1f))
                    }
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
private fun DiffStatusCell(step: SerializableStepResult?, status: StepStatus, modifier: Modifier) {
    Column(modifier.padding(end = 8.dp)) {
        StatusBadge(status)
        if (step?.message?.isNotBlank() == true) {
            Text(step.message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 2, lineHeight = 13.sp)
        }
        step?.durationMs?.let {
            Text("${it}ms", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun RunSummaryChip(label: String, run: StoredRun, fmt: SimpleDateFormat) {
    val status = runCatching { StepStatus.valueOf(run.overallStatus) }.getOrElse { StepStatus.PENDING }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatusBadge(status)
        Text(run.generatedEmail, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
        Text(fmt.format(Date(run.startedAt)), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
