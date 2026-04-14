package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import gameobjects.Puck
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckSkin

class PrismSkin(override val theme: ColorTheme) : PuckSkin {

    private val facet = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val edge = Paint().apply { color = Color.WHITE; isAntiAlias = true; style = Paint.Style.STROKE }
    private val path = Path()

    private val hues = if (theme.isWarm) floatArrayOf(0f, 30f, 340f, 15f, 50f, 5f)
                       else floatArrayOf(200f, 240f, 280f, 180f, 220f, 260f)

    override fun drawBody(canvas: Canvas, puck: Puck, radius: Float) {
        val sides = 6
        val angleOffset = puck.frame * 0.8f
        canvas.save()
        canvas.translate(puck.x, puck.y)
        canvas.rotate(angleOffset)

        for (i in 0 until sides) {
            val a1 = (i * 360f / sides) * Math.PI.toFloat() / 180f
            val a2 = ((i + 1) * 360f / sides) * Math.PI.toFloat() / 180f
            path.reset()
            path.moveTo(0f, 0f)
            path.lineTo(kotlin.math.cos(a1) * radius, kotlin.math.sin(a1) * radius)
            path.lineTo(kotlin.math.cos(a2) * radius, kotlin.math.sin(a2) * radius)
            path.close()
            facet.color = Palette.hsv(hues[i % hues.size] + puck.frame * 2f, 0.8f, 0.95f)
            canvas.drawPath(path, facet)
        }
        edge.strokeWidth = puck.strokePaint.strokeWidth * 0.6f

        path.reset()
        for (i in 0 until sides) {
            val a = (i * 360f / sides) * Math.PI.toFloat() / 180f
            val px = kotlin.math.cos(a) * radius
            val py = kotlin.math.sin(a) * radius
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, edge)
        canvas.restore()
    }
}
