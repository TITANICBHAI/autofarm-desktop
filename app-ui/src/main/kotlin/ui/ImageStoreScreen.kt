package com.autofarm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autofarm.engine.ImageMatcher
import org.jetbrains.skia.Image as SkiaImage
import java.io.File

/**
 * Image Store — lets the user:
 *  • Import PNG/JPG reference images (drag-drop or file dialog)
 *  • View all stored images with their filenames
 *  • Copy the step JSON snippet for CLICK_IMAGE / WAIT_FOR_IMAGE
 *  • Delete images they no longer need
 *
 * Stored at: ~/.autofarm/images/
 */
@Composable
fun ImageStoreScreen() {
    val imageDir = remember { File(System.getProperty("user.home") + "/.autofarm/images").also { it.mkdirs() } }
    var images by remember { mutableStateOf(loadImages(imageDir)) }
    var selected by remember { mutableStateOf<File?>(null) }
    var snackMessage by remember { mutableStateOf<String?>(null) }
    var confidence by remember { mutableStateOf("0.8") }

    LaunchedEffect(snackMessage) {
        if (snackMessage != null) {
            kotlinx.coroutines.delay(2500)
            snackMessage = null
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Image Store", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Import reference images", java.awt.FileDialog.LOAD)
                    dialog.file = "*.png;*.jpg;*.jpeg"
                    dialog.isMultipleMode = true
                    dialog.isVisible = true
                    dialog.files?.forEach { f ->
                        val stored = ImageMatcher.storeImage(f)
                        snackMessage = "Imported: $stored"
                    }
                    images = loadImages(imageDir)
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import Images")
                }
                OutlinedButton(onClick = { images = loadImages(imageDir) }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }

        AppCard(Modifier.fillMaxWidth()) {
            SectionHeader("HOW TO USE IMAGE STEPS")
            Text(
                "1. Take a screenshot of any button or element you want to interact with.\n" +
                "2. Crop it tightly around just that element and save as PNG.\n" +
                "3. Import it here — it gets saved to ~/.autofarm/images/.\n" +
                "4. Copy the generated JSON snippet below into your flow's step list.\n\n" +
                "The engine takes a screenshot of the page, finds your reference image using\n" +
                "template matching, and clicks its center. Confidence 0.8 = 80% match required.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontFamily = FontFamily.Default
            )
        }

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {

            // Image grid
            Column(Modifier.weight(1.2f).fillMaxHeight()) {
                if (images.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No images yet. Import some!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(images) { file ->
                            ImageCard(
                                file = file,
                                isSelected = selected == file,
                                onClick = { selected = file },
                                onDelete = {
                                    file.delete()
                                    if (selected == file) selected = null
                                    images = loadImages(imageDir)
                                }
                            )
                        }
                    }
                }
            }

            // Detail / snippet panel
            Column(Modifier.weight(0.8f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                selected?.let { file ->
                    AppCard(Modifier.fillMaxWidth()) {
                        SectionHeader("SELECTED: ${file.name}")

                        val bitmap = runCatching {
                            SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
                        }.getOrNull()
                        bitmap?.let {
                            Image(bitmap = it, contentDescription = file.name, modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp))
                        }

                        Spacer(Modifier.height(12.dp))
                        LabeledField("Match confidence (0.0 – 1.0)") {
                            OutlinedTextField(
                                value = confidence,
                                onValueChange = { confidence = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }

                    val conf = confidence.toDoubleOrNull() ?: 0.8

                    listOf(
                        "CLICK_IMAGE" to "Click element matching this image",
                        "WAIT_FOR_IMAGE" to "Wait until this image appears",
                        "ASSERT_IMAGE" to "Fail if image not found",
                        "HOVER_IMAGE" to "Hover over matched element"
                    ).forEach { (stepType, desc) ->
                        val snippet = """{ "type": "$stepType", "imageFile": "${file.name}", "confidence": $conf, "description": "$desc" }"""
                        AppCard(Modifier.fillMaxWidth().clickable {
                            val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                            cb.setContents(java.awt.datatransfer.StringSelection(snippet), null)
                            snackMessage = "Copied $stepType snippet!"
                        }) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stepType, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                } ?: run {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select an image to see JSON snippets", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        snackMessage?.let { msg ->
            Surface(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(msg, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun ImageCard(file: File, isSelected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier
            .aspectRatio(1.2f)
            .clickable(onClick = onClick)
            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(Modifier.fillMaxSize()) {
            val bitmap = runCatching {
                SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
            }.getOrNull()
            bitmap?.let {
                Image(bitmap = it, contentDescription = file.name, modifier = Modifier.fillMaxSize().padding(4.dp))
            } ?: Icon(Icons.Default.BrokenImage, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(4.dp)) {
                Text(file.name, color = Color.White, fontSize = 9.sp, maxLines = 1, fontFamily = FontFamily.Monospace)
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFCA5A5), modifier = Modifier.size(14.dp))
            }
        }
    }
}

private fun loadImages(dir: File): List<File> =
    dir.listFiles { f -> f.extension.lowercase() in listOf("png", "jpg", "jpeg") }
        ?.sortedByDescending { it.lastModified() } ?: emptyList()
