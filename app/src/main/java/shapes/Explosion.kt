package shapes

import android.graphics.*
import android.os.Build
import enums.Direction
import physics.Point
import physics.Ticker

class Explosion(var firstColor: Int, var secondColor: Int, var backgroundColor: Int, var position: Point, var radius: Float, var persistant: Boolean, var angle: Direction = Direction.FULL, var alpha: Int = 255) {
    constructor() : this(0, 0, 0, Point(), 0f, false)

    var firstPaint : Paint = Paint().apply {
        color = firstColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 12f
    }
    var secondPaint : Paint = Paint().apply {
       color = secondColor
       isAntiAlias = true
       isDither = true
       style = Paint.Style.FILL
       strokeJoin = Paint.Join.ROUND
       strokeCap = Paint.Cap.ROUND
       strokeWidth = 12f
    }
    var backgroundPaint : Paint = Paint().apply {
        color = backgroundColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 12f
    }
    var testPaint : Paint = Paint().apply {
       color = Color.RED
       isAntiAlias = true
       isDither = true
       style = Paint.Style.FILL
       strokeJoin = Paint.Join.ROUND
       strokeCap = Paint.Cap.ROUND
       strokeWidth = 12f
    }
    var box : RectF
    var imploding = false
    var ratio = 0f
    val speed = 9
    var firstTicker = Ticker(speed, true)
    var secondTicker = Ticker(speed, true)
    var thirdTicker = Ticker(speed, true)
    var spacer1 = 2
    var spacer2 = 10
    var doneExploding = false
    var currentAlpha = 255
    var outerRing = Path()
    var innerRing = Path()
    var clipPath = Path()

    val finished : Boolean
        get() = doneExploding

    init  {
        box = RectF(position.x - radius, position.y - radius, position.x + radius, position.y + radius)
    }

    fun getColor() : Int {
        return firstColor
    }

    /**
     * A 32bit color not a color resources.
     * @param newColor
     */
    fun setColor(newColor: Int) {
        firstColor = newColor
        firstPaint.apply {color = firstColor }
    }

    fun drawTo(canvas: Canvas) {
        if (doneExploding) return


        outerRing.reset()
        outerRing.moveTo(position.x, position.y)
        if (firstTicker.finished){
            if (persistant) {
                firstPaint.alpha = currentAlpha
                if (currentAlpha > alpha) currentAlpha--
            }
            setBox(radius)
        } else {
            setRatio(firstTicker)
        }
        drawExplosion(outerRing)

        if (spacer1 <= 0) {
            innerRing.reset()
            innerRing.moveTo(position.x, position.y)
            if (secondTicker.finished){
                if (persistant) {
                    secondPaint.alpha = currentAlpha
                    if (currentAlpha > alpha) currentAlpha--
                }
                if (imploding) {
                    doneExploding = true
                }
                setBox(radius)
            } else {
                setRatio(secondTicker)
            }
            drawExplosion(innerRing)
        } else {
            spacer1--
        }

        if (!persistant && !imploding && spacer2 <= 0) {
            clipPath.reset()
            clipPath.moveTo(position.x, position.y)
            if(thirdTicker.finished) {
                doneExploding = true
                setBox(radius)
            } else {
                setRatio(thirdTicker)
            }
            drawExplosion(clipPath)
        }
        else {
            spacer2--
        }


        canvas.save()
        canvas.clipPath(outerRing)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipOutPath(clipPath)
        }
        else {
            canvas.clipPath(clipPath, Region.Op.DIFFERENCE)
        }
        canvas.drawPath(outerRing, firstPaint)
        canvas.restore()
        canvas.save()
        canvas.clipPath(innerRing)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipOutPath(clipPath)
        }
        else {
            canvas.clipPath(clipPath, Region.Op.DIFFERENCE)
        }
        canvas.drawPath(innerRing, secondPaint)
        canvas.restore()
    }


//    fun drawTo(canvas: Canvas) {
//        if (doneExploding) return
//
//        if (firstTicker.finished){
//            if (persistant) {
//                firstPaint.alpha = currentAlpha
//                if (currentAlpha > alpha) currentAlpha--
//            }
//            setBox(radius)
//        } else {
//            setRatio(firstTicker)
//        }
//        drawExplosion(canvas, firstPaint)
//
//        if (spacer1 <= 0) {
//            if (secondTicker.finished){
//                if (persistant) {
//                    secondPaint.alpha = currentAlpha
//                    if (currentAlpha > alpha) currentAlpha--
//                }
//                if (imploding) {
//                    doneExploding = true
//                }
//                setBox(radius)
//            } else {
//                setRatio(secondTicker)
//            }
//            drawExplosion(canvas, secondPaint)
//        } else {
//            spacer1--
//        }
//
//        if (!persistant && !imploding && spacer2 <= 0) {
//            if(thirdTicker.finished) {
//                doneExploding = true
//                setBox(radius)
//            } else {
//                setRatio(thirdTicker)
//            }
//            drawExplosion(canvas, backgroundPaint)
//        }
//        else {
//            spacer2--
//        }
//    }

    fun implode() {
        firstTicker.accending = false
        firstTicker.reset(100)
        secondTicker.accending = false
        secondTicker.reset(100)
        thirdTicker.accending = false
        thirdTicker.reset(100)
        persistant = false
        imploding = true
    }

    private fun drawExplosion(path: Path) {
        when (angle) {
            Direction.LEFT -> path.addArc(box,90f,180f)
            Direction.TOP -> path.addArc(box, -180f, 180f)
            Direction.RIGHT -> path.addArc(box, 270f, 180f)
            Direction.BOTTOM -> path.addArc(box, 0f, 180f)
            else -> path.addCircle(position.x, position.y, ratio, Path.Direction.CCW)
        }
    }

//    private fun drawExplosion(canvas: Canvas, paint: Paint) {
//        when (angle) {
//            Direction.LEFT -> canvas.drawArc(box,90f,180f,true, paint)
//            Direction.TOP -> canvas.drawArc(box, -180f, 180f, true, paint)
//            Direction.RIGHT -> canvas.drawArc(box, 270f, 180f, true, paint)
//            Direction.BOTTOM -> canvas.drawArc(box, 0f, 180f, true, paint)
//            else -> canvas.drawCircle(position.x, position.y, ratio, paint)
//        }
//    }

    private fun setRatio(ticker: Ticker) {
        if (!ticker.tick) {
            ratio = radius * ticker.ratio
        }

        setBox(ratio)
    }

    private fun setBox(ratio: Float) {
        box.left = position.x - ratio
        box.right = position.x + ratio
        box.top = position.y - ratio
        box.bottom = position.y + ratio
    }
}