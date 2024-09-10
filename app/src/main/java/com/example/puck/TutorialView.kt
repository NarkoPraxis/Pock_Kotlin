package com.example.puck

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import enums.FingerState
import enums.GameState
import enums.TutorialState
import gameobjects.Settings
import physics.Ticker
import utility.*

class TutorialView(context: Context, override var activity: AppCompatActivity) : GameView(context, activity) {
    var handle: Handler = Handler()
    var runnable: Runnable = Runnable {}

    var showedBasicExplaination = false
    var showedChargeExplaination = false
    var showedChargeCancelExplaination = false
    var showedGameFinishedExplaination = false
    var showedGamePausedExplaination = false

    var collisions = 0

    val startTicker = Ticker(100)


    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        doOnSizeChange(width, height, oldWidth, oldHeight)
    }

    override fun doOnSizeChange(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        Logic.initializeSettings(width, height)
        Logic.initialize(activity, this)
        Sounds.initializeGame()
        PaintBucket.initialize(resources)
        Drawing.initialize()
        Tutorial.initialize()
        Tutorial.assignBox(TutorialState.Intro)
        startPlayers()
    }

    private fun startPlayers() {
        handle.removeCallbacksAndMessages(null)

        runnable = Runnable {
            when (Settings.gameState) {
                GameState.FingerSelection -> {

                }
                GameState.CountDown -> {
                    Logic.countDown()
                }
                GameState.Tutorial -> {

                }
                GameState.Play -> {
                    playGame()
                    if (!showedBasicExplaination) {
                        showedBasicExplaination = true
                        Tutorial.assignBox(TutorialState.BasicGameplayExplain)
                    }
                }

                GameState.Scored -> {
                    Logic.scored()
                }
                GameState.GameOver -> {
                    playGame()
                    if (!showedGameFinishedExplaination) {
                        showedGameFinishedExplaination = true
                        if (Logic.highPlayer.score >= Logic.lowPlayer.score) {
                            Tutorial.assignBox(TutorialState.TopWinsExplain)
                        } else {
                            Tutorial.assignBox(TutorialState.BottomWinsExplain)
                        }
                        Logic.highPlayer.disableEffects = false
                        Logic.lowPlayer.disableEffects = false
                    }
                }
            }
            Tutorial.checkProgress()
            invalidate()
            handle.postDelayed(runnable, 16)
        }
        handle.post(runnable)
    }

    fun playGame() {
        Logic.adjustPlayerPositions()

        Logic.checkCharge()
        Logic.checkShield()
        Logic.calculateCollision()
        if (Logic.calculateCollision()) {
            collisions++
            if (!showedChargeExplaination && collisions > 5) {
                showedChargeExplaination = true
                Tutorial.assignBox(TutorialState.ChargeExplain)
            }
        }
        Logic.checkScored()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (Logic.leaving) {
            return
        }

        Drawing.drawArena(canvas)

        if (Settings.gameState == GameState.CountDown) {
            Drawing.mirrorText(canvas, Logic.countDownText(), Settings.middleX,Settings.middleY / 2, PaintBucket.textPaint )
            Drawing.drawCountDownRectangles(canvas, Logic.highFingerState, Logic.lowFingerState)
        }
        if (Settings.gameState != GameState.FingerSelection) {
            Effects.drawEffects(canvas)
            Drawing.drawScores(canvas, Logic.highFingerState, Logic.highPlayer, Logic.lowFingerState,Logic.lowPlayer)
            Drawing.drawPlayers(canvas)
            Drawing.drawWalls(canvas)
            if (Settings.pauseGame) {
                canvas.drawText("Paused", Settings.middleX, Settings.middleY, PaintBucket.debugTextPaint)
                if (!Settings.tutorialPaused) {
                    Logic.pauseMenu.drawTo(canvas)
                }
                else if (Settings.playerPaused && !showedGamePausedExplaination) {
                    Settings.playerPaused = false
                    showedGamePausedExplaination = true
                    Tutorial.assignBox(TutorialState.PauseExplain)
                    Logic.pauseMenu.drawTo(canvas)
                }
            }
        } else if (Settings.gameState == GameState.FingerSelection) {
            val textX = Settings.screenRatio * 2
            val textY = (6 * Settings.middleY) / 5f
            Logic.bottomRightFinger.drawTo(canvas)
            Logic.bottomLeftFinger.drawTo(canvas)
            Logic.topRightFinger.drawTo(canvas)
            Logic.topLeftFinger.drawTo(canvas)

            if (Logic.lowFingerState != FingerState.Unselected && Logic.highFingerState != FingerState.Unselected) {
                Drawing.mirrorText(canvas,Logic.countDownText(),textX, textY, PaintBucket.textPaint)
                Logic.countDown()
                Drawing.drawCountDownRectangles(canvas, Logic.highFingerState, Logic.lowFingerState);
            }
            else {
                Drawing.mirrorText(canvas,"Pick Your Finger",textX, textY, PaintBucket.textPaint)
            }

            // Logic.showDebugInfo(canvas)
        }

        if (Settings.tutorialPaused) {
            Tutorial.drawTo(canvas)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (Settings.gameState == GameState.FingerSelection) {
            Drawing.countDownProgressTicker.reset()
            Logic.countDownTicker.reset()
            Logic.cdIndex = 0
        }

        if (Settings.tutorialPaused) {
            Tutorial.onTouchEvent(event)
        }
        else {
            Logic.onTouchEvent(event, context)
        }
        return true
    }
}