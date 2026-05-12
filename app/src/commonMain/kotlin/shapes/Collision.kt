package shapes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import physics.Point
import physics.Ticker

class Collision(val collision: Point, val direction: Point, lineColor: Int) : Shape(collision.x, collision.y) {

    constructor() : this(Point(), Point(), 0) {
        r1 = kotlin.random.Random.nextFloat()
        r2 = kotlin.random.Random.nextFloat()
    }

    val timeTicker = Ticker(15, true)

    var r1 = 0f
    var r2 = 0f

    val finished: Boolean get() = timeTicker.tick

    private var lineColorInt: Int = lineColor

    fun DrawScope.drawTo() {
        if (!timeTicker.tick) {
            val x1 = direction.x
            val y1 = -direction.y
            val x2 = -direction.x
            val y2 = direction.y

            val starting = 0f
            val ending = 200 * timeTicker.ratio
            val color = Color(lineColorInt)
            drawLine(color, Offset(collision.x + starting * x1, collision.y + starting * y1), Offset(collision.x + ending * x1, collision.y + ending * y1), strokeWidth = 10f)
            drawLine(color, Offset(collision.x + starting * x2, collision.y + starting * y2), Offset(collision.x + ending * x2, collision.y + ending * y2), strokeWidth = 10f)
        } else if (timeTicker.accending) {
            timeTicker.accending = false
            timeTicker.reset()
        }
    }

    override fun intersects(circle: Circle): Boolean {
        throw Exception("Collision animation should not be checking for collision with circles")
    }
}
