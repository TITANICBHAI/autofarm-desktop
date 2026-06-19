package com.autofarm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autofarm.core.AppConfig
import com.autofarm.core.HumanConfig
import com.autofarm.core.MailConfig
import com.autofarm.mail.ImapClient
import kotlinx.coroutines.*

@Composable
fun SettingsScreen(
    appConfig: AppConfig,
    mailConfig: MailConfig,
    onSave: (AppConfig, MailConfig) -> Unit
) {
    var allowedHosts by remember { mutableStateOf(appConfig.allowedHostPatterns.joinToString("\n")) }
    var concurrency by remember { mutableStateOf(appConfig.concurrency.toString()) }
    var headless by remember { mutableStateOf(appConfig.headless) }
    var dryRun by remember { mutableStateOf(appConfig.dryRun) }
    var delayMs by remember { mutableStateOf(appConfig.delayBetweenRunsMs.toString()) }
    var deleteApiEndpoint by remember { mutableStateOf(appConfig.deleteApiEndpoint) }

    // Human-like behavior
    var humanLike by remember { mutableStateOf(appConfig.humanLike) }
    var typingMinMs by remember { mutableStateOf(appConfig.human.typingMinMs.toString()) }
    var typingMaxMs by remember { mutableStateOf(appConfig.human.typingMaxMs.toString()) }
    var enableTypos by remember { mutableStateOf(appConfig.human.enableTypos) }
    var typoRate by remember { mutableStateOf(appConfig.human.typoRate.toString()) }
    var mouseMoveSteps by remember { mutableStateOf(appConfig.human.mouseMoveSteps.toString()) }
    var bezierSpread by remember { mutableStateOf(appConfig.human.bezierSpread.toString()) }
    var jitterPx by remember { mutableStateOf(appConfig.human.jitterPx.toString()) }
    var thinkPauseMinMs by remember { mutableStateOf(appConfig.human.thinkPauseMinMs.toString()) }
    var thinkPauseMaxMs by remember { mutableStateOf(appConfig.human.thinkPauseMaxMs.toString()) }

    // IMAP
    var imapHost by remember { mutableStateOf(mailConfig.imapHost) }
    var imapPort by remember { mutableStateOf(mailConfig.imapPort.toString()) }
    var imapUser by remember { mutableStateOf(mailConfig.imapUser) }
    var imapPassword by remember { mutableStateOf(mailConfig.imapPassword) }
    var otpRegex by remember { mutableStateOf(mailConfig.otpRegex) }
    var useSSL by remember { mutableStateOf(mailConfig.useSSL) }

    var connectionTestResult by remember { mutableStateOf<String?>(null) }
    var testingConnection by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)

        // ── Safety ────────────────────────────────────────────────────────────
        AppCard(Modifier.fillMaxWidth()) {
            SectionHeader("SAFETY")
            LabeledField("Allowed host patterns (one per line — run is blocked if BASE_URL doesn't match)") {
                OutlinedTextField(
                    value = allowedHosts,
                    onValueChange = { allowedHosts = it },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    textStyle = LocalTextStyle.current.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    placeholder = { Text("staging.\nlocalhost\n127.0.0.1", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledField("Concurrency (parallel runs)") {
                    OutlinedTextField(
                        value = concurrency,
                        onValueChange = { concurrency = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.width(120.dp),
                        singleLine = true
                    )
                }
                LabeledField("Delay between runs (ms)") {
                    OutlinedTextField(
                        value = delayMs,
                        onValueChange = { delayMs = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.width(140.dp),
                        singleLine = true
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = headless, onCheckedChange = { headless = it })
                    Text("Headless browser", color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = dryRun, onCheckedChange = { dryRun = it })
                    Text("Dry run (log only, no clicks)", color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(8.dp))
            LabeledField("Admin delete-account API endpoint (optional)") {
                OutlinedTextField(
                    value = deleteApiEndpoint,
                    onValueChange = { deleteApiEndpoint = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://staging.example.com/admin/delete-user", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }
        }

        // ── Human-like Behavior ───────────────────────────────────────────────
        AppCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column {
                    SectionHeader("HUMAN-LIKE BEHAVIOR")
                    Text(
                        "Bezier-curve mouse movement + per-key typing delays + occasional typos.\nUse on staging sites that have behavioral analysis.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = humanLike, onCheckedChange = { humanLike = it })
            }

            if (humanLike) {
                Spacer(Modifier.height(16.dp))
                SectionHeader("TYPING")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField("Min delay/key (ms)") {
                        OutlinedTextField(
                            value = typingMinMs,
                            onValueChange = { typingMinMs = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.width(120.dp), singleLine = true
                        )
                    }
                    LabeledField("Max delay/key (ms)") {
                        OutlinedTextField(
                            value = typingMaxMs,
                            onValueChange = { typingMaxMs = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.width(120.dp), singleLine = true
                        )
                    }
                    LabeledField("Think pause min (ms)") {
                        OutlinedTextField(
                            value = thinkPauseMinMs,
                            onValueChange = { thinkPauseMinMs = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.width(140.dp), singleLine = true
                        )
                    }
                    LabeledField("Think pause max (ms)") {
                        OutlinedTextField(
                            value = thinkPauseMaxMs,
                            onValueChange = { thinkPauseMaxMs = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.width(140.dp), singleLine = true
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = enableTypos, onCheckedChange = { enableTypos = it })
                        Text("Simulate typos", color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (enableTypos) {
                        LabeledField("Typo rate (0.0–0.1)") {
                            OutlinedTextField(
                                value = typoRate,
                                onValueChange = { typoRate = it },
                                modifier = Modifier.width(120.dp), singleLine = true,
                                placeholder = { Text("0.02", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                SectionHeader("MOUSE MOVEMENT")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField("Curve steps") {
                        OutlinedTextField(
                            value = mouseMoveSteps,
                            onValueChange = { mouseMoveSteps = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.width(100.dp), singleLine = true
                        )
                    }
                    LabeledField("Bezier spread (px)") {
                        OutlinedTextField(
                            value = bezierSpread,
                            onValueChange = { bezierSpread = it },
                            modifier = Modifier.width(120.dp), singleLine = true
                        )
                    }
                    LabeledField("Jitter (px)") {
                        OutlinedTextField(
                            value = jitterPx,
                            onValueChange = { jitterPx = it },
                            modifier = Modifier.width(100.dp), singleLine = true
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Current profile summary:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val minMs = typingMinMs.toLongOrNull() ?: 60
                        val maxMs = typingMaxMs.toLongOrNull() ?: 140
                        val steps = mouseMoveSteps.toIntOrNull() ?: 30
                        Text(
                            "• Typing: ${minMs}–${maxMs}ms per key  (~${(minMs + maxMs) / 2 * 8}ms for an 8-char password)\n" +
                            "• Mouse: $steps interpolation steps along Bezier curve  (${bezierSpread}px spread)\n" +
                            "• Jitter: ±${jitterPx}px per step\n" +
                            "• Typos: ${if (enableTypos) "${(typoRate.toDoubleOrNull() ?: 0.02) * 100}% chance per character" else "disabled"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // ── Email / IMAP ──────────────────────────────────────────────────────
        AppCard(Modifier.fillMaxWidth()) {
            SectionHeader("EMAIL / IMAP (for OTP reading)")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("IMAP host") {
                    OutlinedTextField(
                        value = imapHost, onValueChange = { imapHost = it },
                        modifier = Modifier.width(260.dp), singleLine = true
                    )
                }
                LabeledField("Port") {
                    OutlinedTextField(
                        value = imapPort, onValueChange = { imapPort = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.width(80.dp), singleLine = true
                    )
                }
                Column(verticalArrangement = Arrangement.Bottom) {
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = useSSL, onCheckedChange = { useSSL = it })
                        Text("SSL", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("Catch-all IMAP username") {
                    OutlinedTextField(
                        value = imapUser, onValueChange = { imapUser = it },
                        modifier = Modifier.width(240.dp), singleLine = true
                    )
                }
                LabeledField("Password") {
                    OutlinedTextField(
                        value = imapPassword, onValueChange = { imapPassword = it },
                        modifier = Modifier.width(200.dp), singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LabeledField("OTP regex (capture group 1 = the code)") {
                OutlinedTextField(
                    value = otpRegex, onValueChange = { otpRegex = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Button(
                    onClick = {
                        testingConnection = true
                        connectionTestResult = null
                        scope.launch {
                            val client = ImapClient(MailConfig(imapHost, imapPort.toIntOrNull() ?: 993, imapUser, imapPassword, otpRegex, useSSL))
                            val res = withContext(Dispatchers.IO) { client.testConnection() }
                            connectionTestResult = res.getOrElse { "Error: ${it.message}" }
                            testingConnection = false
                        }
                    },
                    enabled = !testingConnection
                ) {
                    if (testingConnection) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Test Connection")
                }
                connectionTestResult?.let {
                    Text(it, color = if (it.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ── Save ──────────────────────────────────────────────────────────────
        Button(
            onClick = {
                val humanCfg = HumanConfig(
                    typingMinMs = typingMinMs.toLongOrNull() ?: 60,
                    typingMaxMs = typingMaxMs.toLongOrNull() ?: 140,
                    thinkPauseMinMs = thinkPauseMinMs.toLongOrNull() ?: 300,
                    thinkPauseMaxMs = thinkPauseMaxMs.toLongOrNull() ?: 900,
                    enableTypos = enableTypos,
                    typoRate = typoRate.toDoubleOrNull() ?: 0.02,
                    mouseMoveSteps = mouseMoveSteps.toIntOrNull() ?: 30,
                    bezierSpread = bezierSpread.toDoubleOrNull() ?: 80.0,
                    jitterPx = jitterPx.toDoubleOrNull() ?: 1.2
                )
                onSave(
                    AppConfig(
                        allowedHostPatterns = allowedHosts.lines().map { it.trim() }.filter { it.isNotBlank() },
                        concurrency = concurrency.toIntOrNull() ?: 1,
                        delayBetweenRunsMs = delayMs.toLongOrNull() ?: 2000,
                        headless = headless,
                        dryRun = dryRun,
                        mail = MailConfig(imapHost, imapPort.toIntOrNull() ?: 993, imapUser, imapPassword, otpRegex, useSSL),
                        deleteApiEndpoint = deleteApiEndpoint,
                        humanLike = humanLike,
                        human = humanCfg
                    ),
                    MailConfig(imapHost, imapPort.toIntOrNull() ?: 993, imapUser, imapPassword, otpRegex, useSSL)
                )
            },
            modifier = Modifier.height(44.dp)
        ) {
            Text("Save Settings")
        }
    }
}
