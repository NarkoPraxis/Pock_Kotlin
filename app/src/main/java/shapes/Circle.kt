package shapes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import physics.Point

open class Circle(var radius: Float, x: Float, y: Float, var fillColor: Int, var strokeColor: Int) : Shape(x, y) {

constructor() : this(0f, 0f, 0f, Color.BLACK, Color.BLACK)

    var fillPaint = Paint().apply {
        color = fillColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    var strokePaint = Paint().apply {
        color = strokeColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    override fun intersects(circle: Circle) : Boolean {
        return distanceTo(circle) < (radius + circle.radius)
    }

    fun intersectionPoint(circle: Circle) : Point {
        val direction = directionTo(circle)
        return Point(x + direction.x * radius, y + direction.y * radius)
    }

    fun overlapLength(circle: Circle) : Float {
        val distance = distanceTo(circle)
        return (radius - (distance - circle.radius))
    }

    override fun drawTo(canvas: Canvas) {
        canvas.drawCircle(x, y, radius, fillPaint)
//        canvas.drawCircle(x, y, radius-strokePaint.strokeWidth/5, strokePaint)
        canvas.drawCircle(x, y, radius, strokePaint)
    }

    fun drawTo(radius: Float, canvas: Canvas) {
        canvas.drawCircle(x, y, radius, fillPaint)
//        canvas.drawCircle(x, y, radius-strokePaint.strokeWidth/5, strokePaint)
        canvas.drawCircle(x, y, radius, strokePaint)
    }

    fun setStroke(stroke: Int) {
        strokeColor = stroke
        strokePaint.apply { color = strokeColor }
//        strokePaint.apply { alpha = 50 }
    }

    fun setFill(fill: Int) {
        fillColor = fill
        fillPaint.apply { color = fillColor }
    }

    fun setAlpha(value: Int) {
        strokePaint.apply { alpha = value }
        fillPaint.apply   { alpha = value}
    }
}