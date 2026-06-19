package com.autofarm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.autofarm.core.StepStatus
import com.autofarm.engine.FlowManager
import com.autofarm.engine.SerializableStepResult
import com.autofarm.engine.StoredRun
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun HistoryScreen(flowManager: FlowManager) {
    var runs by remember { mutableStateOf(flowManager.loadRunHistory()) }
    var selectedRun by remember { mutableStateOf<StoredRun?>(null) }
    var diffRunA by remember { mutableStateOf<StoredRun?>(null) }
    var showDiff by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var screenshotPath by remember { mutableStateOf<String?>(null) }

    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss") }
    val json = remember { Json { ignoreUnknownKeys = true } }

    // Full-screen diff view
    if (showDiff && diffRunA != null && selectedRun != null && diffRunA!!.id != selectedRun!!.id) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { showDiff = false }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Back to History")
                }
                Text("Comparing two runs", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            RunDiffScreen(runA = diffRunA!!, runB = selectedRun!!)
        }
        return
    }

    // Screenshot viewer dialog
    screenshotPath?.let { path ->
        Dialog(onDismissRequest = { screenshotPath = null }) {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(File(path).name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            TextButton(onClick = { runCatching { java.awt.Desktop.getDesktop().open(File(path)) } }) { Text("Open in OS viewer") }
                            IconButton(onClick = { screenshotPath = null }) { Icon(Icons.Default.Close, contentDescription = "Close") }
                        }
                    }
                    val bitmap = runCatching { SkiaImage.makeFromEncoded(File(path).readBytes()).toComposeImageBitmap() }.getOrNull()
                    if (bitmap != null) {
                        Image(bitmap = bitmap, contentDescription = "Screenshot", modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp))
                    } else {
                        Text("Could not load image: $path", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all history?") },
            text = { Text("This will permanently delete all run records.") },
            confirmButton = {
                TextButton(onClick = {
                    flowManager.clearHistory(); runs = emptyList(); selectedRun = null; showClearDialog = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {

        // ── Run list ─────────────────────────────────────────────────────────
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Run History", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    if (diffRunA != null) {
                        Text("Diff mode: Run A pinned (${diffRunA!!.generatedEmail.take(20)}…) — select Run B then click Compare", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { runs = flowManager.loadRunHistory() }) { Text("Refresh") }
                    if (runs.size >= 2) {
                        if (diffRunA == null) {
                            OutlinedButton(onClick = { diffRunA = selectedRun }) {
                                Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Pin A for Diff")
                            }
                        } else {
                            OutlinedButton(onClick = { diffRunA = null }) { Text("Cancel Diff") }
                            if (selectedRun != null && selectedRun!!.id != diffRunA!!.id) {
                                Button(onClick = { showDiff = true }) {
                                    Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Compare →")
                                }
                            }
                        }
                    }
                    if (runs.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (runs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No runs yet. Start one from the Run tab.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                AppCard(Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(runs) { run ->
                            val isSelected = selectedRun?.id == run.id
                            val isPinnedA = diffRunA?.id == run.id
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { selectedRun = run },
                                color = when {
                                    isPinnedA  -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else       -> MaterialTheme.colorScheme.surface
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (isPinnedA) {
                                        Surface(color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) {
                                            Text("A", fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                        }
                                    }
                                    StatusBadge(runCatching { StepStatus.valueOf(run.overallStatus) }.getOrElse { StepStatus.PENDING })
                                    Column(Modifier.weight(1f)) {
                                        Text(run.generatedEmail, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                                        Text(fmt.format(Date(run.startedAt)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    run.finishedAt?.let { fin ->
                                        Text("${fin - run.startedAt}ms", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }

        // ── Run detail ───────────────────────────────────────────────────────
        selectedRun?.let { run ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Run Detail", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)

                AppCard(Modifier.fillMaxWidth()) {
                    Text(run.generatedEmail, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    Text(fmt.format(Date(run.startedAt)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    run.finishedAt?.let { Text("Duration: ${it - run.startedAt}ms", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    run.errorMessage?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }

                val stepResults = runCatching { json.decodeFromString<List<SerializableStepResult>>(run.stepResultsJson) }.getOrElse { emptyList() }

                if (stepResults.isNotEmpty()) {
                    AppCard(Modifier.fillMaxWidth().weight(1f)) {
                        SectionHeader("STEPS — click 📷 to view failure screenshot")
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(stepResults.indices.toList()) { index ->
                                val sr = stepResults[index]
                                val status = runCatching { StepStatus.valueOf(sr.status) }.getOrElse { StepStatus.PENDING }
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(24.dp))
                                    StatusBadge(status)
                                    Column(Modifier.weight(1f)) {
                                        Text(sr.stepDescription, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                                        if (sr.message.isNotBlank()) Text(sr.message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Text("${sr.durationMs}ms", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                                    if (sr.screenshotPath != null && File(sr.screenshotPath).exists()) {
                                        IconButton(onClick = { screenshotPath = sr.screenshotPath }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Image, contentDescription = "View screenshot", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }
    }
}
