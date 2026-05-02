package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.Palette.hsv
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.paddles.RainbowLaunch
import physics.Point
import utility.Effects

class RainbowSkin( override val renderer: PuckRenderer) : PuckSkin {
    private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val stroke = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val hueOffset = Palette.themeHue(theme)
    private val shieldHue = Palette.colorHue(theme.shield.primary)

    override fun drawBody(canvas: Canvas) {
        val baseHue = renderer.frame * 4f + hueOffset
        val osc = kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f
        fill.color = when {
            renderer.isInert -> hsv(baseHue, 0.10f, 0.90f)
            renderer.shielded -> Palette.hsvThemed(shieldHue + osc)
            else -> Palette.hsvThemed(baseHue)
        }
        stroke.color = when {
            renderer.isInert -> theme.inert.secondary
            renderer.shielded -> theme.shield.secondary
            else -> theme.main.primary
        }
        stroke.strokeWidth = renderer.strokePaint.strokeWidth
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, fill)
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, stroke)
        renderer.chargePaint.color = fill.color
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        RainbowLaunch.spawnRainbow(renderer.x, renderer.y, renderer.radius)
    }

    override fun onShieldedCollision(position: Point) {
        RainbowLaunch.spawnRainbow(renderer.x, renderer.y, renderer.radius)
    }

    override val explosionFrequency get() = 20

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(RainbowLaunch.spawnCelebration(position.x, position.y, renderer.radius))
    }

    override fun onVictory(x: Float, y: Float) {
        Effects.addPersistentEffect(RainbowLaunch.spawnCelebration(x, y, renderer.radius))
    }
}
