package com.autofarm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.autofarm.core.AppConfig
import com.autofarm.core.MailConfig

/**
 * First-run setup wizard shown when IMAP password is blank (meaning
 * the user has never configured email or explicitly skipped).
 *
 * Covers the two most common blockers for new users:
 *   1. IMAP catch-all credentials
 *   2. Allowed host list (so RunScreen doesn't refuse their staging URL)
 */
@Composable
fun SetupWizard(
    initialMail: MailConfig,
    initialApp: AppConfig,
    onComplete: (AppConfig, MailConfig) -> Unit,
    onSkip: () -> Unit
) {
    var page by remember { mutableStateOf(0) }

    // Mail fields
    var imapHost by remember { mutableStateOf(initialMail.imapHost) }
    var imapUser by remember { mutableStateOf(initialMail.imapUser) }
    var imapPass by remember { mutableStateOf(initialMail.imapPassword) }
    var imapSsl by remember { mutableStateOf(initialMail.useSSL) }
    var showPass by remember { mutableStateOf(false) }

    // App fields
    var allowedHosts by remember { mutableStateOf(initialApp.allowedHostPatterns.joinToString("\n")) }

    Dialog(onDismissRequest = {}) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(520.dp)
        ) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

                // Header
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column {
                        Text("Welcome to AutoFarm", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("Step ${page + 1} of 2 — quick setup", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }

                LinearProgressIndicator(
                    progress = { (page + 1) / 2f },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                when (page) {

                    // ── Page 0: IMAP ─────────────────────────────────────────
                    0 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Email / IMAP catch-all", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "AutoFarm generates a random email address for each run and reads the OTP from your catch-all mailbox via IMAP. Enter your credentials below.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp
                            )
                        }

                        LabeledField("IMAP host") {
                            OutlinedTextField(value = imapHost, onValueChange = { imapHost = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                                placeholder = { Text("imap.yourdomain.com") })
                        }
                        LabeledField("Catch-all mailbox address") {
                            OutlinedTextField(value = imapUser, onValueChange = { imapUser = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                                placeholder = { Text("catchall@yourdomain.com") })
                        }
                        LabeledField("IMAP password") {
                            OutlinedTextField(
                                value = imapPass,
                                onValueChange = { imapPass = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPass = !showPass }) {
                                        Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                                    }
                                }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = imapSsl, onCheckedChange = { imapSsl = it })
                            Text("Use SSL / port 993", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // ── Page 1: Allowed hosts ────────────────────────────────
                    1 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Allowed staging hosts", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "AutoFarm only opens URLs that match one of these patterns (safety guard). Add your staging domain below — one pattern per line.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp
                            )
                        }
                        LabeledField("Host patterns (one per line, substring match)") {
                            OutlinedTextField(
                                value = allowedHosts,
                                onValueChange = { allowedHosts = it },
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                                placeholder = { Text("staging.example.com\nlocalhost\n127.0.0.1\ndev.") }
                            )
                        }
                        Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("Tip: use \"staging.\" to match any subdomain, or the full hostname for stricter control.", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                        }
                    }
                }

                // Navigation
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onSkip) { Text("Skip for now") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (page > 0) OutlinedButton(onClick = { page-- }) { Text("Back") }
                        Button(onClick = {
                            if (page < 1) {
                                page++
                            } else {
                                val newMail = initialMail.copy(imapHost = imapHost, imapUser = imapUser, imapPassword = imapPass, useSSL = imapSsl)
                                val patterns = allowedHosts.lines().map { it.trim() }.filter { it.isNotBlank() }
                                val newApp = initialApp.copy(allowedHostPatterns = patterns.ifEmpty { initialApp.allowedHostPatterns })
                                onComplete(newApp, newMail)
                            }
                        }) {
                            Text(if (page < 1) "Next" else "Finish")
                        }
                    }
                }
            }
        }
    }
}
