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
import androidx.compose.ui.window.Dialog
import com.autofarm.core.*
import com.autofarm.engine.FlowLibrary
import com.autofarm.engine.FlowManager
import com.autofarm.engine.LoopProgress
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun RunScreen(
    flowManager: FlowManager,
    appConfig: AppConfig,
    mailConfig: MailConfig,
    onRunComplete: (RunResult) -> Unit = {}
) {
    val json = Json { prettyPrint = true; ignoreUnknownKeys = true; isLenient = true }

    // Flow config state
    var baseUrl by remember { mutableStateOf("https://staging.example.com") }
    var emailDomain by remember { mutableStateOf("yourdomain.com") }
    var flowName by remember { mutableStateOf("My Flow") }
    var stepsJson by remember { mutableStateOf(DEFAULT_FLOW_JSON) }
    var loopCount by remember { mutableStateOf("1") }
    var jsonError by remember { mutableStateOf<String?>(null) }
    // Proxy settings
    var proxyServer by remember { mutableStateOf("") }
    var proxyUser by remember { mutableStateOf("") }
    var proxyPass by remember { mutableStateOf("") }
    var showProxyFields by remember { mutableStateOf(false) }
    // BYPASS_AUTH endpoint
    var bypassAuthEndpoint by remember { mutableStateOf("") }

    // Current flow object (rebuilt on changes)
    val currentFlow by remember(flowName, baseUrl, emailDomain, stepsJson, loopCount, proxyServer, proxyUser, proxyPass, bypassAuthEndpoint) {
        derivedStateOf {
            val steps = runCatching { json.decodeFromString<List<Step>>(stepsJson) }.getOrElse { emptyList() }
            FlowConfig(
                name = flowName, baseUrl = baseUrl, emailDomain = emailDomain, steps = steps,
                loopCount = loopCount.toIntOrNull() ?: 1,
                proxyServer = proxyServer, proxyUsername = proxyUser, proxyPassword = proxyPass,
                bypassAuthEndpoint = bypassAuthEndpoint
            )
        }
    }

    // Run state
    var isRunning by remember { mutableStateOf(false) }
    var liveStepResults by remember { mutableStateOf<List<StepResult>>(emptyList()) }
    var lastResults by remember { mutableStateOf<List<RunResult>>(emptyList()) }
    var loopProgress by remember { mutableStateOf<LoopProgress?>(null) }

    // PAUSE_FOR_INPUT state
    var pausePrompt by remember { mutableStateOf<String?>(null) }
    var pauseInput by remember { mutableStateOf("") }
    var pauseContinuation by remember { mutableStateOf<CompletableDeferred<String>?>(null) }

    val scope = rememberCoroutineScope()

    // Pause dialog
    pausePrompt?.let { prompt ->
        Dialog(onDismissRequest = {}) {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Input Required", style = MaterialTheme.typography.titleMedium)
                    Text(prompt, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = pauseInput,
                        onValueChange = { pauseInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Your input") }
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = {
                            pauseContinuation?.complete(pauseInput)
                            pausePrompt = null
                            pauseInput = ""
                            pauseContinuation = null
                        }) { Text("Continue Run") }
                    }
                }
            }
        }
    }

    Row(Modifier.fillMaxSize()) {

        // ── Flow Library sidebar ──────────────────────────────────────────────
        Surface(modifier = Modifier.width(220.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            FlowLibraryPanel(
                currentFlow = currentFlow,
                onLoad = { flow ->
                    flowName = flow.name
                    baseUrl = flow.baseUrl
                    emailDomain = flow.emailDomain
                    loopCount = flow.loopCount.toString()
                    proxyServer = flow.proxyServer
                    proxyUser = flow.proxyUsername
                    proxyPass = flow.proxyPassword
                    bypassAuthEndpoint = flow.bypassAuthEndpoint
                    stepsJson = try { json.encodeToString(flow.steps) } catch (_: Exception) { DEFAULT_FLOW_JSON }
                    jsonError = null
                },
                onSaveCurrent = { FlowLibrary.save(currentFlow) }
            )
        }

        Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        // ── Main content ──────────────────────────────────────────────────────
        Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {

            // Left: config + JSON editor
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                Text("Run Flow", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)

                AppCard(Modifier.fillMaxWidth()) {
                    SectionHeader("FLOW CONFIGURATION")
                    LabeledField("Flow name") {
                        OutlinedTextField(value = flowName, onValueChange = { flowName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                    Spacer(Modifier.height(8.dp))
                    LabeledField("Base URL (must match an allowed host in Settings)") {
                        OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabeledField("Email domain") {
                            OutlinedTextField(value = emailDomain, onValueChange = { emailDomain = it }, modifier = Modifier.weight(1f), singleLine = true, textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
                        }
                        LabeledField("Loop (repeat N times)") {
                            OutlinedTextField(
                                value = loopCount,
                                onValueChange = { loopCount = it.filter { c -> c.isDigit() }.ifEmpty { "1" } },
                                modifier = Modifier.width(90.dp),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // Proxy / advanced expander
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Advanced", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { showProxyFields = !showProxyFields }, modifier = Modifier.height(28.dp)) {
                            Icon(if (showProxyFields) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (showProxyFields) "Hide" else "Proxy / BYPASS_AUTH", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (showProxyFields) {
                        Spacer(Modifier.height(4.dp))
                        LabeledField("Proxy server (blank = no proxy)") {
                            OutlinedTextField(value = proxyServer, onValueChange = { proxyServer = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                                placeholder = { Text("http://proxy.example.com:8080") })
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LabeledField("Proxy username") {
                                OutlinedTextField(value = proxyUser, onValueChange = { proxyUser = it }, modifier = Modifier.weight(1f), singleLine = true)
                            }
                            LabeledField("Proxy password") {
                                OutlinedTextField(value = proxyPass, onValueChange = { proxyPass = it }, modifier = Modifier.weight(1f), singleLine = true,
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        LabeledField("BYPASS_AUTH endpoint (your own staging server test-login URL)") {
                            OutlinedTextField(value = bypassAuthEndpoint, onValueChange = { bypassAuthEndpoint = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                                placeholder = { Text("https://staging.example.com/test-login") })
                        }
                    }
                }

                AppCard(Modifier.fillMaxWidth().weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("STEPS (JSON)")
                        TextButton(onClick = { stepsJson = DEFAULT_FLOW_JSON; jsonError = null }) {
                            Text("Reset to template", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedTextField(
                        value = stepsJson,
                        onValueChange = {
                            stepsJson = it
                            jsonError = runCatching {
                                json.decodeFromString<List<Step>>(it)
                                null
                            }.getOrElse { e -> e.message }
                        },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        isError = jsonError != null
                    )
                    jsonError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            val steps = runCatching { json.decodeFromString<List<Step>>(stepsJson) }.getOrElse { emptyList() }
                            if (steps.isEmpty()) { jsonError = "No valid steps"; return@Button }
                            val flow = currentFlow.copy(steps = steps)

                            isRunning = true
                            liveStepResults = List(steps.size) { i -> StepResult(steps[i], StepStatus.PENDING) }
                            lastResults = emptyList()
                            loopProgress = null

                            flowManager.startRun(
                                flow = flow,
                                scope = scope,
                                onStepUpdate = { idx, result ->
                                    liveStepResults = liveStepResults.toMutableList().also { list ->
                                        if (idx < list.size) list[idx] = result
                                    }
                                },
                                onRunComplete = { result ->
                                    lastResults = lastResults + result
                                    liveStepResults = result.stepResults.ifEmpty { liveStepResults }
                                    onRunComplete(result)   // notify Main (tray, etc.)
                                },
                                onLoopProgress = { progress -> loopProgress = progress },
                                onInputRequired = { prompt ->
                                    val deferred = CompletableDeferred<String>()
                                    withContext(Dispatchers.Main) {
                                        pausePrompt = prompt
                                        pauseInput = ""
                                        pauseContinuation = deferred
                                    }
                                    deferred.await()
                                }
                            ).also { job -> scope.launch { job.join(); isRunning = false } }
                        },
                        enabled = !isRunning && jsonError == null,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (appConfig.dryRun) "Dry Run" else if ((loopCount.toIntOrNull() ?: 1) > 1) "Run ×${loopCount}" else "Run")
                    }

                    if (isRunning) {
                        OutlinedButton(onClick = { flowManager.cancelAll(); isRunning = false }, modifier = Modifier.height(44.dp)) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cancel")
                        }
                    }

                    TextButton(onClick = { FlowLibrary.save(currentFlow) }, modifier = Modifier.height(44.dp)) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            }

            // Right: live output
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Live Output", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)

                // Loop progress bar
                loopProgress?.let { progress ->
                    AppCard(Modifier.fillMaxWidth()) {
                        val total = progress.total
                        val done = progress.iteration
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Loop $done / $total", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, modifier = Modifier.width(90.dp))
                            LinearProgressIndicator(progress = { done.toFloat() / total.coerceAtLeast(1) }, modifier = Modifier.weight(1f).height(6.dp))
                            val passCount = lastResults.count { it.overallStatus == StepStatus.PASS }
                            val failCount = lastResults.count { it.overallStatus == StepStatus.FAIL }
                            Text("✓$passCount ✗$failCount", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // Last run summary
                lastResults.lastOrNull()?.let { result ->
                    AppCard(Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(result.overallStatus)
                            Text(result.generatedEmail, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            val dur = (result.finishedAt ?: System.currentTimeMillis()) - result.startedAt
                            Text("${dur}ms", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        result.errorMessage?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                }

                if (isRunning && loopProgress == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text("Running…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }

                if (liveStepResults.isNotEmpty()) {
                    AppCard(Modifier.fillMaxWidth().weight(1f)) {
                        SectionHeader("STEP RESULTS")
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(liveStepResults) { index, result ->
                                StepRow(index, result)
                                if (index < liveStepResults.lastIndex) Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }
    }
}
