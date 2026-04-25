package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer

class IceTail(override val theme: ColorTheme) : TailRenderer {

    private class Shard(
        val x: Float,
        val y: Float,
        var iceSize: Float,
        var puddleSize: Float,
        var life: Float
    )

    private val shards = ArrayDeque<Shard>()
    private val maxShards = 120
    private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    override fun render(canvas: Canvas, renderer: PuckRenderer) {
        shards.addLast(Shard(
            x = renderer.x,
            y = renderer.y,
            iceSize = renderer.radius * 1.2f,
            puddleSize = renderer.radius * 0.3f,
            life = 1f
        ))
        while (shards.size > (maxShards * Settings.tailLengthMultiplier).toInt()) shards.removeFirst()

        val it = shards.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.life -= 0.012f / Settings.tailLengthMultiplier
            s.iceSize *= 0.95f
            if (s.life > 0.6f) {
                s.puddleSize *= 1.2f
            } else {
                s.puddleSize *= 0.99f
            }
            s.puddleSize = s.puddleSize.coerceIn(0f, renderer.radius * 1.5f)
            if (s.life <= 0f) { it.remove(); continue }

            // Puddle layer — peaks at mid-life, then fades as water evaporates
            val puddleAlpha = (90f * s.life * (1f - s.life)).toInt().coerceIn(0, 180)
            paint.color = Palette.withAlpha(resolvedColors(renderer).primary, puddleAlpha)
            canvas.drawCircle(s.x, s.y, s.puddleSize, paint)

            // Ice crystal layer on top — shrinking white circle
            if (s.iceSize > renderer.radius * 0.05f) {
                paint.color = Palette.withAlpha(Color.WHITE, 255)
                canvas.drawCircle(s.x, s.y, s.iceSize, paint)
            }
        }
    }

    override fun clear() { shards.clear() }

    override fun fillTo(x: Float, y: Float) { shards.clear() }
}
