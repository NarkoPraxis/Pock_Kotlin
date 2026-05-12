package gameobjects.puckstyle.tails

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer

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
                    color = Color.White,
                    radius = s.iceSize,
                    center = Offset(s.x, s.y)
                )
            }
            i++
        }
    }

    override fun clear() { shards.clear() }

    override fun fillTo(x: Float, y: Float) { shards.clear() }
}
