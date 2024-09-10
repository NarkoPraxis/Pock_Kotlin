package shapes

import android.graphics.*
import enums.FingerState
import gameobjects.Settings
import physics.Point
import physics.Ticker
import utility.PaintBucket
import kotlin.math.abs

class HandSelection(var position: Point, var strokeColor: Int, var fillColor: Int, var backgroundColor: Int, var isRightHand: Boolean, var flipped: Boolean = false) {

    constructor(isRightHand: Boolean, flipped: Boolean) : this(Point(), 0, 0, 0,isRightHand, flipped)

    private val handOutline = Path()
    private val fingerFill = Path()
    private val thumbFill = Path()
    private val xStep : Float =  Settings.screenRatio * 1.5f * if (isRightHand && !flipped || !isRightHand && flipped) -1 else 1
    private val largeXStep = xStep * 2
    private val largestXStep = largeXStep * 2
    private val smallXStep = xStep / 2f
    private val smallestXStep = smallXStep / 2f
    private val yStep = abs(xStep) * if (flipped) -1 else 1
    private val largeYStep = abs(largeXStep) * if (flipped) -1 else 1
    private val largestYStep = abs(largestXStep) * if (flipped) -1 else 1
    private val smallYStep = abs(smallXStep) * if (flipped) -1 else 1
    private val smallestYStep = abs(smallestXStep) * if (flipped) -1 else 1

    private var x = position.x
    private var y = position.y

    private val adjustedX = position.x + largeXStep
    private val adjustedY = position.y - largeYStep

    private val pointerShelf = RectF(adjustedX, adjustedY - largestYStep, adjustedX + largeXStep + smallestXStep, adjustedY - yStep - smallYStep)
    private val pointerFinger = RectF(adjustedX - largestXStep, adjustedY - largestYStep, adjustedX, adjustedY + smallXStep)
    private val thumbFinger = RectF(adjustedX, adjustedY - yStep - smallYStep, adjustedX + largeXStep + smallestXStep, adjustedY + largestYStep)
    private val thumbShelf = RectF(adjustedX - largestXStep, adjustedY + smallXStep, adjustedX, adjustedY + largestYStep)

    var fingerState: FingerState = FingerState.Unselected
    var lockedIn = false

    val ticker = Ticker(100, true)

    init {
        initialize()
    }

    var strokePaint : Paint = Paint().apply {
        color = strokeColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    var effectPaint : Paint = Paint().apply {
        color = PaintBucket.effectColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    var fillEffectPaint : Paint = Paint().apply {
        color = PaintBucket.effectColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL_AND_STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    var fillPaint : Paint = Paint().apply {
        color = fillColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    var testPaintPurple : Paint = Paint().apply {
        color = Color.MAGENTA
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    var testPaint : Paint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    fun drawTo(canvas: Canvas) {

//        canvas.drawRect(thumbShelf, effectPaint)
//        canvas.drawRect(thumbFinger, effectPaint)
//        canvas.drawRect(pointerShelf, testPaintPurple)
//        canvas.drawRect(pointerFinger, testPaintPurple)

        if (lockedIn) {
            if (fingerState == FingerState.LeftPointer || fingerState == FingerState.RightPointer) {
                canvas.drawPath(fingerFill, fillPaint)
            }
            if (fingerState == FingerState.LeftThumb || fingerState == FingerState.RightThumb) {
                canvas.drawPath(thumbFill, fillPaint)
            }
        }
        canvas.drawPath(handOutline, strokePaint)
    }

    private fun initialize() {
        handOutline.reset()
        fingerFill.reset()
        thumbFill.reset()
        x = position.x
        y = position.y
        handOutline.moveTo(position.x+largeXStep, position.y - yStep - smallYStep)

        arcThumb(handOutline)
        arcPalm()
        arcFingerPoint()
        step(0f, smallestYStep)
        step(0f, -smallYStep)
        arcFingerPoint()
        step(0f, smallestYStep)
        step(0f, -smallYStep)
        arcFingerPoint()
        step(0f, smallestYStep)
        step(0f, -largeYStep)
        arcFingerTip(handOutline)
        step(0f, largeYStep + yStep + smallYStep)
        handOutline.close()

        thumbFill.moveTo(position.x + largeXStep, position.y - yStep - smallYStep)
        arcThumb(thumbFill)
        thumbFill.rCubicTo(0f, 0f, -smallestXStep, -yStep, 0f, -largeYStep)
        thumbFill.close()

        fingerFill.moveTo(position.x + largeXStep - xStep, position.y - largeYStep + smallestYStep + smallestYStep / 3f)
        fingerFill.rLineTo(0f, -largestYStep + yStep - smallYStep + smallestYStep)
        arcFingerTip(fingerFill)
        fingerFill.rLineTo(0f, largestYStep - yStep + smallYStep - smallestYStep)
        fingerFill.rCubicTo(0f, 0f, -smallestXStep, smallestYStep, -smallXStep, smallestYStep)
        fingerFill.rCubicTo(0f, 0f, -smallestXStep - smallestXStep * .9f, 0f, -smallXStep, -smallYStep)

        fingerFill.close()
    }

    private fun step(deltaX: Float, deltaY:Float) {
        handOutline.rLineTo(deltaX,deltaY)
    }

    private fun arcThumb(path: Path) {
        path.rCubicTo(0f, 0f, smallXStep, -smallYStep - smallestYStep, xStep, -yStep)
        path.rCubicTo(0f, 0f, smallXStep + smallestXStep, -smallestYStep, smallXStep, smallYStep)
        path.rCubicTo(0f, 0f, -xStep + smallestXStep, largeYStep, -xStep-smallXStep, largeYStep + yStep-smallestYStep)
    }

    private fun arcFingerPoint() {
        handOutline.rCubicTo(0f, 0f, smallestXStep /4f, -smallestYStep - (smallestYStep / 2f), smallXStep, -smallestYStep - (smallestYStep / 2f))
        handOutline.rCubicTo(0f, 0f, (5 * smallestXStep /4f), 0f, smallXStep,  smallestYStep )
    }

    private fun arcPalm() {
        handOutline.rCubicTo(0f, 0f, -smallXStep, smallYStep, -largeXStep, smallYStep)
        handOutline.rCubicTo(0f, 0f, -xStep-smallXStep, 0f, -largeXStep+smallestXStep, -smallYStep)
        handOutline.rCubicTo(0f, 0f, -smallXStep, -smallestYStep, -smallestXStep, -largeYStep - yStep)
    }

    private fun arcFingerTip(path: Path) {
        path.rCubicTo(0f, 0f, smallestXStep /4f, -smallestYStep - (smallestYStep / 2f), smallXStep, -smallestYStep - (smallestYStep / 2f))
        path.rCubicTo(0f, 0f, smallestXStep + (3 * smallestXStep / 4f), 0f, smallXStep,  smallYStep )
    }

    fun getFinger(x: Float, y: Float) : FingerState {
        return if (((pointerFinger.left <= x && x <= pointerFinger.right && pointerFinger.top <= y && y <= pointerFinger.bottom) ||
                    (pointerFinger.right <= x && x <= pointerFinger.left && pointerFinger.bottom <= y && y <= pointerFinger.top) ||
                    (pointerFinger.left <= x && x <= pointerFinger.right && pointerFinger.bottom <= y && y <= pointerFinger.top) ||
                    (pointerFinger.right <= x && x <= pointerFinger.left && pointerFinger.top <= y && y <= pointerFinger.bottom)) ||
                   ((pointerShelf.left <= x && x <= pointerShelf.right && pointerShelf.top <= y && y <= pointerShelf.bottom) ||
                    (pointerShelf.right <= x && x <= pointerShelf.left && pointerShelf.bottom <= y && y <= pointerShelf.top) ||
                    (pointerShelf.left <= x && x <= pointerShelf.right && pointerShelf.bottom <= y && y <= pointerShelf.top) ||
                    (pointerShelf.right <= x && x <= pointerShelf.left && pointerShelf.top <= y && y <= pointerShelf.bottom))) {
            if (isRightHand) FingerState.RightPointer else FingerState.LeftPointer
        }else if (((thumbFinger.left <= x && x <= thumbFinger.right && thumbFinger.top <= y && y <= thumbFinger.bottom) ||
                    (thumbFinger.right <= x && x <= thumbFinger.left && thumbFinger.bottom <= y && y <= thumbFinger.top) ||
                    (thumbFinger.left <= x && x <= thumbFinger.right && thumbFinger.bottom <= y && y <= thumbFinger.top) ||
                    (thumbFinger.right <= x && x <= thumbFinger.left && thumbFinger.top <= y && y <= thumbFinger.bottom)) ||
                   ((thumbShelf.left <= x && x <= thumbShelf.right && thumbShelf.top <= y && y <= thumbShelf.bottom) ||
                    (thumbShelf.right <= x && x <= thumbShelf.left && thumbShelf.bottom <= y && y <= thumbShelf.top) ||
                    (thumbShelf.left <= x && x <= thumbShelf.right && thumbShelf.bottom <= y && y <= thumbShelf.top) ||
                    (thumbShelf.right <= x && x <= thumbShelf.left && thumbShelf.top <= y && y <= thumbShelf.bottom))) {
            if (isRightHand) FingerState.RightThumb else FingerState.LeftThumb
        } else {
            FingerState.Unselected
        }
    }




    fun reset() {
        if (lockedIn) {
            ticker.reset()
            lockedIn = false
            ticker.accending = true

        }
    }

    fun selected() : Boolean  {
        if (!lockedIn) {
            ticker.accending = true
            if (ticker.finished) {
                return true
            }
            initialize()
            return ticker.tick
        }
        return true
    }

    fun deselect() {
        if (!lockedIn) {
            ticker.accending = false
            initialize()
            if (!ticker.finished)
            {
                ticker.tick

            }
        }
    }

    fun lockIn(fingerState: FingerState) {
        lockedIn = true
        this.fingerState = fingerState
    }

    fun unlock() {
        lockedIn = false
        fingerState = FingerState.Unselected
    }

    fun adjustedY(y: Float, radius: Float) : Float {
        val attemptedAdjustment = 8 * radius * ticker.ratio
        return if (attemptedAdjustment < 2 * radius) y - attemptedAdjustment else y - 2 * radius
    }

}