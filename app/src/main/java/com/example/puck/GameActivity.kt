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
        startPlayers()
    }

    private fun startPlayers() {
        handle.removeCallbacksAndMessages(null)
        runnable = Runnable {
            Logic.updateExitHold()
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
            invalidate()
            handle.postDelayed(runnable, Settings.refreshRate.toLong())
        }
        handle.post(runnable)
    }

    private fun playGame() {
        Logic.adjustPlayerPositions()
        Logic.checkCharge()
        Logic.checkShield()
        Logic.calculateCollision()
        Logic.checkScored()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (Logic.leaving) {
            return
        }

        Drawing.drawArenaBackground(canvas)
        Drawing.drawChargeFill(canvas)

        if (Settings.gameState != GameState.BallSelection) {
            Effects.drawEffects(canvas)
            Drawing.drawPlayers(canvas)
            Drawing.drawAimArrows(canvas)
            Drawing.drawScoreFlash(canvas)
            Drawing.drawScores(canvas, Logic.highPlayer, Logic.lowPlayer)
            Drawing.drawCanScoreWalls(canvas)
            Drawing.drawWalls(canvas)
            Drawing.drawArenaForeground(canvas)
            if (Settings.pauseGame) {
                canvas.drawText("Paused", Settings.middleX, Settings.middleY, PaintBucket.debugTextPaint)
                Logic.pauseMenu.drawTo(canvas)
            }
        } else {
            if (Settings.pauseGame) {
                canvas.drawText("Paused", Settings.middleX, Settings.middleY, PaintBucket.debugTextPaint)
                Logic.pauseMenu.drawTo(canvas)
            } else {
                Drawing.drawCanScoreWalls(canvas)
                Drawing.drawRules(canvas)
                Logic.highBallCard.drawTo(canvas)
                Logic.lowBallCard.drawTo(canvas)
                Logic.highBallPopup.drawTo(canvas)
                Logic.lowBallPopup.drawTo(canvas)
                Drawing.drawAimArrows(canvas)
                Drawing.drawArenaForeground(canvas)
            }
        }

        Drawing.drawExitHoldFill(canvas)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playView = PlayView(this, this)
        playView.contentDescription = getString(R.string.gameViewDescription)
        setContentView(playView)
        hideSystemUI()
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
    }

    override fun onBackPressed() {
        if (Settings.pauseGame) {
            super.onBackPressed()
        } else {
            //donothing
        }
    }
}
