package com.example.puck

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import enums.*
import gameobjects.*
import utility.*

open class PlayView(context: Context, override var activity: AppCompatActivity) : GameView(context, activity) {
    var handle: Handler = Handler()
    var runnable: Runnable = Runnable {}
    private var gameLoopPaused = false
    private var tipOverlayVisible = false
    private var lastHighTipIndex = -1
    private var lastLowTipIndex = -1

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        doOnSizeChange(width, height, oldWidth, oldHeight)
    }

    override fun doOnSizeChange(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        Logic.initializeSettings(width, height)
        PaintBucket.initialize(resources)
        Logic.initialize(activity, this)
        Sounds.initializeGame()
        Drawing.initialize(resources)
        (activity as? GameActivity)?.positionTipOverlays(
            width, height, Settings.topGoalBottom, Settings.bottomGoalTop
        )
        startPlayers()
    }

    private fun startPlayers() {
        handle.removeCallbacksAndMessages(null)
        runnable = Runnable {
            Logic.botBrain?.tick()
            Logic.updateCanScoreWall()
            when (Settings.gameState) {
                GameState.BallSelection -> {
                    Logic.checkCharge()
                    Logic.cancelChargesOnRelease()
                    Logic.checkBallSelectionEnd()
                }
                GameState.CountDown -> { /* dead state */ }
                GameState.Tutorial -> { }
                GameState.Play -> playGame()
                GameState.Scored -> {
                    Logic.scored()
                }
                GameState.GameOver -> {
                    Logic.gameOver()
                }
                GameState.Temp -> { }
            }
            val inSelection = Settings.gameState == GameState.BallSelection
            val needsTipUpdate = inSelection != tipOverlayVisible
                    || (inSelection && (Drawing.highTipIndex != lastHighTipIndex || Drawing.lowTipIndex != lastLowTipIndex))
            if (needsTipUpdate) {
                tipOverlayVisible = inSelection
                lastHighTipIndex = Drawing.highTipIndex
                lastLowTipIndex = Drawing.lowTipIndex
                (activity as? GameActivity)?.updateTipOverlay(Drawing.highTipIndex, Drawing.lowTipIndex, inSelection)
            }
            invalidate()
            handle.postDelayed(runnable, Settings.refreshRate.toLong())
        }
        handle.post(runnable)
    }

    private fun playGame() {
        Logic.adjustPlayerPositions()
        Logic.checkCharge()
        Logic.calculateCollision()
        Logic.checkScored()
        Logic.checkDanger()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (Logic.leaving) {
            return
        }

        Drawing.drawArenaBackground(canvas)
        Drawing.drawChargeFill(canvas)
        Effects.drawEffects(canvas)
        Drawing.drawPlayers(canvas)
        Drawing.drawWalls(canvas)
        Drawing.drawAimArrows(canvas)
        Drawing.drawArenaForeground(canvas)

        if (Settings.gameState != GameState.BallSelection) {
            Drawing.drawScoreFlash(canvas)
            Drawing.drawScores(canvas, Logic.highPlayer, Logic.lowPlayer)
        } else {
            Logic.highBallPopup.drawTo(canvas)
            Logic.lowBallPopup.drawTo(canvas)
        }

    }

    fun pauseGameLoop() {
        handle.removeCallbacksAndMessages(null)
        gameLoopPaused = true
    }

    fun resumeGameLoop() {
        if (gameLoopPaused) {
            gameLoopPaused = false
            handle.post(runnable)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handle.removeCallbacksAndMessages(null)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        Logic.onTouchEvent(event, context)
        return true
    }
}

class GameActivity : AppCompatActivity() {
    lateinit var playView: PlayView
    private lateinit var lowTipOverlay: android.view.View
    private lateinit var highTipOverlay: android.view.View

    private val tipStringIds = intArrayOf(
        R.string.tip_controls,
        R.string.tip_scoring,
        R.string.tip_charging,
        R.string.tip_shields,
        R.string.tip_overcharge,
        R.string.tip_grey
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = android.widget.FrameLayout(this)

        playView = PlayView(this, this)
        playView.contentDescription = getString(R.string.gameViewDescription)
        root.addView(playView, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))

        lowTipOverlay = layoutInflater.inflate(R.layout.tip_overlay, root, false)
        highTipOverlay = layoutInflater.inflate(R.layout.tip_overlay, root, false)
        highTipOverlay.rotation = 180f
        lowTipOverlay.visibility = View.GONE
        highTipOverlay.visibility = View.GONE

        root.addView(lowTipOverlay, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(highTipOverlay, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        setContentView(root)
        hideSystemUI()
    }

    fun positionTipOverlays(width: Int, height: Int, topGoalBottom: Float, bottomGoalTop: Float) {
        val padding = (height * 0.02f).toInt()
        val carouselHeight = (gameobjects.Settings.screenRatio * 5f).toInt()

        (lowTipOverlay.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = (height - bottomGoalTop).toInt() + carouselHeight + padding
        }
        lowTipOverlay.requestLayout()

        (highTipOverlay.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            topMargin = topGoalBottom.toInt() + carouselHeight + padding
        }
        highTipOverlay.requestLayout()
    }

    fun updateTipOverlay(highIndex: Int, lowIndex: Int, visible: Boolean) {
        val vis = if (visible) View.VISIBLE else View.GONE
        lowTipOverlay.visibility = vis
        highTipOverlay.visibility = vis
        if (visible) {
            lowTipOverlay.findViewById<android.widget.TextView>(R.id.tipText).setText(tipStringIds[lowIndex])
            highTipOverlay.findViewById<android.widget.TextView>(R.id.tipText).setText(tipStringIds[highIndex])
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    override fun onPause() {
        super.onPause()
        if (Logic.leaving) {
            Sounds.soundPool.autoPause()
        } else {
            Sounds.pauseAll()
        }
        playView.pauseGameLoop()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        Sounds.resumeAll()
        playView.resumeGameLoop()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Logic.unregisterPhaseCallbacks()
        Settings.isSinglePlayer = false
    }


}
