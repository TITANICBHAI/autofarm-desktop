package com.autofarm.engine

import com.autofarm.core.*
import com.autofarm.mail.ImapClient
import com.microsoft.playwright.*
import com.microsoft.playwright.options.LoadState
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Paths
import java.util.UUID

class StepRunner(
    private val config: AppConfig,
    private val mailClient: ImapClient,
    private val onStepUpdate: (Int, StepResult) -> Unit = { _, _ -> },
    private val onInputRequired: suspend (prompt: String) -> String = { "" }
) {
    private val screenshotDir = System.getProperty("user.home") + "/.autofarm/screenshots"
    /** Values collected from PAUSE_FOR_INPUT steps; substituted in later steps via \${KEY}. */
    private val capturedInputs = mutableMapOf<String, String>()

    fun isAllowedHost(url: String): Boolean {
        if (config.allowedHostPatterns.isEmpty()) return true
        return config.allowedHostPatterns.any { pattern ->
            url.contains(pattern, ignoreCase = true)
        }
    }

    suspend fun runFlow(
        flow: FlowConfig,
        generatedEmail: String,
        otp: String = ""
    ): RunResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()

        if (!isAllowedHost(flow.baseUrl)) {
            return@withContext RunResult(
                generatedEmail = generatedEmail,
                startedAt = startedAt,
                finishedAt = System.currentTimeMillis(),
                overallStatus = StepStatus.FAIL,
                errorMessage = "BASE_URL '${flow.baseUrl}' is not in the allowed host list. Add it in Settings to proceed."
            )
        }

        java.io.File(screenshotDir).mkdirs()

        val playwright = Playwright.create()
        val browser = playwright.chromium().launch(
            BrowserType.LaunchOptions().setHeadless(config.headless)
        )
        val ctxOptions = Browser.NewContextOptions().setViewportSize(1280, 800)
        if (flow.proxyServer.isNotBlank()) {
            val proxy = com.microsoft.playwright.options.Proxy()
            proxy.server = flow.proxyServer
            if (flow.proxyUsername.isNotBlank()) proxy.username = flow.proxyUsername
            if (flow.proxyPassword.isNotBlank()) proxy.password = flow.proxyPassword
            ctxOptions.setProxy(proxy)
        }
        val context = browser.newContext(ctxOptions)
        val page = context.newPage()

        val stepResults = mutableListOf<StepResult>()
        var overallStatus = StepStatus.PASS
        var capturedOtp = otp

        try {
            flow.steps.forEachIndexed { index, step ->
                val stepStart = System.currentTimeMillis()
                val result = if (config.dryRun) {
                    StepResult(
                        step = step,
                        status = StepStatus.PASS,
                        message = "[DRY RUN] Would execute: ${step.type} ${step.selector ?: ""} ${step.value ?: ""}",
                        durationMs = 0
                    )
                } else {
                    executeStep(step, page, flow.baseUrl, generatedEmail, capturedOtp, flow.emailDomain, flow.bypassAuthEndpoint) { newOtp ->
                        capturedOtp = newOtp
                    }
                }

                val finalResult = result.copy(durationMs = System.currentTimeMillis() - stepStart)
                stepResults.add(finalResult)
                onStepUpdate(index, finalResult)

                if (finalResult.status == StepStatus.FAIL && !step.optional) {
                    overallStatus = StepStatus.FAIL
                    val screenshotPath = takeScreenshot(page, "fail_step${index}_${UUID.randomUUID()}")
                    stepResults[stepResults.lastIndex] = finalResult.copy(screenshotPath = screenshotPath)
                    return@withContext RunResult(
                        generatedEmail = generatedEmail,
                        startedAt = startedAt,
                        finishedAt = System.currentTimeMillis(),
                        stepResults = stepResults,
                        overallStatus = StepStatus.FAIL,
                        errorMessage = "Step ${index + 1} failed: ${finalResult.message}"
                    )
                }
            }
        } catch (e: Exception) {
            overallStatus = StepStatus.FAIL
            val screenshotPath = runCatching { takeScreenshot(page, "exception_${UUID.randomUUID()}") }.getOrNull()
            return@withContext RunResult(
                generatedEmail = generatedEmail,
                startedAt = startedAt,
                finishedAt = System.currentTimeMillis(),
                stepResults = stepResults,
                overallStatus = StepStatus.FAIL,
                errorMessage = e.message ?: "Unknown error"
            ).also {
                stepResults.lastOrNull()?.let { last ->
                    if (screenshotPath != null) {
                        stepResults[stepResults.lastIndex] = last.copy(screenshotPath = screenshotPath)
                    }
                }
            }
        } finally {
            runCatching { context.close() }
            runCatching { browser.close() }
            runCatching { playwright.close() }
        }

        RunResult(
            generatedEmail = generatedEmail,
            startedAt = startedAt,
            finishedAt = System.currentTimeMillis(),
            stepResults = stepResults,
            overallStatus = overallStatus
        )
    }

    private suspend fun executeStep(
        step: Step,
        page: Page,
        baseUrl: String,
        generatedEmail: String,
        currentOtp: String,
        emailDomain: String,
        bypassAuthEndpoint: String = "",
        onOtpReceived: (String) -> Unit
    ): StepResult {
        fun String.interpolate(): String {
            var result = this
                .replace(PLACEHOLDER_BASE_URL, baseUrl)
                .replace(PLACEHOLDER_EMAIL, generatedEmail)
                .replace(PLACEHOLDER_OTP, currentOtp)
                .replace("\${INPUT}", capturedInputs["INPUT"] ?: "")
            // Substitute any named captured inputs: ${KEY}
            capturedInputs.forEach { (key, value) -> result = result.replace("\${$key}", value) }
            return result
        }

        return try {
            when (step.type) {
                StepType.NAVIGATE -> {
                    val url = step.value!!.interpolate()
                    page.navigate(url)
                    page.waitForLoadState(LoadState.NETWORKIDLE)
                    StepResult(step, StepStatus.PASS, "Navigated to $url")
                }
                StepType.CLICK -> {
                    val sel = step.selector!!
                    page.waitForSelector(sel, Page.WaitForSelectorOptions().setTimeout((step.timeoutSec * 1000).toDouble()))
                    if (config.humanLike) {
                        HumanBehavior.humanClickSelector(page, sel, config.human)
                    } else {
                        page.click(sel)
                    }
                    StepResult(step, StepStatus.PASS, "Clicked $sel${if (config.humanLike) " (human)" else ""}")
                }
                StepType.FILL -> {
                    val sel = step.selector!!
                    val value = step.value!!.interpolate()
                    page.waitForSelector(sel, Page.WaitForSelectorOptions().setTimeout((step.timeoutSec * 1000).toDouble()))
                    if (config.humanLike) {
                        HumanBehavior.humanFill(page, sel, value, config.human)
                    } else {
                        page.fill(sel, value)
                    }
                    StepResult(step, StepStatus.PASS, "Filled $sel${if (config.humanLike) " (human)" else ""}")
                }
                StepType.WAIT_FOR_SELECTOR -> {
                    val sel = step.selector!!
                    page.waitForSelector(sel, Page.WaitForSelectorOptions().setTimeout((step.timeoutSec * 1000).toDouble()))
                    StepResult(step, StepStatus.PASS, "Selector found: $sel")
                }
                StepType.WAIT_FOR_OTP -> {
                    val otp = withContext(Dispatchers.IO) {
                        mailClient.pollForOtp(
                            recipientEmail = generatedEmail,
                            timeoutSec = step.timeoutSec
                        )
                    }
                    if (otp != null) {
                        onOtpReceived(otp)
                        StepResult(step, StepStatus.PASS, "OTP received: $otp")
                    } else {
                        StepResult(step, StepStatus.FAIL, "OTP not received within ${step.timeoutSec}s")
                    }
                }
                StepType.ASSERT_TEXT -> {
                    val sel = step.selector!!
                    val expected = step.value!!.interpolate()
                    page.waitForSelector(sel, Page.WaitForSelectorOptions().setTimeout((step.timeoutSec * 1000).toDouble()))
                    val actual = page.textContent(sel) ?: ""
                    if (actual.contains(expected, ignoreCase = true)) {
                        StepResult(step, StepStatus.PASS, "Text verified: '$expected'")
                    } else {
                        StepResult(step, StepStatus.FAIL, "Expected '$expected', got '$actual'")
                    }
                }
                StepType.SELECT_OPTION -> {
                    val sel = step.selector!!
                    val value = step.value!!.interpolate()
                    page.selectOption(sel, value)
                    StepResult(step, StepStatus.PASS, "Selected option '$value' in $sel")
                }
                StepType.SCROLL_TO -> {
                    val sel = step.selector!!
                    page.locator(sel).scrollIntoViewIfNeeded()
                    StepResult(step, StepStatus.PASS, "Scrolled to $sel")
                }
                StepType.PRESS_KEY -> {
                    val key = step.value ?: "Enter"
                    if (step.selector != null) {
                        page.press(step.selector, key)
                    } else {
                        page.keyboard().press(key)
                    }
                    StepResult(step, StepStatus.PASS, "Pressed key '$key'")
                }
                StepType.SCREENSHOT -> {
                    val path = takeScreenshot(page, "manual_${System.currentTimeMillis()}")
                    StepResult(step, StepStatus.PASS, "Screenshot saved", screenshotPath = path)
                }
                StepType.DELETE_ACCOUNT_API -> {
                    if (step.value.isNullOrBlank()) {
                        StepResult(step, StepStatus.SKIPPED, "No DELETE_ACCOUNT_API endpoint configured")
                    } else {
                        val url = step.value.interpolate()
                        val result = java.net.HttpURLConnection.setFollowRedirects(true).let {
                            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                            conn.requestMethod = "DELETE"
                            conn.setRequestProperty("X-Email", generatedEmail)
                            conn.connectTimeout = 10_000
                            conn.readTimeout = 10_000
                            val code = conn.responseCode
                            conn.disconnect()
                            code
                        }
                        StepResult(step, StepStatus.PASS, "DELETE API responded: HTTP $result")
                    }
                }

                // ── Image-matching steps ──────────────────────────────────
                StepType.CLICK_IMAGE -> {
                    val imgFile = step.imageFile
                        ?: return@try StepResult(step, StepStatus.FAIL, "imageFile not specified")
                    val ref = ImageMatcher.resolveImageFile(imgFile)
                    val deadline = System.currentTimeMillis() + step.timeoutSec * 1000L
                    var match: ImageMatcher.MatchResult? = null
                    while (System.currentTimeMillis() < deadline) {
                        val snap = page.screenshot()
                        match = ImageMatcher.find(snap, ref, step.confidence)
                        if (match != null) break
                        delay(500)
                    }
                    if (match == null) {
                        StepResult(step, StepStatus.FAIL, "Image '${ref.name}' not found on screen (confidence ${step.confidence})")
                    } else {
                        page.mouse().click(
                            (match.centerX + step.offsetX).toDouble(),
                            (match.centerY + step.offsetY).toDouble()
                        )
                        StepResult(step, StepStatus.PASS, "Clicked image '${ref.name}' at (${match.centerX}, ${match.centerY}), score=${String.format("%.2f", match.score)}")
                    }
                }

                StepType.WAIT_FOR_IMAGE -> {
                    val imgFile = step.imageFile
                        ?: return@try StepResult(step, StepStatus.FAIL, "imageFile not specified")
                    val ref = ImageMatcher.resolveImageFile(imgFile)
                    val deadline = System.currentTimeMillis() + step.timeoutSec * 1000L
                    var match: ImageMatcher.MatchResult? = null
                    while (System.currentTimeMillis() < deadline) {
                        val snap = page.screenshot()
                        match = ImageMatcher.find(snap, ref, step.confidence)
                        if (match != null) break
                        delay(500)
                    }
                    if (match == null) {
                        StepResult(step, StepStatus.FAIL, "Timed out waiting for image '${ref.name}' (${step.timeoutSec}s)")
                    } else {
                        StepResult(step, StepStatus.PASS, "Image '${ref.name}' appeared at (${match.centerX}, ${match.centerY})")
                    }
                }

                StepType.ASSERT_IMAGE -> {
                    val imgFile = step.imageFile
                        ?: return@try StepResult(step, StepStatus.FAIL, "imageFile not specified")
                    val ref = ImageMatcher.resolveImageFile(imgFile)
                    val snap = page.screenshot()
                    val match = ImageMatcher.find(snap, ref, step.confidence)
                    if (match != null) {
                        StepResult(step, StepStatus.PASS, "Image '${ref.name}' found at (${match.centerX}, ${match.centerY})")
                    } else {
                        val annotPath = runCatching {
                            val path = "$screenshotDir/assert_fail_${UUID.randomUUID()}.png"
                            File(path).writeBytes(snap)
                            path
                        }.getOrNull()
                        StepResult(step, StepStatus.FAIL, "Image '${ref.name}' NOT found on screen", screenshotPath = annotPath)
                    }
                }

                StepType.HOVER_IMAGE -> {
                    val imgFile = step.imageFile
                        ?: return@try StepResult(step, StepStatus.FAIL, "imageFile not specified")
                    val ref = ImageMatcher.resolveImageFile(imgFile)
                    val snap = page.screenshot()
                    val match = ImageMatcher.find(snap, ref, step.confidence)
                    if (match == null) {
                        StepResult(step, StepStatus.FAIL, "Image '${ref.name}' not found for hover")
                    } else {
                        page.mouse().move(
                            (match.centerX + step.offsetX).toDouble(),
                            (match.centerY + step.offsetY).toDouble()
                        )
                        StepResult(step, StepStatus.PASS, "Hovered image '${ref.name}' at (${match.centerX}, ${match.centerY})")
                    }
                }

                StepType.PAUSE_FOR_INPUT -> {
                    val prompt = step.value ?: "Enter a value"
                    val input = onInputRequired(prompt)
                    // Store the input so subsequent steps can reference ${INPUT} or ${KEY}
                    capturedInputs[step.selector ?: "INPUT"] = input
                    StepResult(step, StepStatus.PASS, "User entered: $input")
                }

                StepType.BYPASS_AUTH -> {
                    // POSTs to a test-login endpoint on YOUR OWN staging server.
                    // Endpoint must accept: { "email": "...", "password": "..." }
                    // and respond with any 2xx to indicate success.
                    val endpoint = step.value?.interpolate() ?: bypassAuthEndpoint
                    if (endpoint.isBlank()) {
                        return@try StepResult(step, StepStatus.FAIL, "No bypass endpoint — set bypassAuthEndpoint in the flow config or use step.value")
                    }
                    val password = step.selector?.interpolate() ?: ""
                    val body = """{"email":"${generatedEmail}","password":"$password"}"""
                    val conn = java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection
                    conn.apply {
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        connectTimeout = (step.timeoutSec * 1000)
                        readTimeout = (step.timeoutSec * 1000)
                        outputStream.use { it.write(body.toByteArray()) }
                    }
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code in 200..299) {
                        StepResult(step, StepStatus.PASS, "BYPASS_AUTH: endpoint returned $code for $generatedEmail")
                    } else {
                        StepResult(step, StepStatus.FAIL, "BYPASS_AUTH: endpoint returned $code (expected 2xx)")
                    }
                }
            }
        } catch (e: PlaywrightException) {
            StepResult(step, if (step.optional) StepStatus.SKIPPED else StepStatus.FAIL, e.message ?: "Playwright error")
        } catch (e: Exception) {
            StepResult(step, if (step.optional) StepStatus.SKIPPED else StepStatus.FAIL, e.message ?: "Error")
        }
    }

    private fun takeScreenshot(page: Page, name: String): String? {
        return try {
            val path = "$screenshotDir/$name.png"
            page.screenshot(Page.ScreenshotOptions().setPath(Paths.get(path)))
            path
        } catch (e: Exception) {
            null
        }
    }
}

fun generateEmail(domain: String): String {
    val chars = ('a'..'z') + ('0'..'9')
    val localPart = "qa-" + (1..8).map { chars.random() }.joinToString("")
    return "$localPart@$domain"
}
