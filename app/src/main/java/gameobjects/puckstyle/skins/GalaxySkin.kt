package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Puck
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckSkin
import kotlin.random.Random

class GalaxySkin(override val theme: ColorTheme) : PuckSkin {

    private val yellow = Color.rgb(255, 220, 80)
    private val darkFill = Paint().apply { color = Color.rgb(15, 10, 30); isAntiAlias = true; style = Paint.Style.FILL }
    private val rim = Paint().apply { color = theme.primary; isAntiAlias = true; style = Paint.Style.STROKE }
    private val star = Paint().apply { color = Color.WHITE; isAntiAlias = true; style = Paint.Style.FILL }

    private val starSeeds = List(12) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) }

    override fun drawBody(canvas: Canvas, puck: Puck, radius: Float) {
        canvas.drawCircle(puck.x, puck.y, radius, darkFill)
        for ((sx, sy, tw) in starSeeds) {
            val ang = sx * Math.PI.toFloat() * 2
            val dist = sy * radius * 0.75f
            val px = puck.x + kotlin.math.cos(ang) * dist
            val py = puck.y + kotlin.math.sin(ang) * dist
            val twinkle = (kotlin.math.sin((puck.frame * 0.1f) + tw * 10f) + 1f) * 0.5f
            star.alpha = (120 + 135 * twinkle).toInt()
            canvas.drawCircle(px, py, radius * 0.06f * (0.6f + twinkle * 0.8f), star)
        }
        rim.strokeWidth = puck.strokePaint.strokeWidth * 0.8f
        canvas.drawCircle(puck.x, puck.y, radius, rim)
        puck.chargePaint.color = yellow
    }
}
