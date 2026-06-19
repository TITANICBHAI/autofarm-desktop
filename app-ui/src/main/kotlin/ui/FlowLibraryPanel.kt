package com.autofarm.ui

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autofarm.core.FlowConfig
import com.autofarm.engine.FlowLibrary
import java.io.File

/**
 * Sidebar panel listing saved flows.
 * Supports: load, save-current, rename, delete, import from file, export to file.
 */
@Composable
fun FlowLibraryPanel(
    currentFlow: FlowConfig,
    onLoad: (FlowConfig) -> Unit,
    onSaveCurrent: () -> Unit,
    modifier: Modifier = Modifier
) {
    var flows by remember { mutableStateOf(FlowLibrary.list()) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var snack by remember { mutableStateOf<String?>(null) }

    fun refresh() { flows = FlowLibrary.list() }

    LaunchedEffect(snack) {
        if (snack != null) { kotlinx.coroutines.delay(2000); snack = null }
    }

    // Confirm delete dialog
    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"$name\"?") },
            confirmButton = {
                TextButton(onClick = {
                    FlowLibrary.delete(name); refresh(); confirmDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }

    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Flows", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                // Import from file
                IconButton(onClick = {
                    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Import Flow", java.awt.FileDialog.LOAD)
                    dialog.file = "*.json"
                    dialog.isVisible = true
                    val file = dialog.files?.firstOrNull()
                    if (file != null) {
                        val flow = FlowLibrary.loadFile(file)
                        if (flow != null) { onLoad(flow); FlowLibrary.save(flow); refresh(); snack = "Imported: ${flow.name}" }
                        else snack = "Invalid flow file"
                    }
                }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.FileUpload, contentDescription = "Import", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Save current
                IconButton(onClick = { onSaveCurrent(); refresh(); snack = "Saved: ${currentFlow.name}" }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        if (flows.isEmpty()) {
            Text("No saved flows.\nClick ↑ save to store the current one.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
        } else {
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(flows) { name ->
                    val isActive = name == currentFlow.name
                    if (renaming == name) {
                        // Inline rename field
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                modifier = Modifier.weight(1f).height(36.dp),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                            )
                            IconButton(onClick = {
                                if (renameText.isNotBlank()) {
                                    FlowLibrary.rename(name, renameText.trim())
                                    refresh()
                                    snack = "Renamed to ${renameText.trim()}"
                                }
                                renaming = null
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { renaming = null }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                FlowLibrary.load(name)?.let { onLoad(it) }
                            },
                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Article, contentDescription = null, modifier = Modifier.size(12.dp), tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                Text(name, fontSize = 12.sp, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                // Export
                                IconButton(onClick = {
                                    val flow = FlowLibrary.load(name) ?: return@IconButton
                                    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Export Flow", java.awt.FileDialog.SAVE)
                                    dialog.file = "$name.json"
                                    dialog.isVisible = true
                                    val dir = dialog.directory; val file = dialog.file
                                    if (dir != null && file != null) {
                                        FlowLibrary.export(flow, File(dir, file))
                                        snack = "Exported: $file"
                                    }
                                }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.FileDownload, contentDescription = "Export", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                // Rename
                                IconButton(onClick = { renaming = name; renameText = name }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                // Delete
                                IconButton(onClick = { confirmDelete = name }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }

        snack?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
