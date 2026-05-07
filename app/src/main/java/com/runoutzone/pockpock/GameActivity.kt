package com.runoutzone.pockpock

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableIntStateOf
import enums.*
import gameobjects.*
import utility.*

open class PlayView(context: Context, override var activity: AppCompatActivity) : GameView(context, activity) {
    private var tipOverlayVisible = false
    private var lastHighTipIndex = -1
    private var lastLowTipIndex = -1

    private val gameLoop = GameLoop(
        intervalMs = { Settings.refreshRate.toLong() },
        onTick = { tick() }
    )

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        doOnSizeChange(width, height, oldWidth, oldHeight)
    }

    override fun doOnSizeChange(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        Logic.initializeSettings(width, height)
        PaintBucket.initialize(resources)
        Logic.initialize(activity, this)
        Sounds.initializeGame()
        Drawing.initialize()
        (activity as? GameActivity)?.positionTipOverlays(
            width, height, Settings.topGoalBottom, Settings.bottomGoalTop
        )
        startPlayers()
    }

    private fun startPlayers() {
        gameLoop.stop()
        gameLoop.start()
    }

    private fun tick() {
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
    }

    private fun playGame() {
        Logic.adjustPlayerPositions()
        Logic.checkCharge()
        Logic.calculateCollision()
        Logic.checkScored()
        Logic.checkDanger()
    }

    // onDraw is unused — GameActivity renders via Compose Canvas (drawGameFrame).
    // Retained for compilation; PlayView is not shown when GameActivity uses setContent.
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    fun pauseGameLoop() {
        gameLoop.stop()
    }

    fun resumeGameLoop() {
        gameLoop.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gameLoop.stop()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        Logic.onTouchEvent(event, context)
        return true
    }
}

class GameActivity : AppCompatActivity() {
    private val tickState = mutableIntStateOf(0)
    private val gameLoop = GameLoop(
        intervalMs = { Settings.refreshRate.toLong() },
        onTick = {
            if (Settings.screenWidth > 0f) {
                Logic.botBrain?.tick()
                Logic.updateCanScoreWall()
                when (Settings.gameState) {
                    GameState.BallSelection -> {
                        Logic.checkCharge()
                        Logic.cancelChargesOnRelease()
                        Logic.checkBallSelectionEnd()
                    }
                    GameState.Play -> {
                        Logic.adjustPlayerPositions()
                        Logic.checkCharge()
                        Logic.calculateCollision()
                        Logic.checkScored()
                        Logic.checkDanger()
                    }
                    GameState.Scored -> Logic.scored()
                    GameState.GameOver -> Logic.gameOver()
                    else -> {}
                }
            }
            tickState.intValue++
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GameScreen(
                gameLoopTick = tickState,
                onSizeKnown = { w, h -> initGame(w, h) }
            )
        }
        hideSystemUI()
    }

    private fun initGame(width: Float, height: Float) {
        Logic.initializeSettings(width.toInt(), height.toInt())
        PaintBucket.initialize(resources)
        Logic.initialize(this)
        Sounds.initializeGame()
        Drawing.initialize()
        Logic.composeReinitCallback = { initGame(width, height) }
        gameLoop.start()
    }

    // Stubs retained so PlayView (kept as fallback) continues to compile
    fun positionTipOverlays(width: Int, height: Int, topGoalBottom: Float, bottomGoalTop: Float) {}
    fun updateTipOverlay(highIndex: Int, lowIndex: Int, visible: Boolean) {}

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    override fun onPause() {
        super.onPause()
        if (Logic.leaving) Sounds.autoPauseSfx() else Sounds.pauseAll()
        gameLoop.stop()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        Sounds.resumeAll()
        gameLoop.start()
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
