package shapes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

open class DrawablePoint(x: Float, y: Float, var pointColor: Int = 0xFF000000.toInt(), var size: Float = 10f) : Shape(x, y) {

    constructor() : this(0f, 0f, 0xFF000000.toInt(), 10f)
    constructor(circle: Circle) : this(circle.x, circle.y, circle.strokeColor, 10f)
    constructor(x: Float, y: Float) : this(x, y, 0xFF000000.toInt(), 10f)

    private var alpha: Float = 1f

    fun setColor(c: Int) { pointColor = c }
    fun setAlpha(c: Int) { alpha = c / 255f }

    fun drawTo(scope: DrawScope) {
        scope.drawCircle(
            color = Color(pointColor).copy(alpha = alpha),
            radius = size,
            center = Offset(x, y)
        )
    }

    override fun intersects(circle: Circle): Boolean = distanceTo(circle) < circle.radius
}
