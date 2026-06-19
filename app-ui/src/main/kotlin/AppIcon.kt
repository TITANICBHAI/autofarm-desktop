package com.autofarm

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Font as SkiaFont
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.Typeface

/**
 * Creates the AutoFarm teal "AF" icon at 256×256 entirely in code.
 * No external PNG file required — works on any platform.
 */
fun buildAppIcon(): BitmapPainter {
    val size = 256
    val surface = Surface.makeRasterN32Premul(size, size)
    val canvas = surface.canvas

    // Teal background circle
    val bgPaint = Paint().apply { color = 0xFF1DB8A8.toInt() }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)

    // Inner accent ring
    val ringPaint = Paint().apply {
        color = 0x22FFFFFF.toInt()
        isAntiAlias = true
    }
    canvas.drawCircle(size / 2f, size / 2f, size * 0.38f, ringPaint)

    // "AF" letters
    val typeface = Typeface.makeDefault()
    val font = SkiaFont(typeface, size * 0.38f)
    val textLine = TextLine.make("AF", font)
    val x = (size - textLine.width) / 2f
    val y = (size + textLine.capHeight) / 2f

    val textPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        isAntiAlias = true
    }
    canvas.drawTextLine(textLine, x, y, textPaint)

    return BitmapPainter(surface.makeImageSnapshot().toComposeImageBitmap())
}
