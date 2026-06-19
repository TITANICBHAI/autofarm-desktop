package com.autofarm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autofarm.core.Step
import com.autofarm.core.StepType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Visual step editor — each step is a card with inline fields.
 * Supports: add, remove, reorder (up/down), edit all fields, export JSON.
 */
@Composable
fun StepEditorScreen(
    initialSteps: List<Step> = emptyList(),
    onApply: (List<Step>) -> Unit = {}
) {
    val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    var steps by remember(initialSteps) { mutableStateOf(initialSteps.toMutableList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var generatedJson by remember(steps) { mutableStateOf(json.encodeToString(steps.toList())) }

    if (showAddDialog) {
        AddStepDialog(
            onAdd = { step -> steps = (steps + step).toMutableList(); showAddDialog = false },
            onDismiss = { showAddDialog = false }
        )
    }

    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {

        // ── Left: step cards ─────────────────────────────────────────────────
        Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Step Editor", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Step")
                    }
                    Button(onClick = { onApply(steps.toList()); generatedJson = json.encodeToString(steps.toList()) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Apply to Run")
                    }
                }
            }

            if (steps.isEmpty()) {
                AppCard(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No steps yet. Click \"Add Step\" to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(steps) { index, step ->
                        StepCard(
                            index = index,
                            step = step,
                            isFirst = index == 0,
                            isLast = index == steps.lastIndex,
                            onUpdate = { updated -> steps = steps.toMutableList().also { it[index] = updated } },
                            onDelete = { steps = steps.toMutableList().also { it.removeAt(index) } },
                            onMoveUp = {
                                if (index > 0) steps = steps.toMutableList().also {
                                    val tmp = it[index - 1]; it[index - 1] = it[index]; it[index] = tmp
                                }
                            },
                            onMoveDown = {
                                if (index < steps.lastIndex) steps = steps.toMutableList().also {
                                    val tmp = it[index + 1]; it[index + 1] = it[index]; it[index] = tmp
                                }
                            },
                            onDuplicate = { steps = steps.toMutableList().also { list -> list.add(index + 1, step) } }
                        )
                    }
                }
            }
        }

        // ── Right: generated JSON preview ────────────────────────────────────
        Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Generated JSON", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            AppCard(Modifier.fillMaxWidth().weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("PASTE INTO RUN TAB")
                    TextButton(onClick = {
                        val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                        cb.setContents(java.awt.datatransfer.StringSelection(generatedJson), null)
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", style = MaterialTheme.typography.labelSmall)
                    }
                }
                OutlinedTextField(
                    value = generatedJson,
                    onValueChange = {},
                    modifier = Modifier.fillMaxSize(),
                    readOnly = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                )
            }
        }
    }
}

@Composable
private fun StepCard(
    index: Int,
    step: Step,
    isFirst: Boolean,
    isLast: Boolean,
    onUpdate: (Step) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var description by remember(step) { mutableStateOf(step.description ?: "") }
    var selector by remember(step) { mutableStateOf(step.selector ?: "") }
    var value by remember(step) { mutableStateOf(step.value ?: "") }
    var timeoutSec by remember(step) { mutableStateOf(step.timeoutSec.toString()) }
    var optional by remember(step) { mutableStateOf(step.optional) }
    var imageFile by remember(step) { mutableStateOf(step.imageFile ?: "") }
    var confidence by remember(step) { mutableStateOf(step.confidence.toString()) }

    fun pushUpdate() = onUpdate(step.copy(
        description = description.ifBlank { null },
        selector = selector.ifBlank { null },
        value = value.ifBlank { null },
        timeoutSec = timeoutSec.toIntOrNull() ?: 30,
        optional = optional,
        imageFile = imageFile.ifBlank { null },
        confidence = confidence.toDoubleOrNull() ?: 0.8
    ))

    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Index badge
            Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(20.dp))

            // Step type chip
            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) {
                Text(step.type.name, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }

            Text(
                description.ifBlank { selector.ifBlank { value.take(30).ifBlank { "—" } } },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            if (optional) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) {
                    Text("optional", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }

            // Reorder
            IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = onDuplicate, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(26.dp)) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Edit", modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
            }
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField("Description (shown in step list)") {
                    OutlinedTextField(value = description, onValueChange = { description = it; pushUpdate() }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (step.type !in listOf(StepType.NAVIGATE, StepType.WAIT_FOR_OTP, StepType.PRESS_KEY, StepType.SCREENSHOT, StepType.DELETE_ACCOUNT_API,
                            StepType.CLICK_IMAGE, StepType.WAIT_FOR_IMAGE, StepType.ASSERT_IMAGE, StepType.HOVER_IMAGE, StepType.PAUSE_FOR_INPUT)) {
                        LabeledField("CSS selector") {
                            OutlinedTextField(value = selector, onValueChange = { selector = it; pushUpdate() }, modifier = Modifier.width(280.dp), singleLine = true, textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
                        }
                    }
                    if (step.type !in listOf(StepType.CLICK, StepType.WAIT_FOR_SELECTOR, StepType.WAIT_FOR_OTP, StepType.SCREENSHOT,
                            StepType.CLICK_IMAGE, StepType.WAIT_FOR_IMAGE, StepType.ASSERT_IMAGE, StepType.HOVER_IMAGE, StepType.SCROLL_TO)) {
                        LabeledField("Value") {
                            OutlinedTextField(value = value, onValueChange = { value = it; pushUpdate() }, modifier = Modifier.width(280.dp), singleLine = true, textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
                        }
                    }
                }
                if (step.type in listOf(StepType.CLICK_IMAGE, StepType.WAIT_FOR_IMAGE, StepType.ASSERT_IMAGE, StepType.HOVER_IMAGE)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabeledField("imageFile (filename in ~/.autofarm/images/)") {
                            OutlinedTextField(value = imageFile, onValueChange = { imageFile = it; pushUpdate() }, modifier = Modifier.width(260.dp), singleLine = true, textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
                        }
                        LabeledField("Confidence") {
                            OutlinedTextField(value = confidence, onValueChange = { confidence = it; pushUpdate() }, modifier = Modifier.width(100.dp), singleLine = true)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LabeledField("Timeout (sec)") {
                        OutlinedTextField(value = timeoutSec, onValueChange = { timeoutSec = it.filter { c -> c.isDigit() }; pushUpdate() }, modifier = Modifier.width(80.dp), singleLine = true)
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = optional, onCheckedChange = { optional = it; pushUpdate() })
                        Text("Optional (skip on failure)", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddStepDialog(onAdd: (Step) -> Unit, onDismiss: () -> Unit) {
    var selectedType by remember { mutableStateOf(StepType.CLICK) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Step") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose step type:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedType.name,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        StepType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                                onClick = { selectedType = type; expanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(Step(type = selectedType, description = selectedType.name.lowercase().replace('_', ' '))) }) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
