package shapes

import android.graphics.Canvas
import physics.Point


//parent class for all shapes, defines functions and location
abstract class Shape(x: Float, y: Float) : Point(x, y) {
    abstract fun drawTo(canvas: Canvas)
    abstract fun intersects(circle: Circle): Boolean
}