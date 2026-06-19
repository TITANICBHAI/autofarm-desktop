# AutoFarm — Browser Automation Desktop App

A native Kotlin + Compose for Desktop app. Automates browser flows using:
- **JSON step configs** (selector-based, like Playwright)
- **Image/screenshot matching** (OpenCV template matching — no selectors needed)
- **Visual step recorder** (click normally in a browser, get JSON auto-generated)
- **IMAP OTP reading** (polls your catch-all inbox automatically)
- **Run history** (SQLite, per-step timing + failure screenshots)

Works on **Windows and Linux** from one codebase.

---

## App tabs

| Tab | What it does |
|-----|-------------|
| **Run** | Paste/edit JSON steps, set BASE_URL + email domain, start a run, watch live step results |
| **Record** | Opens a real browser — you interact normally, JSON is generated automatically |
| **Images** | Import reference PNG crops, get copy-paste step snippets for image-based clicks |
| **History** | Browse past runs, inspect per-step results |
| **Settings** | Allowed host allowlist, concurrency, headless mode, dry run, IMAP config |

---

## Prerequisites

- **JDK 17+** — [adoptium.net](https://adoptium.net)
- **A domain with catch-all email** — needed only for `WAIT_FOR_OTP` steps (see Email Setup below)

---

## Build & Run

```bash
cd kotlin-desktop-app

# First time only — downloads the Gradle wrapper jar
bash bootstrap.sh          # Linux/macOS
bootstrap.bat              # Windows

# Run the app from source
./gradlew :app-ui:run

# Build a native installer for your current OS
./gradlew :app-ui:packageDeb    # Linux → build/compose/binaries/main/deb/
./gradlew :app-ui:packageMsi    # Windows → build/compose/binaries/main/msi/
```

### Build both platforms free via GitHub Actions

1. Push the repo to any free GitHub account
2. Go to **Actions** → the `Build native installers` workflow runs automatically
3. Download `.msi` (Windows) and `.deb` (Linux) from the run's Artifacts section

---

## All step types

### Selector-based (standard)

| Type | Required fields | Description |
|------|----------------|-------------|
| `NAVIGATE` | `value` (URL) | Navigate to a URL. Supports `${BASE_URL}` |
| `CLICK` | `selector` | Click a CSS selector |
| `FILL` | `selector`, `value` | Type into a field. Supports `${GENERATED_EMAIL}`, `${OTP}` |
| `WAIT_FOR_SELECTOR` | `selector` | Wait until element appears |
| `WAIT_FOR_OTP` | `timeoutSec` | Poll IMAP inbox until OTP arrives, stores it as `${OTP}` |
| `ASSERT_TEXT` | `selector`, `value` | Fail if element text doesn't contain value |
| `SELECT_OPTION` | `selector`, `value` | Select a `<select>` dropdown option |
| `SCROLL_TO` | `selector` | Scroll element into view |
| `PRESS_KEY` | `value` (key name) | Press a keyboard key (Enter, Tab, Escape…) |
| `SCREENSHOT` | — | Save a screenshot to `~/.autofarm/screenshots/` |
| `DELETE_ACCOUNT_API` | `value` (URL) | HTTP DELETE to an admin endpoint instead of clicking through UI |

### Image-matching (OpenCV)

These steps take a screenshot of the current page and use OpenCV template matching to find a reference image you supply. No CSS selectors needed — works even when IDs/classes change.

| Type | Required fields | Description |
|------|----------------|-------------|
| `CLICK_IMAGE` | `imageFile`, `confidence` | Find reference image on screen and click its center |
| `WAIT_FOR_IMAGE` | `imageFile`, `timeoutSec` | Keep checking until image appears |
| `ASSERT_IMAGE` | `imageFile`, `confidence` | Fail the step if image is NOT visible |
| `HOVER_IMAGE` | `imageFile` | Move mouse over matched region (no click) |

**imageFile** — filename in `~/.autofarm/images/` (managed in the Images tab), or an absolute path.  
**confidence** — 0.0–1.0. `0.8` = 80% match required. Lower if you get false negatives, raise if you get false positives.  
**offsetX / offsetY** — pixel offset from matched center (optional, default 0).

### Other

| Type | Required fields | Description |
|------|----------------|-------------|
| `PAUSE_FOR_INPUT` | `value` (prompt text) | Pauses the run for manual input |

All steps accept `"optional": true` — failure is logged as SKIPPED and the run continues.

---

## Image step workflow (step by step)

```
1. Open your target website in any browser.
2. Take a screenshot (F12 → screenshot, or Snipping Tool).
3. Crop tightly around the button/element you want to target.
4. Save as PNG.
5. In the app → Images tab → Import Images → select your PNG.
6. Click the image in the grid → copy the CLICK_IMAGE snippet on the right.
7. Paste into your flow's step list in the Run tab.
```

Example snippet the Images tab generates:
```json
{ "type": "CLICK_IMAGE", "imageFile": "login-btn.png", "confidence": 0.8, "description": "Click login button" }
```

---

## Visual recorder workflow

```
1. Go to the Record tab.
2. Enter the starting URL.
3. Click Start Recording — a real browser window opens.
4. Interact normally: navigate, click, type. Every action is captured live.
5. Click Snapshot at any point to save a screenshot (useful for image steps).
6. Click Stop — the generated JSON appears on the right.
7. Copy it into the Run tab's step editor.
8. Edit descriptions or add image steps inline.
```

The recorder injects JavaScript event listeners into every page. It captures:
- **Navigations** — full URL
- **Clicks** — best available CSS selector (id → data-testid → aria-label → class → nth-child path)
- **Fills** — captured on blur with final value
- **Key presses** — Enter, Tab, Escape, Arrow keys

---

## Config files (all in `~/.autofarm/`)

| File/Dir | Contents |
|----------|----------|
| `config.json` | Saved Settings (IMAP, allowlist, headless, etc.) |
| `runs.db` | SQLite run history |
| `screenshots/` | Failure + manual screenshots |
| `images/` | Reference PNGs for image-matching steps |

---

## Email / OTP setup

Only needed if your flow uses `WAIT_FOR_OTP`. You need a domain where **any** address (`anything@yourdomain.com`) delivers mail to one inbox.

### Free: ImprovMX
1. [improvmx.com](https://improvmx.com) → Add domain → set catch-all alias → forward to your email
2. Enable IMAP on your email provider (Gmail: Settings → IMAP; Outlook: Settings → IMAP)
3. In the app → Settings → Email / IMAP → enter host/port/user/password → Test Connection

### Free: Cloudflare Email Routing
1. Add your domain to Cloudflare (free)
2. Email → Email Routing → Catch-all rule → forward to your mailbox
3. Use your mailbox's IMAP credentials in Settings

---

## Project layout (for new agents)

```
kotlin-desktop-app/
├── core/                         # Shared models — Step, StepType, RunResult, FlowConfig, MailConfig, AppConfig
│   └── src/main/kotlin/
│       └── Models.kt             ← ALL data classes and enums live here. Edit step types here first.
├── automation-engine/            # No UI — pure logic
│   └── src/main/kotlin/
│       ├── StepRunner.kt         ← Executes steps against a Playwright Page. Add new step handlers here.
│       ├── ImageMatcher.kt       ← OpenCV template matching. find() takes screenshot bytes + reference File.
│       ├── StepRecorder.kt       ← Opens browser, injects JS, emits RecordedEvent via Channel.
│       ├── FlowManager.kt        ← Orchestrates runs: parses JSON, calls StepRunner, saves to DB.
│       └── Database.kt           ← Exposed/SQLite ORM. RunsTable, insert/update/query runs.
├── mail-client/                  # IMAP OTP polling
│   └── src/main/kotlin/
│       └── ImapClient.kt         ← pollForOtp() polls INBOX, extracts OTP via regex, deletes message.
└── app-ui/                       # Compose for Desktop UI
    └── src/main/kotlin/
        ├── Main.kt               ← Entry point. Window + NavigationRail + tab routing (Run/Record/Images/History/Settings).
        ├── AppState.kt           ← AppPrefs: load/save config.json in ~/.autofarm/
        └── ui/
            ├── Theme.kt          ← Dark color scheme (MaterialTheme).
            ├── Components.kt     ← Shared composables: StatusBadge, StepRow, AppCard, LabeledField.
            ├── RunScreen.kt      ← Left: flow config + JSON editor. Right: live step results.
            ├── RecorderScreen.kt ← Left: recorder controls + event log. Right: generated JSON + snapshot.
            ├── ImageStoreScreen.kt ← Grid of reference PNGs + copy-paste step snippets.
            ├── HistoryScreen.kt  ← Run history list + detail panel.
            └── SettingsScreen.kt ← AppConfig + MailConfig form with IMAP test button.
```

### Adding a new step type (checklist for future agents)

1. Add the enum value to `StepType` in `core/src/main/kotlin/Models.kt`
2. Add new fields to `Step` if needed (also in `Models.kt`)
3. Add the handler `when` branch in `StepRunner.kt → executeStep()`
4. Update the step types table in this README
5. Done — the JSON editor, recorder output, and history all pick it up automatically

---

## Selectors quick reference

Use browser DevTools (F12 → Inspector → right-click element → Copy selector):

| Pattern | Example | Notes |
|---------|---------|-------|
| `#id` | `#signup-btn` | Most reliable |
| `[data-testid="x"]` | `[data-testid="login"]` | Very stable |
| `[aria-label="x"]` | `[aria-label="Close"]` | Good for icon buttons |
| `button:has-text("x")` | `button:has-text("Sign up")` | Playwright text selector |
| `.class` | `.submit-button` | Use if no ID/testid |
