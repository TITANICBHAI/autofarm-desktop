package com.autofarm

import java.awt.*

/**
 * Sends OS-level balloon/toast notification via java.awt.SystemTray.
 * Safe to call from any thread. No-ops gracefully if SystemTray is unavailable.
 */
object TrayNotifier {

    private var trayIcon: TrayIcon? = null

    fun init(appName: String = "AutoFarm") {
        if (!SystemTray.isSupported()) return
        runCatching {
            val tray = SystemTray.getSystemTray()
            // Tiny 16x16 icon painted in code — no external resource needed
            val img = createIcon(tray.trayIconSize)
            val icon = TrayIcon(img, appName).also {
                it.isImageAutoSize = true
                it.toolTip = appName
            }
            tray.add(icon)
            trayIcon = icon
        }
    }

    fun notify(title: String, message: String, type: TrayIcon.MessageType = TrayIcon.MessageType.INFO) {
        trayIcon?.displayMessage(title, message, type) ?: run {
            // Fallback: print to stderr so CI logs still capture it
            System.err.println("[$title] $message")
        }
    }

    fun dispose() {
        trayIcon?.let {
            runCatching { SystemTray.getSystemTray().remove(it) }
            trayIcon = null
        }
    }

    private fun createIcon(size: Dimension): Image {
        val w = size.width.coerceAtLeast(16)
        val h = size.height.coerceAtLeast(16)
        val img = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        // Background circle
        g.color = Color(0x1DB8A8)
        g.fillOval(0, 0, w, h)
        // "AF" text
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.BOLD, (w * 0.45).toInt().coerceAtLeast(6))
        val fm = g.fontMetrics
        val text = "AF"
        val tx = (w - fm.stringWidth(text)) / 2
        val ty = (h + fm.ascent - fm.descent) / 2
        g.drawString(text, tx, ty)
        g.dispose()
        return img
    }
}
