package gameobjects.puckstyle.tails

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import utility.PaintBucket
import kotlin.random.Random

class IceTail(override val renderer: PuckRenderer) : TailRenderer {

    private class Shard(
        val x: Float,
        val y: Float,
        var iceSize: Float,
        var puddleSize: Float,
        var life: Float
    )

    private val shards = ArrayDeque<Shard>()
    private val maxShards = 120

    // Cached constants derived from Settings values that never change after init.
    private val maxCount = (maxShards * Settings.tailLengthMultiplier).toInt()
    private val lifeDecrement = 0.012f / Settings.tailLengthMultiplier

    override fun render(scope: DrawScope) {
        if (renderer.staticUiMode) { renderStatic(scope); return }
        shards.addLast(Shard(
            x = renderer.x,
            y = renderer.y,
            iceSize = renderer.radius * 1.2f,
            puddleSize = renderer.radius * 0.3f,
            life = 1f
        ))
        while (shards.size > maxCount) shards.removeFirst()

        // Resolve color and radius-derived thresholds once before the loop.
        val primaryColor = responsivePrimary
        val maxPuddleSize = renderer.radius * 1.5f
        val iceCutoff = renderer.radius * 0.05f

        var i = 0
        while (i < shards.size) {
            val s = shards[i]
            s.life -= lifeDecrement
            s.iceSize *= 0.95f
            if (s.life > 0.6f) {
                s.puddleSize *= 1.2f
            } else {
                s.puddleSize *= 0.99f
            }
            s.puddleSize = s.puddleSize.coerceIn(0f, maxPuddleSize)
            if (s.life <= 0f) {
                shards.removeAt(i)
                // do not increment i — the element at i is now the next shard
                continue
            }

            // Puddle layer — peaks at mid-life, then fades as water evaporates.
            val puddleAlpha = (90f * s.life * (1f - s.life)).toInt().coerceIn(0, 180)
            scope.drawCircle(
                color = Color(Palette.withAlpha(primaryColor, puddleAlpha)),
                radius = s.puddleSize,
                center = Offset(s.x, s.y)
            )

            // Ice crystal layer on top — shrinking white circle.
            if (s.iceSize > iceCutoff) {
                scope.drawCircle(
                    color = PaintBucket.white,
                    radius = s.iceSize,
                    center = Offset(s.x, s.y)
                )
            }
            i++
        }
    }

    /** Frozen "screenshot of motion": melt-puddles + ice crystals strewn along the swoosh. */
    private fun renderStatic(scope: DrawScope) {
        val primaryColor = responsivePrimary
        val count = maxCount.coerceIn(20, 90)
        val last = (count - 1).coerceAtLeast(1)
        val jitter = renderer.radius * 0.5f
        val iceCutoff = renderer.radius * 0.05f
        for (i in 0 until count) {
            val ratio = i.toFloat() / last
            val base = staticSwooshPoint(ratio)
            val rnd = Random(i + 1)
            val cx = base.x + (rnd.nextFloat() - 0.5f) * jitter
            val cy = base.y + (rnd.nextFloat() - 0.5f) * jitter
            val life = 1f - ratio

            val puddleAlpha = (120f * life * (0.4f + 0.6f * ratio)).toInt().coerceIn(0, 180)
            val puddleSize = renderer.radius * (0.4f + 0.9f * ratio)
            scope.drawCircle(Color(Palette.withAlpha(primaryColor, puddleAlpha)), puddleSize, Offset(cx, cy))

            val iceSize = renderer.radius * 1.1f * life
            if (iceSize > iceCutoff) {
                scope.drawCircle(PaintBucket.white, iceSize, Offset(cx, cy))
            }
        }
    }

    override fun clear() { shards.clear() }

    override fun fillTo(x: Float, y: Float) { shards.clear() }
}
