package com.example.puck

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import gameobjects.Settings
import utility.Drawing
import utility.Logic
import utility.PaintBucket
import utility.Storage

class ScoreCalibrationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Storage.darkMode) setTheme(R.style.SettingsThemeDark) else setTheme(R.style.SettingsThemeLight)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val view = ScoreCalibrationView(this)
        @Suppress("DEPRECATION")
        view.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(view)
    }
}

@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class ScoreCalibrationView(context: Context) : View(context) {

    private var highOffset = Storage.scoreOffsetHigh.toFloat()
    private var lowOffset = Storage.scoreOffsetLow.toFloat()

    private enum class TouchZone { High, Low, Button, None }
    private var activeTouchZone = TouchZone.None
    private var lastTouchX = 0f
    private var activePointerId = -1

    private val saveButtonRect = RectF()

    private val scorePaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val hintPaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(140, 0, 0, 0)
    }
    private val btnFillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val btnTextPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val bgPaint = Paint().apply { style = Paint.Style.FILL }
    private val goalPaint = Paint().apply { style = Paint.Style.FILL }
    private val wallPaint = Paint().apply { style = Paint.Style.FILL }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (Settings.screenRatio == 0f) {
            Logic.initializeSettings(w, h)
        }
        PaintBucket.initialize(resources)
        Drawing.initialize()

        scorePaint.textSize = Settings.topGoalBottom * 0.85f
        hintPaint.textSize = Settings.screenRatio * 0.85f

        bgPaint.color = PaintBucket.backgroundPaint.color
        goalPaint.color = PaintBucket.goalColor
        wallPaint.color = PaintBucket.effectColor

        val tv = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorSecondary, tv, true)
        btnFillPaint.color = tv.data


        // Save button: same proportions as main menu buttons (260dp × 56dp, 28dp radius)
        val density = resources.displayMetrics.density
        val btnW = 260f * density
        val btnH = 56f * density
        btnTextPaint.textSize = 18f * density
        val cx = Settings.screenWidth / 2f
        val cy = Settings.screenHeight / 2f
        saveButtonRect.set(cx - btnW / 2f, cy - btnH / 2f, cx + btnW / 2f, cy + btnH / 2f)

        // Sync offsets in case initializeSettings loaded them into Settings
        highOffset = Settings.scoreOffsetHigh
        lowOffset = Settings.scoreOffsetLow
    }

    override fun onDraw(canvas: Canvas) {
        val sw = Settings.screenWidth
        val sh = Settings.screenHeight
        val tgb = Settings.topGoalBottom
        val bgt = Settings.bottomGoalTop
        val cx = sw / 2f
        val cy = sh / 2f
        val wallThick = Settings.shortParticleSide

        // Background and goal zones
        canvas.drawRect(0f, 0f, sw, sh, bgPaint)
        canvas.drawRect(0f, 0f, sw, tgb, goalPaint)
        canvas.drawRect(0f, bgt, sw, sh, goalPaint)

        // Static arena walls
        canvas.drawRect(0f, tgb - wallThick, sw, tgb, wallPaint)
        canvas.drawRect(0f, bgt, sw, bgt + wallThick, wallPaint)
        canvas.drawRect(0f, tgb, wallThick, bgt, wallPaint)
        canvas.drawRect(sw - wallThick, tgb, sw, bgt, wallPaint)

        val xMargin = Settings.screenRatio * 3f
        val yMargin = tgb * 0.2f
        val scoreY = sh - yMargin
        val scoreText = "5"

        // Low player score
        val lowDrawX = clampToScreen(xMargin + lowOffset, scoreText)
        canvas.drawText(scoreText, lowDrawX, scoreY, scorePaint)

        // High player score (mirrored)
        canvas.save()
        canvas.scale(-1f, -1f, cx, cy)
        val highDrawX = clampToScreen(xMargin + highOffset, scoreText)
        canvas.drawText(scoreText, highDrawX, scoreY, scorePaint)
        canvas.restore()

        // Drag hints — centered in each goal zone, mirrored for high player
        val lowGoalCenterY = bgt + (sh - bgt) / 2f
        canvas.drawText("← drag to position score →", cx, lowGoalCenterY, hintPaint)

        canvas.save()
        canvas.scale(-1f, -1f, cx, tgb / 2f)
        canvas.drawText("← drag to position score →", cx, tgb / 2f, hintPaint)
        canvas.restore()

        // Save Positions button in play area center
        val cornerRadius = saveButtonRect.height() / 2f
        canvas.drawRoundRect(saveButtonRect, cornerRadius, cornerRadius, btnFillPaint)
        val textY = cy - (btnTextPaint.ascent() + btnTextPaint.descent()) / 2f
        canvas.drawText("Save Positions", cx, textY, btnTextPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                activeTouchZone = when {
                    saveButtonRect.contains(event.x, event.y) -> TouchZone.Button
                    event.y < Settings.screenHeight / 2f -> TouchZone.High
                    else -> TouchZone.Low
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeTouchZone != TouchZone.High && activeTouchZone != TouchZone.Low) return true
                val idx = event.findPointerIndex(activePointerId)
                if (idx < 0) return true
                val dx = event.getX(idx) - lastTouchX
                lastTouchX = event.getX(idx)
                val xMargin = Settings.screenRatio * 3f
                val textWidth = scorePaint.measureText("5")
                val minOffset = -xMargin
                val maxOffset = Settings.screenWidth - xMargin - textWidth
                if (activeTouchZone == TouchZone.High) {
                    // Invert dx: high player views score in mirrored canvas space
                    highOffset = (highOffset - dx).coerceIn(minOffset, maxOffset)
                    Settings.scoreOffsetHigh = highOffset
                    Storage.saveScoreOffsetHigh(highOffset.toInt())
                } else {
                    lowOffset = (lowOffset + dx).coerceIn(minOffset, maxOffset)
                    Settings.scoreOffsetLow = lowOffset
                    Storage.saveScoreOffsetLow(lowOffset.toInt())
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (activeTouchZone == TouchZone.Button && saveButtonRect.contains(event.x, event.y)) {
                    (context as AppCompatActivity).finish()
                }
                activePointerId = -1
                activeTouchZone = TouchZone.None
            }
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = -1
                activeTouchZone = TouchZone.None
            }
        }
        return true
    }

    private fun clampToScreen(x: Float, text: String): Float {
        val textWidth = scorePaint.measureText(text)
        return x.coerceIn(0f, Settings.screenWidth - textWidth)
    }
}
