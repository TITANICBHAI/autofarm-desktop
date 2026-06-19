package com.autofarm.engine

import com.autofarm.core.HumanConfig
import com.microsoft.playwright.Page
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

/**
 * Human-like browser interaction utilities.
 *
 * Mouse: moves along a randomised cubic Bezier curve with micro-jitter,
 *        so the path looks like a real hand on a mouse rather than a
 *        straight teleport to coordinates.
 *
 * Typing: character-by-character with per-key delay variance, occasional
 *         mid-word pauses, and rare accidental-backspace-then-retype events.
 */
object HumanBehavior {

    // ── Mouse ────────────────────────────────────────────────────────────────

    /**
     * Move mouse from its current position to (targetX, targetY) along a
     * cubic Bezier curve, then click. Optionally nudges aim slightly off
     * center and self-corrects (simulates imprecise then re-aimed click).
     */
    suspend fun humanClick(
        page: Page,
        targetX: Double,
        targetY: Double,
        cfg: HumanConfig = HumanConfig()
    ) {
        moveToBezier(page, targetX, targetY, cfg)
        delay(randomIn(cfg.preClickPauseMinMs, cfg.preClickPauseMaxMs))
        page.mouse().down()
        delay(randomIn(20, 80))
        page.mouse().up()
    }

    /**
     * Move to a CSS selector's bounding-box center, then click humanly.
     */
    suspend fun humanClickSelector(
        page: Page,
        selector: String,
        cfg: HumanConfig = HumanConfig()
    ) {
        val box = page.locator(selector).first().boundingBox() ?: run {
            page.click(selector)   // fallback
            return
        }
        val cx = box.x + box.width / 2 + randomIn(-3.0, 3.0)
        val cy = box.y + box.height / 2 + randomIn(-3.0, 3.0)
        humanClick(page, cx, cy, cfg)
    }

    /**
     * Click a selector, then type text character by character with
     * realistic timing. Handles existing content by clearing first.
     */
    suspend fun humanFill(
        page: Page,
        selector: String,
        text: String,
        cfg: HumanConfig = HumanConfig()
    ) {
        humanClickSelector(page, selector, cfg)
        delay(randomIn(80, 200))

        // Clear existing value
        page.keyboard().press("Control+a")
        delay(randomIn(30, 80))
        page.keyboard().press("Delete")
        delay(randomIn(40, 100))

        humanType(page, text, cfg)
    }

    /**
     * Type [text] into whatever element currently has focus, with
     * per-character timing variation, micro-pauses, and rare typos.
     */
    suspend fun humanType(
        page: Page,
        text: String,
        cfg: HumanConfig = HumanConfig()
    ) {
        var i = 0
        while (i < text.length) {
            val ch = text[i]

            // Rare typo: insert wrong char, pause, backspace, retype
            if (cfg.enableTypos && i > 0 && Random.nextDouble() < cfg.typoRate) {
                val wrongKey = nearbyKey(ch)
                page.keyboard().type(wrongKey.toString())
                delay(randomIn(cfg.typoRealisationMinMs, cfg.typoRealisationMaxMs))
                page.keyboard().press("Backspace")
                delay(randomIn(40, 100))
            }

            page.keyboard().type(ch.toString())

            // Base per-key delay with variance
            val baseDelay = randomIn(cfg.typingMinMs, cfg.typingMaxMs)
            // Extra pause after space or punctuation (natural word boundary)
            val wordPause = if (ch == ' ' || ch == '.' || ch == ',') randomIn(0, cfg.wordPauseExtraMs) else 0L
            // Occasional longer think-pause every 8–15 chars
            val thinkPause = if (Random.nextInt(12) == 0) randomIn(cfg.thinkPauseMinMs, cfg.thinkPauseMaxMs) else 0L

            delay(baseDelay + wordPause + thinkPause)
            i++
        }
    }

    // ── Bezier movement ──────────────────────────────────────────────────────

    private suspend fun moveToBezier(
        page: Page,
        tx: Double,
        ty: Double,
        cfg: HumanConfig
    ) {
        // Get approximate current mouse position via JS (best effort)
        val startX = runCatching {
            (page.evaluate("() => window.__afMouseX || window.innerWidth/2") as? Number)?.toDouble() ?: (page.viewportSize()!!.width / 2.0)
        }.getOrElse { page.viewportSize()?.width?.div(2.0) ?: 640.0 }
        val startY = runCatching {
            (page.evaluate("() => window.__afMouseY || window.innerHeight/2") as? Number)?.toDouble() ?: (page.viewportSize()!!.height / 2.0)
        }.getOrElse { page.viewportSize()?.height?.div(2.0) ?: 400.0 }

        // Install mouse tracker (idempotent)
        runCatching {
            page.evaluate("""
                if (!window.__afMouseTracked) {
                  window.__afMouseTracked = true;
                  document.addEventListener('mousemove', e => {
                    window.__afMouseX = e.clientX;
                    window.__afMouseY = e.clientY;
                  });
                }
            """.trimIndent())
        }

        val steps = (cfg.mouseMoveSteps + Random.nextInt(-5, 10)).coerceAtLeast(10)

        // Two random control points for cubic Bezier
        val spread = cfg.bezierSpread
        val cp1x = lerp(startX, tx, 0.25) + randomIn(-spread, spread)
        val cp1y = lerp(startY, ty, 0.25) + randomIn(-spread, spread)
        val cp2x = lerp(startX, tx, 0.75) + randomIn(-spread, spread)
        val cp2y = lerp(startY, ty, 0.75) + randomIn(-spread, spread)

        for (step in 0..steps) {
            val t = step.toDouble() / steps
            val x = cubicBezier(t, startX, cp1x, cp2x, tx)
            val y = cubicBezier(t, startY, cp1y, cp2y, ty)

            // Micro-jitter on each point
            val jx = x + randomIn(-cfg.jitterPx, cfg.jitterPx)
            val jy = y + randomIn(-cfg.jitterPx, cfg.jitterPx)

            page.mouse().move(jx, jy)
            delay(randomIn(cfg.mouseMoveStepMinMs, cfg.mouseMoveStepMaxMs))
        }

        // Final snap to exact target
        page.mouse().move(tx, ty)
    }

    // ── Math helpers ─────────────────────────────────────────────────────────

    private fun cubicBezier(t: Double, p0: Double, p1: Double, p2: Double, p3: Double): Double {
        val u = 1.0 - t
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    private fun randomIn(min: Long, max: Long) = if (min >= max) min else Random.nextLong(min, max)
    private fun randomIn(min: Int, max: Int): Long = if (min >= max) min.toLong() else Random.nextLong(min.toLong(), max.toLong())
    private fun randomIn(min: Double, max: Double) = min + Random.nextDouble() * (max - min)

    /**
     * Return a key physically close to [ch] on a QWERTY keyboard.
     * Used for the rare typo simulation.
     */
    private fun nearbyKey(ch: Char): Char {
        val neighbours = mapOf(
            'a' to "sqwz", 'b' to "vghn", 'c' to "xdfv", 'd' to "srfec",
            'e' to "wrsdf", 'f' to "dgrtcv", 'g' to "fhtyv", 'h' to "gjuyb",
            'i' to "ujko", 'j' to "hkuin", 'k' to "jlim", 'l' to "kop",
            'm' to "njk", 'n' to "bhjm", 'o' to "iklp", 'p' to "ol",
            'q' to "wa", 'r' to "etdf", 's' to "awedz", 't' to "rfgy",
            'u' to "yhji", 'v' to "cfgb", 'w' to "qase", 'x' to "zscd",
            'y' to "tghu", 'z' to "asx",
            '1' to "2q", '2' to "13qw", '3' to "24we", '4' to "35er",
            '5' to "46rt", '6' to "57ty", '7' to "68yu", '8' to "79ui",
            '9' to "80io", '0' to "9op"
        )
        val pool = neighbours[ch.lowercaseChar()] ?: return ch
        return pool.random()
    }
}
