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
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.google.android.gms.ads.MobileAds
import enums.*
import gameobjects.*
import utility.*

open class PlayView(context: Context, var ad: InterstitialAd, override var activity: AppCompatActivity) : GameView(context, activity) {
    var handle: Handler = Handler()
    var runnable: Runnable = Runnable {}

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
                GameState.Play -> playGame()
                GameState.Scored -> {
                    Logic.scored()
                }
                GameState.GameOver -> {
                    Logic.gameOver(ad)
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

        if (Settings.gameState == GameState.CountDown) {
            Drawing.mirrorText(canvas, Logic.countDownText(), Settings.middleX,Settings.middleY / 2, PaintBucket.textPaint)
            Drawing.drawCountDownRectangles(canvas, Logic.highFingerState, Logic.lowFingerState)
        }
        if (Settings.gameState != GameState.FingerSelection) {
            Effects.drawEffects(canvas)
            Drawing.drawScores(canvas, Logic.highFingerState, Logic.highPlayer, Logic.lowFingerState,Logic.lowPlayer)
            Drawing.drawPlayers(canvas)
            Drawing.drawWalls(canvas)
            if (Settings.pauseGame) {
                canvas.drawText("Paused", Settings.middleX, Settings.middleY, PaintBucket.debugTextPaint)
                Logic.pauseMenu.drawTo(canvas)
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
                Drawing.drawCountDownRectangles(canvas, Logic.highFingerState, Logic.lowFingerState)
            }
            else {
                Drawing.mirrorText(canvas,"Pick Your Finger",textX, textY, PaintBucket.textPaint)
            }

            // Logic.showDebugInfo(canvas)
        }

    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (Settings.gameState == GameState.FingerSelection) {
            Drawing.countDownProgressTicker.reset()
            Logic.countDownTicker.reset()
            Logic.cdIndex = 0
        }
        Logic.onTouchEvent(event, context)
        return true
//        when (motionEvent) {
//            MotionEvent.ACTION_UP -> up++
//            MotionEvent.ACTION_DOWN -> down++
//            MotionEvent.ACTION_MOVE -> move++
//            MotionEvent.ACTION_POINTER_UP -> upP++
//            MotionEvent.ACTION_POINTER_DOWN -> downP++
//            MotionEvent.ACTION_POINTER_1_UP -> up1++
//            MotionEvent.ACTION_POINTER_1_DOWN -> down1++
//            MotionEvent.ACTION_POINTER_2_UP -> up2++
//            MotionEvent.ACTION_POINTER_2_DOWN -> down2++
//            MotionEvent.ACTION_POINTER_3_UP -> up3++
//            MotionEvent.ACTION_POINTER_3_DOWN -> down3++
//            else -> other++
//        }
    }
}

class GameActivity : AppCompatActivity() {
    private lateinit var interstitialAd: InterstitialAd
    lateinit var playView: PlayView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.adShownToday) {
            MobileAds.initialize(this) {}
            interstitialAd = InterstitialAd(this)
            interstitialAd.adUnitId = "ca-app-pub-3940256099942544/1033173712"
            interstitialAd.loadAd(AdRequest.Builder().build())
        }
        else {
            interstitialAd = InterstitialAd(this)
        }
//        var drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
//        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        playView = PlayView(this, interstitialAd, this)
        playView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        playView.contentDescription = getString(R.string.gameViewDescription)
        setContentView(playView)
    }

    override fun onBackPressed() {
        if (Settings.pauseGame) {
            super.onBackPressed()
        } else {
            //donothing
        }
    }
}
