package com.autofarm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.autofarm.core.Step
import com.autofarm.core.StepType
import com.autofarm.engine.StepRecorder
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.imageio.ImageIO

@Composable
fun RecorderScreen() {
    val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    var recorder by remember { mutableStateOf<StepRecorder?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var startUrl by remember { mutableStateOf("https://") }
    var recordedEvents by remember { mutableStateOf<List<StepRecorder.RecordedEvent>>(emptyList()) }
    var generatedJson by remember { mutableStateOf("") }
    var lastSnapshot by remember { mutableStateOf<ByteArray?>(null) }
    var snapshotLabel by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(recordedEvents.size) {
        if (recordedEvents.isNotEmpty()) {
            listState.animateScrollToItem(recordedEvents.lastIndex)
        }
    }

    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {

        // ── Left column: controls + event log ──────────────────────────────
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Text("Step Recorder", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)

            AppCard(Modifier.fillMaxWidth()) {
                SectionHeader("HOW IT WORKS")
                Text(
                    "1. Enter the URL and click Start Recording.\n" +
                    "2. A browser window opens — interact normally (click, type, navigate).\n" +
                    "3. Every action is captured and listed below in real time.\n" +
                    "4. Click Stop to finish. Copy the generated JSON into the Run tab.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }

            AppCard(Modifier.fillMaxWidth()) {
                LabeledField("Start URL") {
                    OutlinedTextField(
                        value = startUrl,
                        onValueChange = { startUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        enabled = !isRecording
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val rec = StepRecorder()
                            recorder = rec
                            isRecording = true
                            recordedEvents = emptyList()
                            generatedJson = ""
                            scope.launch(Dispatchers.IO) {
                                rec.start(startUrl)
                                for (event in rec.eventChannel) {
                                    withContext(Dispatchers.Main) {
                                        recordedEvents = recordedEvents + event
                                        val steps = rec.getRecordedSteps()
                                        generatedJson = json.encodeToString(steps)
                                    }
                                }
                                withContext(Dispatchers.Main) { isRecording = false }
                            }
                        },
                        enabled = !isRecording,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFEF4444))
                        Spacer(Modifier.width(6.dp))
                        Text("Start Recording")
                    }

                    if (isRecording) {
                        OutlinedButton(onClick = {
                            recorder?.stop()
                            recorder = null
                            isRecording = false
                            val steps = recorder?.getRecordedSteps() ?: emptyList()
                            if (steps.isNotEmpty()) generatedJson = json.encodeToString(steps)
                        }) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop")
                        }

                        OutlinedButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                val snap = recorder?.takeSnapshot()
                                withContext(Dispatchers.Main) { lastSnapshot = snap }
                            }
                        }) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Snapshot")
                        }
                    }

                    if (recordedEvents.isNotEmpty() && !isRecording) {
                        TextButton(onClick = {
                            recordedEvents = emptyList()
                            generatedJson = ""
                        }) { Text("Clear") }
                    }
                }
            }

            if (isRecording) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFFEF4444))
                    Text("Recording… interact in the browser window", color = Color(0xFFEF4444), fontSize = 13.sp)
                }
            }

            if (recordedEvents.isNotEmpty()) {
                AppCard(Modifier.fillMaxWidth().weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("CAPTURED EVENTS (${recordedEvents.size})")
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(recordedEvents.indices.toList()) { i ->
                            val ev = recordedEvents[i]
                            RecordedEventRow(i + 1, ev)
                        }
                    }
                }
            }
        }

        // ── Right column: generated JSON + snapshot ─────────────────────────
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Text("Generated JSON", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)

            if (generatedJson.isNotBlank()) {
                AppCard(Modifier.fillMaxWidth().weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("COPY INTO RUN TAB → STEPS")
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
                        onValueChange = { generatedJson = it },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        readOnly = false
                    )
                }
            } else {
                AppCard(Modifier.fillMaxWidth().weight(1f)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Start recording to see generated JSON here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Screenshot / snapshot preview
            lastSnapshot?.let { snap ->
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("LAST SNAPSHOT")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                // Save snapshot to image store so it can be referenced in CLICK_IMAGE steps
                                val dir = File(System.getProperty("user.home") + "/.autofarm/images")
                                dir.mkdirs()
                                val name = "snapshot_${System.currentTimeMillis()}.png"
                                File(dir, name).writeBytes(snap)
                            }) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save to image store", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    val bitmap = runCatching {
                        val img = ImageIO.read(snap.inputStream())
                        img?.let {
                            val bi = java.awt.image.BufferedImage(it.width, it.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                            bi.graphics.drawImage(it, 0, 0, null)
                            org.jetbrains.skia.Image.makeFromEncoded(snap).toComposeImageBitmap()
                        }
                    }.getOrNull()
                    bitmap?.let {
                        Image(bitmap = it, contentDescription = "Snapshot", modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordedEventRow(index: Int, event: StepRecorder.RecordedEvent) {
    val (icon, color) = when (event.type) {
        "navigate" -> Icons.Default.Language to Color(0xFF6EE7B7)
        "click" -> Icons.Default.TouchApp to Color(0xFF93C5FD)
        "fill" -> Icons.Default.Edit to Color(0xFFFCD34D)
        "keypress" -> Icons.Default.Keyboard to Color(0xFFC4B5FD)
        else -> Icons.Default.Circle to Color(0xFF94A3B8)
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("$index", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(22.dp))
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(event.description ?: event.type, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
            val detail = listOfNotNull(
                event.selector?.let { "sel: $it" },
                event.value?.let { "val: ${it.take(30)}" },
                event.url?.let { "url: ${it.take(50)}" }
            ).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
}
