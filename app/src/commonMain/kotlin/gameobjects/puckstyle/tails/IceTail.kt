package gameobjects.puckstyle.tails

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import utility.PaintBucket

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

    /**
     * Frozen screenshot of [render]'s live deque. Ice is a continuous two-layer trail (puddle +
     * ice crystal), not a particle spray, so each shard is posed one aging-step further along the
     * swoosh — the newest at the ball head — carrying the exact ice/puddle state [render] would have
     * aged it to. Drawn oldest→newest so the fresh ice crystal at the ball lands on top, matching the
     * live loop's overlap order. No jitter or randomness: the same recurrence as [render], frozen.
     */
    private fun renderStatic(scope: DrawScope) {
        val primaryColor = responsivePrimary
        val maxPuddleSize = renderer.radius * 1.5f
        val iceCutoff = renderer.radius * 0.05f

        // A shard lives until its life reaches 0, so the trail holds this many shards at steady state.
        val count = (1f / lifeDecrement).toInt().coerceAtLeast(2)
        val last = count - 1

        val cx = FloatArray(count); val cy = FloatArray(count)
        val puddleSizes = FloatArray(count); val puddleAlphas = IntArray(count)
        val iceSizes = FloatArray(count)

        // Walk the same aging math as render(), newest (i=0, at the ball) → oldest (i=last, tail tip).
        var iceSize = renderer.radius * 1.2f
        var puddleSize = renderer.radius * 0.3f
        var life = 1f
        var alive = 0
        for (i in 0 until count) {
            life -= lifeDecrement
            iceSize *= 0.95f
            if (life > 0.6f) puddleSize *= 1.2f else puddleSize *= 0.99f
            puddleSize = puddleSize.coerceIn(0f, maxPuddleSize)
            if (life <= 0f) break

            val pos = staticSwooshPoint(i.toFloat() / last)
            cx[i] = pos.x; cy[i] = pos.y
            puddleSizes[i] = puddleSize
            puddleAlphas[i] = (90f * life * (1f - life)).toInt().coerceIn(0, 180)
            iceSizes[i] = iceSize
            alive++
        }

        // Draw oldest→newest so the ball's fresh ice crystal renders on top, matching render().
        for (i in alive - 1 downTo 0) {
            scope.drawCircle(
                color = Color(Palette.withAlpha(primaryColor, puddleAlphas[i])),
                radius = puddleSizes[i],
                center = Offset(cx[i], cy[i])
            )
            if (iceSizes[i] > iceCutoff) {
                scope.drawCircle(PaintBucket.white, iceSizes[i], Offset(cx[i], cy[i]))
            }
        }
    }

    override fun clear() { shards.clear() }

    override fun fillTo(x: Float, y: Float) { shards.clear() }
}
