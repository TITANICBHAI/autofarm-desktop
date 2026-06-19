package com.autofarm.engine

import com.autofarm.core.Step
import com.autofarm.core.StepType
import com.microsoft.playwright.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Records browser interactions and converts them to Step JSON.
 *
 * Opens a real (non-headless) browser. The user navigates normally.
 * JS event listeners capture clicks, fills, navigations and send them
 * back via Playwright's exposeFunction bridge.
 *
 * Each captured event is emitted on [eventChannel] for the UI to display live.
 */
class StepRecorder {

    @Serializable
    data class RecordedEvent(
        val type: String,        // "click" | "fill" | "navigate" | "keypress"
        val selector: String? = null,
        val value: String? = null,
        val url: String? = null,
        val key: String? = null,
        val description: String? = null
    )

    val eventChannel = Channel<RecordedEvent>(Channel.UNLIMITED)
    private val recordedEvents = mutableListOf<RecordedEvent>()
    private var playwright: Playwright? = null
    private var browser: Browser? = null
    private var page: Page? = null
    private val json = Json { ignoreUnknownKeys = true }

    fun start(startUrl: String = "about:blank") {
        playwright = Playwright.create()
        browser = playwright!!.chromium().launch(
            BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(listOf("--start-maximized"))
        )
        val context = browser!!.newContext(
            Browser.NewContextOptions().setViewportSize(null) // use system window size
        )
        page = context.newPage()

        // Bridge: JS → Kotlin via exposeFunction
        page!!.exposeFunction("__afRecord") { args ->
            val raw = args.firstOrNull()?.toString() ?: return@exposeFunction null
            try {
                val event = json.decodeFromString<RecordedEvent>(raw)
                recordedEvents.add(event)
                eventChannel.trySend(event)
            } catch (_: Exception) {}
            null
        }

        // Inject recorder script on every page load
        val recorderScript = buildRecorderScript()
        context.addInitScript(recorderScript)

        // Also inject immediately on current page
        page!!.addScriptTag(Page.AddScriptTagOptions().setContent(recorderScript))

        if (startUrl != "about:blank" && startUrl.isNotBlank()) {
            page!!.navigate(startUrl)
        }

        // Capture top-level navigations
        page!!.onFrameNavigated { frame ->
            if (frame == page!!.mainFrame()) {
                val event = RecordedEvent(
                    type = "navigate",
                    url = frame.url(),
                    description = "Navigate to ${frame.url()}"
                )
                recordedEvents.add(event)
                eventChannel.trySend(event)
            }
        }
    }

    fun stop() {
        runCatching { browser?.close() }
        runCatching { playwright?.close() }
        eventChannel.close()
    }

    fun getRecordedSteps(): List<Step> = recordedEvents.mapNotNull { it.toStep() }

    fun clearEvents() { recordedEvents.clear() }

    fun takeSnapshot(): ByteArray? = runCatching { page?.screenshot() }.getOrNull()

    private fun RecordedEvent.toStep(): Step? = when (type) {
        "navigate" -> Step(
            type = StepType.NAVIGATE,
            value = url,
            description = description ?: "Navigate to $url"
        )
        "click" -> Step(
            type = StepType.CLICK,
            selector = selector,
            description = description ?: "Click $selector"
        )
        "fill" -> Step(
            type = StepType.FILL,
            selector = selector,
            value = value,
            description = description ?: "Fill $selector"
        )
        "keypress" -> Step(
            type = StepType.PRESS_KEY,
            selector = selector,
            value = key,
            description = description ?: "Press $key"
        )
        "screenshot" -> Step(
            type = StepType.SCREENSHOT,
            description = "Screenshot"
        )
        else -> null
    }

    private fun buildRecorderScript() = """
(function() {
  if (window.__afRecorderInstalled) return;
  window.__afRecorderInstalled = true;

  function bestSelector(el) {
    if (!el) return null;
    if (el.id) return '#' + el.id;
    if (el.getAttribute('data-testid')) return '[data-testid="' + el.getAttribute('data-testid') + '"]';
    if (el.getAttribute('name')) return el.tagName.toLowerCase() + '[name="' + el.getAttribute('name') + '"]';
    if (el.getAttribute('aria-label')) return '[aria-label="' + el.getAttribute('aria-label') + '"]';
    // Build a short class-based path
    var classes = Array.from(el.classList).slice(0, 2).join('.');
    if (classes) return el.tagName.toLowerCase() + '.' + classes;
    // Fallback: short nth-child path (max 3 levels)
    var path = [];
    var cur = el;
    for (var i = 0; i < 3 && cur && cur !== document.body; i++) {
      var tag = cur.tagName.toLowerCase();
      var idx = Array.from(cur.parentElement ? cur.parentElement.children : []).indexOf(cur) + 1;
      path.unshift(tag + ':nth-child(' + idx + ')');
      cur = cur.parentElement;
    }
    return path.join(' > ');
  }

  // Clicks
  document.addEventListener('click', function(e) {
    var el = e.target;
    var sel = bestSelector(el);
    var label = el.textContent ? el.textContent.trim().slice(0, 40) : '';
    window.__afRecord(JSON.stringify({
      type: 'click',
      selector: sel,
      description: label ? 'Click "' + label + '"' : 'Click ' + sel
    }));
  }, true);

  // Fill / input (fires on blur so we capture the final value)
  document.addEventListener('focusout', function(e) {
    var el = e.target;
    if (!['INPUT','TEXTAREA','SELECT'].includes(el.tagName)) return;
    var val = el.value || '';
    if (!val) return;
    var sel = bestSelector(el);
    window.__afRecord(JSON.stringify({
      type: 'fill',
      selector: sel,
      value: val,
      description: 'Fill "' + sel + '" with "' + val.slice(0, 30) + '"'
    }));
  }, true);

  // Key presses (Enter, Tab, Escape, etc.)
  document.addEventListener('keydown', function(e) {
    var interesting = ['Enter','Tab','Escape','ArrowDown','ArrowUp','Space'];
    if (!interesting.includes(e.key)) return;
    var el = document.activeElement;
    window.__afRecord(JSON.stringify({
      type: 'keypress',
      key: e.key,
      selector: el ? bestSelector(el) : null,
      description: 'Press ' + e.key
    }));
  }, true);
})();
""".trimIndent()
}
