package gameobjects

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.puck.R
import utility.PaintBucket
import utility.Tutorial


class PauseMenu(activity: AppCompatActivity) {

    val settingsImage: Drawable?
    val resetImage: Drawable?
    val backImage: Drawable?
    val nextImage: Drawable?

    private val iconSize = (Settings.screenRatio * 3f).toInt()
    private val iconHalf = iconSize / 2

    // Center icons vertically in the bottom goal zone
    private val iconCenterY = ((Settings.bottomGoalTop + Settings.screenHeight) / 2f).toInt()
    val topY = iconCenterY - iconHalf
    val bottomY = iconCenterY + iconHalf

    // Distribute icons at left-third, center, and right-third of screen
    val leftXLeft = (Settings.screenWidth / 6f - iconHalf).toInt()
    val leftXRight = (Settings.screenWidth / 6f + iconHalf).toInt()
    val middleXLeft = (Settings.screenWidth / 2f - iconHalf).toInt()
    val middleXRight = (Settings.screenWidth / 2f + iconHalf).toInt()
    val rightXLeft = (5f * Settings.screenWidth / 6f - iconHalf).toInt()
    val rightXRight = (5f * Settings.screenWidth / 6f + iconHalf).toInt()

    private val debug = Paint().apply {
        color = PaintBucket.effectColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth + Settings.strokeWidth
    }

    init {
        settingsImage = ContextCompat.getDrawable(activity, R.drawable.ic_settings_24dp)
        resetImage = ContextCompat.getDrawable(activity, R.drawable.ic_reset_24dp)
        backImage = ContextCompat.getDrawable(activity, R.drawable.ic_arrow_back_black_24dp)
        nextImage = ContextCompat.getDrawable(activity, R.drawable.ic_baseline_navigate_next_24)
    }

    fun drawTo(canvas: Canvas) {
        for (x in 0..1) {
            if (x==1) {
                canvas.save()
                canvas.scale(-1f, -1f, Settings.screenWidth / 2, Settings.screenHeight / 2)
            }
//        canvas.drawText("We Are Drawing", leftXLeft.toFloat(), topY.toFloat(), debug)
            if (Settings.tutorialPaused) {
                canvas.drawText("Next", Settings.middleX / 2f, Settings.bottomGoalTop, PaintBucket.tutorialStrokePaint)
                nextImage?.setBounds(middleXLeft, topY, middleXRight, bottomY)
                nextImage?.draw(canvas)
            }
            else {
                canvas.drawRect(0f, Settings.bottomGoalTop, Settings.screenWidth, Settings.screenHeight, debug)
                settingsImage?.setBounds(leftXLeft, topY, leftXRight, bottomY)
                settingsImage?.draw(canvas)
                resetImage?.setBounds(middleXLeft, topY, middleXRight, bottomY)
                resetImage?.draw(canvas)
                backImage?.setBounds(rightXLeft, topY, rightXRight, bottomY)
                backImage?.draw(canvas)
            }

            if (x==1) {
                canvas.restore()
            }
        }
    }



//    fun drawTo(canvas: Canvas) {
//        draw(canvas, ::drawPauseMenu, top)
//    }
//
//    fun drawPauseMenu(canvas: Canvas) {
//        canvas.drawRect(0f, Settings.topGoalBottom, Settings.screenWidth, Settings.screenHeight, debug)
//        settingsImage?.setBounds(leftXLeft, topY, leftXRight, bottomY)
//        settingsImage?.draw(canvas)
//        resetImage?.setBounds(middleXLeft, topY, middleXRight, bottomY)
//        resetImage?.draw(canvas)
//        backImage?.setBounds(rightXLeft, topY, rightXRight, bottomY)
//        backImage?.draw(canvas)
//    }
//
//    private fun draw(canvas: Canvas, drawTo: (c: Canvas)-> Unit, mirror: Boolean = false) {
//        if (mirror) {
//            canvas.save()
//            canvas.scale(-1f, -1f, Settings.screenWidth / 2, Settings.screenHeight / 2)
//        }
//        drawTo(canvas)
//
//        if (mirror) {
//            canvas.restore()
//        }
//    }

}