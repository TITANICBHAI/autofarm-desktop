package com.autofarm.core

import kotlinx.serialization.Serializable

@Serializable
enum class StepType {
    NAVIGATE,
    CLICK,
    FILL,
    WAIT_FOR_SELECTOR,
    WAIT_FOR_OTP,
    ASSERT_TEXT,
    SELECT_OPTION,
    SCROLL_TO,
    PRESS_KEY,
    SCREENSHOT,
    DELETE_ACCOUNT_API,
    // Image-matching step types — supply a cropped reference PNG
    CLICK_IMAGE,        // finds reference image on screen, clicks its center
    WAIT_FOR_IMAGE,     // waits until reference image appears on screen
    ASSERT_IMAGE,       // fails if reference image is NOT visible
    HOVER_IMAGE,        // moves mouse over matched region without clicking
    PAUSE_FOR_INPUT,    // pauses run and shows a dialog asking user to type a value
    BYPASS_AUTH         // POSTs to a test-login endpoint on your own staging server
}

@Serializable
data class Step(
    val type: StepType,
    val selector: String? = null,
    val value: String? = null,
    val timeoutSec: Int = 30,
    val optional: Boolean = false,
    val description: String? = null,
    // Image-matching fields
    val imageFile: String? = null,       // path to reference PNG (relative to ~/.autofarm/images/ or absolute)
    val confidence: Double = 0.8,        // 0..1 match threshold (0.8 = 80%)
    val offsetX: Int = 0,                // click offset from matched center
    val offsetY: Int = 0
)

@Serializable
data class FlowConfig(
    val name: String = "My Flow",
    val baseUrl: String = "https://staging.example.com",
    val emailDomain: String = "yourdomain.com",
    val steps: List<Step> = emptyList(),
    val loopCount: Int = 1,              // how many times to repeat this flow end-to-end
    val loopDelayMs: Long = 2000,        // delay between loop iterations
    // Per-run proxy (leave blank to use no proxy)
    val proxyServer: String = "",        // e.g. "http://proxy.example.com:8080"
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    // BYPASS_AUTH endpoint (your own staging server test-login route)
    val bypassAuthEndpoint: String = ""  // e.g. "https://staging.example.com/test-login"
)

@Serializable
data class MailConfig(
    val imapHost: String = "imap.yourdomain.com",
    val imapPort: Int = 993,
    val imapUser: String = "catchall@yourdomain.com",
    val imapPassword: String = "",
    val otpRegex: String = "\\b(\\d{4,8})\\b",
    val useSSL: Boolean = true
)

@Serializable
data class HumanConfig(
    // Typing
    val typingMinMs: Long = 60,
    val typingMaxMs: Long = 140,
    val wordPauseExtraMs: Long = 180,
    val thinkPauseMinMs: Long = 300,
    val thinkPauseMaxMs: Long = 900,
    val enableTypos: Boolean = true,
    val typoRate: Double = 0.02,          // 2% chance per character
    val typoRealisationMinMs: Long = 200,
    val typoRealisationMaxMs: Long = 600,
    // Mouse
    val mouseMoveSteps: Int = 30,
    val mouseMoveStepMinMs: Long = 5,
    val mouseMoveStepMaxMs: Long = 18,
    val bezierSpread: Double = 80.0,      // control-point spread in px
    val jitterPx: Double = 1.2,
    val preClickPauseMinMs: Long = 40,
    val preClickPauseMaxMs: Long = 140
)

@Serializable
data class AppConfig(
    val allowedHostPatterns: List<String> = listOf("staging.", "localhost", "127.0.0.1", "dev."),
    val concurrency: Int = 1,
    val delayBetweenRunsMs: Long = 2000,
    val headless: Boolean = false,
    val dryRun: Boolean = false,
    val mail: MailConfig = MailConfig(),
    val deleteApiEndpoint: String = "",
    val humanLike: Boolean = false,
    val human: HumanConfig = HumanConfig()
)

enum class StepStatus { PENDING, RUNNING, PASS, FAIL, SKIPPED }

data class StepResult(
    val step: Step,
    val status: StepStatus,
    val message: String = "",
    val screenshotPath: String? = null,
    val durationMs: Long = 0
)

data class RunResult(
    val id: Long = 0,
    val generatedEmail: String,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val stepResults: List<StepResult> = emptyList(),
    val overallStatus: StepStatus = StepStatus.PENDING,
    val errorMessage: String? = null
)

const val PLACEHOLDER_EMAIL = "\${GENERATED_EMAIL}"
const val PLACEHOLDER_OTP = "\${OTP}"
const val PLACEHOLDER_BASE_URL = "\${BASE_URL}"

val DEFAULT_FLOW_JSON = """
[
  { "type": "NAVIGATE", "value": "${'$'}{BASE_URL}/signup", "description": "Open signup page" },
  { "type": "FILL", "selector": "#email", "value": "${'$'}{GENERATED_EMAIL}", "description": "Enter generated email" },
  { "type": "FILL", "selector": "#password", "value": "TestPass123!", "description": "Enter password" },
  { "type": "CLICK", "selector": "#signup-btn", "description": "Submit signup" },
  { "type": "WAIT_FOR_OTP", "timeoutSec": 60, "description": "Wait for OTP email" },
  { "type": "FILL", "selector": "#otp-field", "value": "${'$'}{OTP}", "description": "Enter OTP" },
  { "type": "CLICK", "selector": "#verify-btn", "description": "Verify OTP" },
  { "type": "WAIT_FOR_SELECTOR", "selector": "#dashboard", "timeoutSec": 15, "description": "Wait for dashboard" },
  { "type": "CLICK", "selector": "#delete-account-btn", "optional": true, "description": "Delete account" },
  { "type": "CLICK", "selector": "#confirm-delete", "optional": true, "description": "Confirm deletion" }
]
""".trimIndent()
