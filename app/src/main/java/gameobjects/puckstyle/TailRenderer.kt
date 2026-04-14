package gameobjects.puckstyle

import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import gameobjects.Puck

interface TailRenderer {
    val theme: ColorTheme
    fun render(
        canvas: Canvas,
        puck: Puck,
        shielded: Boolean,
        launched: Boolean,
        baseFillColor: Int
    )
    fun clear()

    // Plan 03: wrapper for preview screens — passes through for unlocked balls, maps all tail
    // colors to near-black for placeholder (silhouette) pucks via a saveLayer color matrix.
    fun renderForPreview(canvas: Canvas, puck: Puck, shielded: Boolean, launched: Boolean, baseFillColor: Int) {
        if (!puck.isPlaceholder) {
            render(canvas, puck, shielded, launched, baseFillColor)
            return
        }
        // Map every rendered RGB to ~12% brightness (≈ dark grey Color.argb(200,30,30,30))
        // while preserving each particle's alpha so fade effects remain intact.
        val cm = ColorMatrix(floatArrayOf(
            0.12f, 0f,    0f,    0f, 0f,
            0f,    0.12f, 0f,    0f, 0f,
            0f,    0f,    0.12f, 0f, 0f,
            0f,    0f,    0f,    1f, 0f
        ))
        @Suppress("DEPRECATION")
        canvas.saveLayer(null, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        render(canvas, puck, shielded, launched, baseFillColor)
        canvas.restore()
    }
}
