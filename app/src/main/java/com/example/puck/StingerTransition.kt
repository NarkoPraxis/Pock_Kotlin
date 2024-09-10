package com.example.puck

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.InterstitialAd
import gameobjects.Settings
import physics.Ticker
import shapes.Circle

class StingerTransition(context: Context, var ad: InterstitialAd, var activity: AppCompatActivity) : View(context) {

    var screenWidth = 0f
    var screenHeight = 0f

    lateinit var rectangle: RectF
    lateinit var circle: Circle

    val ticker = Ticker(100)

    var testPaint : Paint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    fun transitionTo(activity: AppCompatActivity) {

    }

    override fun onScreenStateChanged(screenState: Int) {
        super.onScreenStateChanged(screenState)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w.toFloat()
        screenHeight = h.toFloat()

        rectangle = RectF(0f, -screenHeight, screenWidth, 0f)
        circle = Circle(screenWidth / 2f, screenWidth / 2f, -screenWidth / 2f, Color.RED, Color.MAGENTA)
    }

    var step = 100f

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(rectangle, testPaint)
        circle.drawTo(canvas)

        rectangle.offset(0f, step)
        circle.setLocation(circle.x, circle.y + step)

        if (circle.y > screenHeight + screenWidth / 2f) {
            step = -100f
        }
        if (circle.y < -screenWidth / 2f) {
            step = 100f
        }
    }
}


