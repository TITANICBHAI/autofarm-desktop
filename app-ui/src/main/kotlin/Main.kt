import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.autofarm.AppPrefs
import com.autofarm.TrayNotifier
import com.autofarm.buildAppIcon
import com.autofarm.engine.FlowLibrary
import com.autofarm.engine.FlowManager
import com.autofarm.engine.ImageMatcher
import com.autofarm.engine.PlaywrightSetup
import com.autofarm.ui.*
import kotlinx.coroutines.launch

fun main() = application {
    ImageMatcher.init()

    val (initApp, initMail) = AppPrefs.load()
    var appConfig by remember { mutableStateOf(initApp) }
    var mailConfig by remember { mutableStateOf(initMail) }

    val flowManager = remember(appConfig, mailConfig) { FlowManager(appConfig, mailConfig) }

    // Build the programmatic app icon once
    val appIcon = remember { buildAppIcon() }

    // System tray (safe no-op if unsupported)
    LaunchedEffect(Unit) { TrayNotifier.init("AutoFarm") }

    // ── Playwright browser auto-install ───────────────────────────────────────
    var pwSetupDone by remember { mutableStateOf(false) }
    var pwLogs by remember { mutableStateOf(listOf<String>()) }
    var pwError by remember { mutableStateOf<String?>(null) }

    // ── First-run setup wizard ────────────────────────────────────────────────
    // Show if IMAP password is blank (never configured)
    var showWizard by remember { mutableStateOf(initMail.imapPassword.isBlank()) }

    Window(
        onCloseRequest = { TrayNotifier.dispose(); exitApplication() },
        title = "AutoFarm — Browser Automation",
        state = rememberWindowState(size = DpSize(1440.dp, 900.dp)),
        icon = appIcon
    ) {
        AppTheme {
            val scope = rememberCoroutineScope()

            // Playwright check on first composition
            LaunchedEffect(Unit) {
                scope.launch {
                    val result = PlaywrightSetup.ensureInstalled { line -> pwLogs = pwLogs + line }
                    pwError = result.error
                    pwSetupDone = true
                }
            }

            // First-run setup wizard overlay
            if (showWizard) {
                SetupWizard(
                    initialMail = mailConfig,
                    initialApp = appConfig,
                    onComplete = { newApp, newMail ->
                        appConfig = newApp
                        mailConfig = newMail
                        AppPrefs.save(newApp, newMail)
                        showWizard = false
                    },
                    onSkip = { showWizard = false }
                )
            }

            // Playwright install progress screen (blocks until done)
            if (!pwSetupDone && !showWizard) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(48.dp)) {
                        CircularProgressIndicator()
                        Text("Setting up Chromium browser (one-time, ~150 MB)…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                        if (pwLogs.isNotEmpty()) {
                            Surface(color = MaterialTheme.colorScheme.surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                                Text(
                                    pwLogs.takeLast(6).joinToString("\n"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
                return@AppTheme
            }

            // ── Main UI ───────────────────────────────────────────────────────
            var selectedTab by remember { mutableStateOf(0) }

            data class TabDef(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

            val tabs = listOf(
                TabDef("Run",     Icons.Default.PlayArrow),
                TabDef("Editor",  Icons.Default.Edit),
                TabDef("Record",  Icons.Default.FiberManualRecord),
                TabDef("Images",  Icons.Default.Image),
                TabDef("History", Icons.Default.History),
                TabDef("Settings",Icons.Default.Settings)
            )

            // Steps shared between Editor and Run tabs
            var sharedSteps by remember { mutableStateOf(listOf<com.autofarm.core.Step>()) }

            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "AF",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    tabs.forEachIndexed { index, tab ->
                        NavigationRailItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Setup wizard re-open button
                    IconButton(onClick = { showWizard = true }, modifier = Modifier.padding(bottom = 4.dp)) {
                        Icon(Icons.Default.Help, contentDescription = "Setup wizard", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Browser install error indicator
                    pwError?.let {
                        IconButton(onClick = {}, modifier = Modifier.padding(bottom = 8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = "Browser install issue: $it", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (selectedTab) {
                        0 -> RunScreen(
                            flowManager = flowManager,
                            appConfig = appConfig,
                            mailConfig = mailConfig,
                            onRunComplete = { result ->
                                val status = if (result.overallStatus == com.autofarm.core.StepStatus.PASS) "✓ PASS" else "✗ FAIL"
                                TrayNotifier.notify(
                                    title = "AutoFarm run finished — $status",
                                    message = result.generatedEmail,
                                    type = if (result.overallStatus == com.autofarm.core.StepStatus.PASS)
                                        java.awt.TrayIcon.MessageType.INFO
                                    else java.awt.TrayIcon.MessageType.ERROR
                                )
                            }
                        )
                        1 -> StepEditorScreen(
                            initialSteps = sharedSteps,
                            onApply = { steps -> sharedSteps = steps; selectedTab = 0 }
                        )
                        2 -> RecorderScreen()
                        3 -> ImageStoreScreen()
                        4 -> HistoryScreen(flowManager)
                        5 -> SettingsScreen(
                            appConfig = appConfig,
                            mailConfig = mailConfig,
                            onSave = { newApp, newMail ->
                                appConfig = newApp
                                mailConfig = newMail
                                AppPrefs.save(newApp, newMail)
                            }
                        )
                    }
                }
            }
        }
    }
}
