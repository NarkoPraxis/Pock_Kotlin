package shapes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.Settings
import physics.Point

open class Circle(var radius: Float, x: Float, y: Float, var fillColor: Int, var strokeColor: Int) : Shape(x, y) {

    constructor() : this(0f, 0f, 0f, 0, 0)

    private var alphaValue: Float = 1f

    override fun intersects(circle: Circle): Boolean = distanceTo(circle) < (radius + circle.radius)

    fun intersectionPoint(circle: Circle): Point {
        val direction = directionTo(circle)
        return Point(x + direction.x * radius, y + direction.y * radius)
    }

    fun overlapLength(circle: Circle): Float {
        val distance = distanceTo(circle)
        return (radius - (distance - circle.radius))
    }

    open fun DrawScope.drawTo() {
        val center = Offset(x, y)
        drawCircle(color = Color(fillColor).copy(alpha = alphaValue), radius = radius, center = center)
        drawCircle(
            color = Color(strokeColor).copy(alpha = alphaValue),
            radius = radius,
            center = center,
            style = Stroke(width = Settings.strokeWidth)
        )
    }

    open fun DrawScope.drawTo(r: Float) {
        val center = Offset(x, y)
        drawCircle(color = Color(fillColor).copy(alpha = alphaValue), radius = r, center = center)
        drawCircle(
            color = Color(strokeColor).copy(alpha = alphaValue),
            radius = r,
            center = center,
            style = Stroke(width = Settings.strokeWidth)
        )
    }

    open fun setStroke(stroke: Int) { strokeColor = stroke }
    open fun setFill(fill: Int) { fillColor = fill }

    fun setAlpha(value: Int) { alphaValue = value / 255f }
}
