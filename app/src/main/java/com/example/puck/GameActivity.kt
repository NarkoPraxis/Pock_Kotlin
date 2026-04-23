package com.example.puck

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.View
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
            Logic.updateCanScoreWall()
            when (Settings.gameState) {
                GameState.BallSelection -> {
                    Logic.checkCharge()
                    Logic.cancelChargesOnRelease()
                    Logic.updateReadyFill()
                }
                GameState.CountDown -> {
                    Logic.checkCharge()
                    Logic.cancelChargesOnRelease()
                    Logic.updateReadyFill()
                }
                GameState.Tutorial -> {

                }
                GameState.Play -> playGame()
                GameState.Scored -> {
                    Logic.scored()
                }
                GameState.GameOver -> {
                    Logic.gameOver()
                }
                GameState.Temp -> {
                }
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

        Drawing.drawArena(canvas)


        if (Settings.gameState == GameState.Play) {
            Drawing.drawGoalMenuHints(canvas)
        }

        if (Settings.gameState == GameState.CountDown) {

            Drawing.drawCanScoreWalls(canvas)
        }
        if (Settings.gameState != GameState.BallSelection) {
            Effects.drawEffects(canvas)
            Drawing.drawPlayers(canvas)
            Drawing.drawAimArrows(canvas)
            Drawing.drawScoreFlash(canvas)
            Drawing.drawScores(canvas, Logic.highPlayer, Logic.lowPlayer)
            if (Settings.gameState != GameState.CountDown) {
                Drawing.drawCanScoreWalls(canvas)
            }
            Drawing.drawWalls(canvas)
            if (Settings.pauseGame) {
                canvas.drawText("Paused", Settings.middleX, Settings.middleY, PaintBucket.debugTextPaint)
                Logic.pauseMenu.drawTo(canvas)
            }
        } else {
            if (Settings.pauseGame) {
                canvas.drawText("Paused", Settings.middleX, Settings.middleY, PaintBucket.debugTextPaint)
                Logic.pauseMenu.drawTo(canvas)
            } else {
                Drawing.drawReadyFill(canvas)
                Drawing.drawCanScoreWalls(canvas)
                Drawing.drawRules(canvas)

                Logic.highBallCard.drawTo(canvas)
                Logic.lowBallCard.drawTo(canvas)
                Logic.highBallPopup.drawTo(canvas)
                Logic.lowBallPopup.drawTo(canvas)
                Drawing.drawAimArrows(canvas)
            }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playView = PlayView(this, this)
        playView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        playView.contentDescription = getString(R.string.gameViewDescription)
        setContentView(playView)
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
        Sounds.resumeAll()
        playView.resumeGameLoop()
    }

    override fun onBackPressed() {
        if (Settings.pauseGame) {
            super.onBackPressed()
        } else {
            //donothing
        }
    }
}
