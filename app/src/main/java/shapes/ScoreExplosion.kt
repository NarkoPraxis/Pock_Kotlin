package shapes

import android.graphics.Canvas
import android.graphics.Paint
import physics.Point
import physics.Ticker
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ScoreExplosion(var scoringColor: Int, var scoredColor: Int, var position: Point, var radius: Float, highGoal: Boolean){
    constructor() : this(0, 0, Point(), 0f,false)

    var ticker = Ticker(100)

    var scoringPaint : Paint = Paint().apply {
        color = scoringColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 12f
    }

    var scoredPaint : Paint = Paint().apply {
        color = scoredColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 12f
    }

    private val directions = MutableList(0) { Point() }
    private val step = 10f
    private var currentDistance = 0f
    private var drawScored = true
    var finished = false

    init {
        for(angle in listOf(0, .523599, 1.0472, 1.5708, 2.0944, 2.61799, PI)) {
            var a = angle.toString().toFloat()
            if (highGoal) {
                directions.add(Point(cos(a), sin(a)))
            } else {
                directions.add(-Point(cos(a), sin(a)))
            }
        }
    }

    fun drawTo(canvas: Canvas) {
        if (finished) return
        for(direction in directions) {
            canvas.drawCircle(position.x + direction.x * currentDistance, position.y + direction.y * currentDistance, radius, scoringPaint)
            if (drawScored) {
                canvas.drawCircle(position.x + direction.x * currentDistance, position.y + direction.y * currentDistance, radius, scoredPaint)
            }
        }
        if (drawScored) {
            currentDistance += step
            scoredPaint.alpha = scoredPaint.alpha - step.toInt()
            if(scoredPaint.alpha < step) {
                drawScored = false
            }
        } else {
            currentDistance += step / 2
            scoringPaint.alpha = scoringPaint.alpha - step.toInt()
            if (scoringPaint.alpha < step) {
                finished = true
            }
        }
    }

}