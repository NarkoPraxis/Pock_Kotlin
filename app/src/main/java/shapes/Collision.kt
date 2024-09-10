package shapes

import android.graphics.Canvas
import android.graphics.Paint
import physics.Point
import physics.Ticker

class Collision(val collision: Point, val direction: Point, lineColor: Int) : Shape(collision.x, collision.y) {

    constructor() : this(Point(), Point(), 0) {
        r1 = Math.random().toFloat()
        r2 = Math.random().toFloat()
    }

    val timeTicker = Ticker(15, true)

    var r1 = 0f
    var r2 = 0f

    val finished : Boolean
        get() = timeTicker.tick

    var fillPaint = Paint().apply {
        color = lineColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 10f
    }

    override fun drawTo(canvas: Canvas) {
        if (!timeTicker.tick) {
            val x1 = direction.x
            val y1 = -direction.y
            val x2 = -direction.x
            val y2 = direction.y

            val starting = 0f
            val ending = 200 * (timeTicker.ratio)
            canvas.drawLine(collision.x + starting*x1, collision.y+ starting*y1, collision.x + ending*x1, collision.y + ending*y1, fillPaint)
            canvas.drawLine(collision.x + starting*x2, collision.y+ starting*y2, collision.x + ending*x2, collision.y + ending*y2, fillPaint)
//            canvas.drawLine(collision.x + starting*(x1 + r1), collision.y+ starting*(y1 + r1), collision.x + ending*(x1 + r1), collision.y + ending*(y1 + r1), fillPaint)
//            canvas.drawLine(collision.x + starting*(x2 + r1), collision.y+ starting*(y2 + r1), collision.x + ending*(x2 + r1), collision.y + ending*(y2 + r1), fillPaint)
//            canvas.drawLine(collision.x + starting*(x1 + r2), collision.y+ starting*(y1 + r2), collision.x + ending*(x1 + r2), collision.y + ending*(y1 + r2), fillPaint)
//            canvas.drawLine(collision.x + starting*(x2 + r2), collision.y+ starting*(y2 + r2), collision.x + ending*(x2 + r2), collision.y + ending*(y2 + r2), fillPaint)
        }
        else if (timeTicker.accending) {
            timeTicker.accending = false
            timeTicker.reset()
        }
    }

    override fun intersects(circle: Circle): Boolean {
        throw Exception("Collision animation should not be checking for collision with circles")
    }


}