package shapes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

open class DrawablePoint(x: Float, y: Float, var pointColor: Int = Color.BLACK, var size: Float = 10f) : Shape(x,y) {

    constructor() : this(0f, 0f, Color.BLACK, 10f)

    constructor(circle: Circle) : this(circle.x, circle.y, circle.strokeColor, 10f)

    constructor(x: Float, y:Float) : this(x, y, Color.BLACK, 10f)

    private var paint = Paint().apply {
        color = pointColor
    }

    fun setColor(c: Int) {
        pointColor = c
        paint.apply { color = pointColor }
    }

    fun setAlpha(c: Int) {
        paint.apply { alpha = c}
    }

    override fun drawTo(canvas: Canvas) {
        canvas.drawCircle(x, y, size, paint)
    }

    override fun intersects(circle: Circle) : Boolean {
        return distanceTo(circle) < circle.radius
    }


}