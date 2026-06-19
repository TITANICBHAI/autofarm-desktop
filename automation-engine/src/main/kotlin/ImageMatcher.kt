package com.autofarm.engine

import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * OpenCV-based template matching.
 *
 * Usage:
 *   ImageMatcher.init()   // call once at startup
 *   val hit = ImageMatcher.find(screenshotBytes, referenceFile, confidence = 0.8)
 *   hit?.let { (x, y) -> page.mouse().click(x.toDouble(), y.toDouble()) }
 */
object ImageMatcher {

    private var loaded = false

    /** Must be called once before any matching. Safe to call multiple times. */
    fun init() {
        if (loaded) return
        nu.pattern.OpenCV.loadLocally()
        loaded = true
    }

    data class MatchResult(
        val centerX: Int,
        val centerY: Int,
        val score: Double,
        val width: Int,
        val height: Int
    )

    /**
     * Find [referenceFile] inside [screenshotBytes] (PNG/JPEG bytes from Playwright).
     * Returns null if no match above [confidence].
     */
    fun find(
        screenshotBytes: ByteArray,
        referenceFile: File,
        confidence: Double = 0.8
    ): MatchResult? {
        init()
        if (!referenceFile.exists()) return null

        val scene = Imgcodecs.imdecode(MatOfByte(*screenshotBytes), Imgcodecs.IMREAD_COLOR)
        val tmpl = Imgcodecs.imread(referenceFile.absolutePath, Imgcodecs.IMREAD_COLOR)

        if (scene.empty() || tmpl.empty()) return null
        if (tmpl.rows() > scene.rows() || tmpl.cols() > scene.cols()) return null

        val result = Mat()
        Imgproc.matchTemplate(scene, tmpl, result, Imgproc.TM_CCOEFF_NORMED)

        val mmr = Core.minMaxLoc(result)
        val score = mmr.maxVal
        if (score < confidence) return null

        val loc = mmr.maxLoc
        return MatchResult(
            centerX = (loc.x + tmpl.cols() / 2).toInt(),
            centerY = (loc.y + tmpl.rows() / 2).toInt(),
            score = score,
            width = tmpl.cols(),
            height = tmpl.rows()
        )
    }

    /**
     * Annotate a screenshot with a green rectangle around the matched region.
     * Returns PNG bytes of the annotated image.
     */
    fun annotate(screenshotBytes: ByteArray, match: MatchResult): ByteArray {
        init()
        val scene = Imgcodecs.imdecode(MatOfByte(*screenshotBytes), Imgcodecs.IMREAD_COLOR)
        val tl = Point((match.centerX - match.width / 2).toDouble(), (match.centerY - match.height / 2).toDouble())
        val br = Point((match.centerX + match.width / 2).toDouble(), (match.centerY + match.height / 2).toDouble())
        Imgproc.rectangle(scene, tl, br, Scalar(0.0, 255.0, 0.0), 3)
        Imgproc.circle(scene, Point(match.centerX.toDouble(), match.centerY.toDouble()), 6, Scalar(0.0, 0.0, 255.0), -1)
        val out = MatOfByte()
        Imgcodecs.imencode(".png", scene, out)
        return out.toArray()
    }

    /** Resolve imageFile path: try absolute, then ~/.autofarm/images/<name> */
    fun resolveImageFile(imageFile: String): File {
        val f = File(imageFile)
        if (f.isAbsolute && f.exists()) return f
        val inStore = File(System.getProperty("user.home") + "/.autofarm/images/$imageFile")
        return if (inStore.exists()) inStore else f
    }

    /** Save an image into the app image store. Returns the stored filename. */
    fun storeImage(sourceFile: File): String {
        val dir = File(System.getProperty("user.home") + "/.autofarm/images")
        dir.mkdirs()
        val dest = File(dir, sourceFile.name)
        sourceFile.copyTo(dest, overwrite = true)
        return dest.name
    }
}
