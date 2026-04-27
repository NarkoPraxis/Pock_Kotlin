package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.paddles.GalaxyLaunch
import gameobjects.puckstyle.paddles.GhostLaunch
import physics.Point
import kotlin.math.sin

class GhostSkin(override val theme: ColorTheme, override val renderer: PuckRenderer) : PuckSkin {

    private data class AuraRing(val baseMult: Float, val amp: Float, val phase: Float, val alpha: Int, val strokeMult: Float)

    private val auraRings = listOf(
        AuraRing(.6f, 0.2f, 1.0f, 80,  .5f),
        AuraRing(.8f, 0.1f, 2.0f, 50,  1f),
        AuraRing(.95f, 0.3f, 3.0f, 30,  2.0f),
        AuraRing(1.10f, 0.2f, 4.0f, 20,  4f)
    )

    override fun onCollisionWin(position: Point, speed: Float) {
        GhostLaunch.spawnImpact(position.x, position.y, renderer.radius * .4f, theme.main.primary, renderer)
    }

    override fun onShieldedCollision(position: Point) {
        GhostLaunch.spawnImpact(position.x, position.y, renderer.radius * .6f, theme.shield.primary, renderer)
    }

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

    companion object {
        fun radiusOffset(renderer: PuckRenderer): Float {
            if (renderer.currentCharge <= 0f) return 1f
            val halfRange = (Settings.sweetSpotMax - Settings.chargeStart) * 0.5f
            val t = ((renderer.currentCharge - Settings.chargeStart) / halfRange).coerceIn(0f, 1f)
            return 1f - 0.3f * t
        }
    }

    override fun drawBody(canvas: Canvas) {
        val glowColor = responsivePrimary

        val sw = renderer.strokePaint.strokeWidth

        val radiusOffset = radiusOffset(renderer)

        // Animated aura rings drawn behind the orb — each has its own oscillation phase
        for (ring in auraRings) {
            val r = renderer.radius * ring.baseMult +
                    renderer.radius * ring.amp * sin(renderer.frame * 0.04f + ring.phase)
            val alpha = ring.alpha + ring.alpha * (ring.amp * sin(renderer.frame * 0.04f + ring.phase)).toInt()
            glow.color = Palette.withAlpha(glowColor, alpha)
            glow.strokeWidth = sw * ring.strokeMult
            canvas.drawCircle(renderer.x, renderer.y, r , glow)
        }

        // White orb at exact radius — not oversized
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius * radiusOffset, fill)

        // Inner ring pulses between 50% and 100% of radius, slowly
        val innerR = renderer.radius * 0.75f +
                renderer.radius * 0.1f * sin(renderer.frame * 0.025f + 5.0f)
        stroke.strokeWidth = sw * 0.7f
        canvas.drawCircle(renderer.x, renderer.y, innerR * radiusOffset, stroke)

        renderer.chargePaint.color = glowColor
    }
}
