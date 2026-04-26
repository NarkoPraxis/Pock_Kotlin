package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import kotlin.math.sin

class GhostSkin(override val theme: ColorTheme, override val renderer: PuckRenderer) : PuckSkin {

    private data class AuraRing(val baseMult: Float, val amp: Float, val phase: Float, val alpha: Int, val strokeMult: Float)

    private val auraRings = listOf(
        AuraRing(1.10f, 0.06f, 0.0f, 70,  1.6f),
        AuraRing(1.20f, 0.08f, 1.0f, 45,  1.2f),
        AuraRing(1.35f, 0.10f, 2.2f, 25,  2.0f),
        AuraRing(1.55f, 0.12f, 3.7f, 12,  2.8f)
    )

    private val fill = Paint().apply {
        color = Color.argb(120, 255, 255, 255)
        isAntiAlias = true; style = Paint.Style.FILL
    }
    private val stroke = Paint().apply {
        color = Color.argb(200, 255, 255, 255)
        isAntiAlias = true; style = Paint.Style.STROKE
    }
    private val glow = Paint().apply {
        isAntiAlias = true; isDither = true; style = Paint.Style.STROKE
    }

    override fun drawBody(canvas: Canvas) {
        val stateColors = resolvedColors()
        val glowColor = when {
            renderer.isInert -> stateColors.primary
            renderer.currentCharge >= Settings.chargeStart || renderer.shielded -> theme.effect.primary
            else -> stateColors.primary
        }
        val sw = renderer.strokePaint.strokeWidth

        // Animated aura rings drawn behind the orb — each has its own oscillation phase
        for (ring in auraRings) {
            val r = renderer.radius * ring.baseMult +
                    renderer.radius * ring.amp * sin(renderer.frame * 0.04f + ring.phase)
            glow.color = Palette.withAlpha(glowColor, ring.alpha)
            glow.strokeWidth = sw * ring.strokeMult
            canvas.drawCircle(renderer.x, renderer.y, r, glow)
        }

        // White orb at exact radius — not oversized
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, fill)

        // Inner ring pulses between 50% and 100% of radius, slowly
        val innerR = renderer.radius * 0.75f +
                renderer.radius * 0.1f * sin(renderer.frame * 0.025f + 5.0f)
        stroke.strokeWidth = sw * 0.7f
        canvas.drawCircle(renderer.x, renderer.y, innerR, stroke)

        renderer.chargePaint.color = glowColor
    }
}
