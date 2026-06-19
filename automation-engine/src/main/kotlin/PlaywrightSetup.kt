package com.autofarm.engine

import com.microsoft.playwright.Playwright
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ensures Playwright's Chromium browser is installed before the first run.
 *
 * Playwright bundles browser binaries separately from its JAR. If they're
 * missing, every launch attempt silently fails or throws a cryptic error.
 * This object checks once and installs if needed.
 */
object PlaywrightSetup {

    private var checked = false

    data class SetupState(
        val needed: Boolean,
        val done: Boolean = false,
        val error: String? = null
    )

    /** Returns true if Chromium is already installed. */
    fun isInstalled(): Boolean {
        return runCatching {
            val pw = Playwright.create()
            val path = pw.chromium().executablePath()
            pw.close()
            path != null && java.io.File(path.toString()).exists()
        }.getOrElse { false }
    }

    /**
     * Install Playwright browsers if not already present.
     * Runs the equivalent of `playwright install chromium` via the Java API.
     * [onProgress] receives log lines as they arrive.
     */
    suspend fun ensureInstalled(onProgress: (String) -> Unit = {}): SetupState = withContext(Dispatchers.IO) {
        if (checked && isInstalled()) return@withContext SetupState(needed = false, done = true)

        if (isInstalled()) {
            checked = true
            return@withContext SetupState(needed = false, done = true)
        }

        onProgress("Chromium browser not found — installing now (one-time setup, ~150 MB)…")

        return@withContext try {
            // Playwright's install CLI is invoked via its main class
            val result = ProcessBuilder(
                ProcessHandle.current().info().command().orElse("java"),
                "-cp", System.getProperty("java.class.path"),
                "com.microsoft.playwright.CLI",
                "install", "chromium"
            )
                .redirectErrorStream(true)
                .start()

            val reader = result.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                onProgress(line ?: "")
            }

            val exitCode = result.waitFor()
            checked = true
            if (exitCode == 0) {
                onProgress("Chromium installed successfully.")
                SetupState(needed = true, done = true)
            } else {
                SetupState(needed = true, done = false, error = "Install exited with code $exitCode")
            }
        } catch (e: Exception) {
            SetupState(needed = true, done = false, error = e.message)
        }
    }
}
