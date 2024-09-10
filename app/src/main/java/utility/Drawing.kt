package utility

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import enums.FingerState
import enums.GameState
import gameobjects.Player
import gameobjects.Settings
import physics.Ticker

object Drawing {

    lateinit var highScoreZone: RectF
    lateinit var lowScoreZone: RectF

    val countDownProgressTicker = Ticker(99, true)

    var wallWidthParticleCount = 0
    var wallHeightParticleCount = 0

    fun initialize() {
        highScoreZone = RectF(0f, 0f, Settings.screenWidth, Settings.topGoalBottom)
        lowScoreZone = RectF(0f, Settings.bottomGoalTop, Settings.screenWidth, Settings.screenHeight)
        wallHeightParticleCount = (Settings.screenHeight.toInt() - Settings.topGoalBottom.toInt() * 2) / Settings.longParticleSide.toInt()
        wallWidthParticleCount = Settings.screenWidth.toInt() / Settings.longParticleSide.toInt()
    }

    fun drawArena(canvas: Canvas) {
        canvas.drawRect(0f, 0f, Settings.screenWidth, Settings.screenHeight, PaintBucket.backgroundPaint)
        canvas.drawRect(highScoreZone, PaintBucket.goalPaint)
        canvas.drawRect(lowScoreZone, PaintBucket.goalPaint)
    }

    fun drawCountDownRectangles(canvas: Canvas, top: FingerState, bottom:FingerState) {
        when (top) {
            FingerState.RightThumb, FingerState.RightPointer -> {
                canvas.drawRect(0f, 0f, Settings.screenWidth * countDownProgressTicker.ratio, Settings.topGoalBottom, PaintBucket.highBallFillPaint)
            }
            FingerState.LeftThumb, FingerState.LeftPointer -> {
                canvas.drawRect(Settings.screenWidth - (Settings.screenWidth * countDownProgressTicker.ratio), 0f, Settings.screenWidth, Settings.topGoalBottom, PaintBucket.highBallFillPaint)
            }
            else -> {}
        }
        when (bottom) {
            FingerState.RightThumb, FingerState.RightPointer -> {
                canvas.drawRect(Settings.screenWidth - (Settings.screenWidth * countDownProgressTicker.ratio), Settings.bottomGoalTop, Settings.screenWidth, Settings.screenHeight, PaintBucket.lowBallFillPaint)
            }
            FingerState.LeftThumb, FingerState.LeftPointer -> { // correct
                canvas.drawRect(0f, Settings.bottomGoalTop, Settings.screenWidth * countDownProgressTicker.ratio, Settings.screenHeight, PaintBucket.lowBallFillPaint)
            }
            else -> {}
        }
    }

    private fun drawScore(canvas: Canvas, fingerState: FingerState, player: Player) {
        when (fingerState) {
            FingerState.RightThumb, FingerState.RightPointer -> {
                canvas.drawText("${player.score}",30f,Settings.screenHeight - 30f, PaintBucket.alwaysBlackTextPaint) //bottom score
            }
            FingerState.LeftThumb, FingerState.LeftPointer -> {
                canvas.drawText("${player.score}",Settings.screenWidth - 90f,  Settings.screenHeight - 30f, PaintBucket.alwaysBlackTextPaint) //top score
            }
            else -> {}
        }
    }

    private fun checkWinner(canvas: Canvas, winner: Player, other: Player) {
        if (Settings.gameState == GameState.GameOver) {
            if (winner.score >= 5 && other.score != 5) {
                canvas.drawText("You Win!", Settings.screenWidth / 2f, Settings.screenHeight / 3f, PaintBucket.textPaint)
            }
            if (winner.score == 5 && other.score == 5) {
                canvas.drawText("It's a Tie!", Settings.screenWidth / 2f, Settings.screenHeight / 3f, PaintBucket.textPaint)
            }
        }
    }

    fun drawScores(canvas: Canvas, highFingerState: FingerState, highPlayer: Player, lowFingerState: FingerState, lowPlayer: Player) {
        canvas.save()
        canvas.scale(-1f, -1f, Settings.screenWidth / 2f, Settings.screenHeight / 2f)
        drawScore(canvas, highFingerState, highPlayer)
        checkWinner(canvas, highPlayer, lowPlayer)
        canvas.restore()

        drawScore(canvas, lowFingerState, lowPlayer)
        checkWinner(canvas, lowPlayer, highPlayer)
    }

    fun drawPlayers(canvas: Canvas) {
        Logic.highPlayer.drawTo(canvas)
        Logic.lowPlayer.drawTo(canvas)
    }

    fun drawWalls(canvas: Canvas) {
        val minDistance = Settings.screenRatio * 6f
        fun getAlpha(location: Float) = (1 - (location / minDistance)) * 200
        var highPlayer = Logic.highPlayer
        var lowPlayer = Logic.lowPlayer
        var effectPaint = PaintBucket.wallPaint
        for (x in 0 until wallWidthParticleCount) {
            val position = x * Settings.longParticleSide
            val highDistanceToHigh = highPlayer.puck.distanceTo(position, Settings.topGoalBottom - Settings.shortParticleSide) - Settings.screenRatio
            val lowDistanceToHigh = lowPlayer.puck.distanceTo(position, Settings.topGoalBottom - Settings.longParticleSide) - Settings.screenRatio

            var wallColor = 0
            var particleAlpha = 0f
            if (highDistanceToHigh < minDistance) {
                particleAlpha = getAlpha(highDistanceToHigh)
                wallColor = if (lowDistanceToHigh < highDistanceToHigh) {
                    lowPlayer.puck.strokeColor
                }else {
                    highPlayer.puck.strokeColor
                }
            }
            if (lowDistanceToHigh < minDistance) {
                val tempAlpha = getAlpha(lowDistanceToHigh)
                particleAlpha = if (tempAlpha > particleAlpha) tempAlpha else particleAlpha
                wallColor = if (highDistanceToHigh < lowDistanceToHigh) {
                    highPlayer.puck.strokeColor
                } else {
                    lowPlayer.puck.strokeColor
                }
            }

            effectPaint.apply {
                color = wallColor
            }
            effectPaint.alpha = particleAlpha.toInt()

            canvas.drawRect(position, 0f, position + Settings.longParticleSide, Settings.topGoalBottom, effectPaint)

            particleAlpha = 0f

            val highDistanceToLow = highPlayer.puck.distanceTo(position,Settings.bottomGoalTop + Settings.longParticleSide) - Settings.screenRatio
            val lowDistanceToLow = lowPlayer.puck.distanceTo(position,Settings.bottomGoalTop + Settings.longParticleSide) - Settings.screenRatio
            if (highDistanceToLow < minDistance) {
                particleAlpha = getAlpha(highDistanceToLow)
                wallColor = if (lowDistanceToLow < highDistanceToLow) {
                    lowPlayer.puck.strokeColor
                }else {
                    highPlayer.puck.strokeColor
                }
            }
            if (lowDistanceToLow < minDistance) {
                val tempAlpha = getAlpha(lowDistanceToLow)
                particleAlpha = if (tempAlpha > particleAlpha) tempAlpha else particleAlpha
                wallColor = if (highDistanceToLow < lowDistanceToLow) {
                    highPlayer.puck.strokeColor
                } else {
                    lowPlayer.puck.strokeColor
                }

            }
            effectPaint.apply {
                color = wallColor
            }
            effectPaint.alpha = particleAlpha.toInt()
//                canvas.drawRect(position, Settings.bottomGoalTop + Settings.shortParticleSide, position + Settings.longParticleSide, Settings.bottomGoalTop, particleColor)
            canvas.drawRect(position, Settings.bottomGoalTop, position + Settings.longParticleSide, Settings.screenHeight, effectPaint)
        }
        for (y in 0 until (wallHeightParticleCount + 1)) {
            val position = (y * Settings.longParticleSide + Settings.topGoalBottom)
            if (position < Settings.topGoalBottom || position > Settings.bottomGoalTop) continue
            val highDistanceToLeft = highPlayer.puck.distanceTo(Settings.shortParticleSide, position) - Settings.screenRatio
            val lowDistanceToLeft = lowPlayer.puck.distanceTo(Settings.shortParticleSide, position) - Settings.screenRatio
            var wallColor = 0
            var particleAlpha = 0f
            if (highDistanceToLeft < minDistance) {
                particleAlpha = getAlpha(highDistanceToLeft)
                wallColor = if (lowDistanceToLeft < highDistanceToLeft) {
                    lowPlayer.puck.strokeColor
                }else {
                    highPlayer.puck.strokeColor
                }
            }
            if (lowDistanceToLeft < minDistance) {
                val tempAlpha = getAlpha(lowDistanceToLeft)
                particleAlpha = if (tempAlpha > particleAlpha) tempAlpha else particleAlpha
                wallColor = if (highDistanceToLeft < lowDistanceToLeft) {
                    highPlayer.puck.strokeColor
                } else {
                    lowPlayer.puck.strokeColor
                }
            }

            effectPaint.apply {
                color = wallColor
            }
            effectPaint.alpha = particleAlpha.toInt()

            if( y < wallHeightParticleCount) {
                canvas.drawRect(0f, position, Settings.shortParticleSide, position + Settings.longParticleSide, effectPaint)
            }else {
                canvas.drawRect(0f, position, Settings.shortParticleSide, Settings.bottomGoalTop, effectPaint)
            }
            particleAlpha = 0f

            val highDistanceToRight = highPlayer.puck.distanceTo(Settings.screenWidth - Settings.shortParticleSide, position) - Settings.screenRatio
            val lowDistanceToRight = lowPlayer.puck.distanceTo(Settings.screenWidth - Settings.shortParticleSide, position) - Settings.screenRatio

            if (highDistanceToRight < minDistance) {
                particleAlpha = getAlpha(highDistanceToRight)
                wallColor = if (lowDistanceToRight < highDistanceToRight) {
                    lowPlayer.puck.strokeColor
                }else {
                    highPlayer.puck.strokeColor
                }
            }
            if (lowDistanceToRight < minDistance) {
                val tempAlpha = getAlpha(lowDistanceToRight)
                particleAlpha = if (tempAlpha > particleAlpha) tempAlpha else particleAlpha
                wallColor = if (highDistanceToRight < lowDistanceToRight) {
                    highPlayer.puck.strokeColor
                } else {
                    lowPlayer.puck.strokeColor
                }
            }

            effectPaint.apply {
                color = wallColor
            }
            effectPaint.alpha = particleAlpha.toInt()

            if (y < wallHeightParticleCount) {
                canvas.drawRect(Settings.screenWidth - Settings.shortParticleSide, position, Settings.screenWidth, position + Settings.longParticleSide, effectPaint)
            } else {
                canvas.drawRect(Settings.screenWidth - Settings.shortParticleSide, position, Settings.screenWidth, Settings.bottomGoalTop, effectPaint)
            }
        }
    }

    fun mirrorText(canvas: Canvas, text: String, x: Float, y: Float, textPaint: Paint) {
        canvas.save()
        canvas.scale(-1f, -1f, Settings.screenWidth / 2, Settings.screenHeight / 2)
        canvas.drawText(text, x, y, textPaint)
        canvas.restore()
        canvas.drawText(text, x, y, textPaint) //bottom score
    }

    fun showDebugInfo(canvas: Canvas) {
//        canvas.drawText("high Y: ${highPlayer.puck.y}", 300f, 200f, debugText)
//        canvas.drawText("low Y: ${lowPlayer.puck.y}", 300f, 250f, debugText)
//        canvas.drawText("scoreRange: ${Settings.topGoalBottom + highPlayer.pRadius}", 300f, 300f, debugText)
//        canvas.drawText("low T: ${Settings.bottomGoalTop - highPlayer.pRadius}", 300f, 350f, debugText)
//        canvas.drawText("high can score: ${highPlayer.canScore}", 300f, 400f, debugText)
//        canvas.drawText("low can score: ${lowPlayer.canScore}", 300f, 450f, debugText)
//        canvas.drawText("both: ${bothMovingCollisions}", 300f, 500f, debugText)
//        canvas.drawText("bottomRight: ${bottomRight}", screenWidth - 300, 550f, debugText)
//        canvas.drawText("bottomLeft: ${bottomLeft}", screenWidth - 300, 600f, debugText)
//        canvas.drawText("p: ${fingerSelectionProgress}", screenWidth - 300, 650f, debugText)
//        canvas.drawText("down1: ${down1}", screenWidth - 300, 700f, debugText)
//        canvas.drawText("up2: ${up2}", screenWidth - 300, 750f, debugText)
//        canvas.drawText("down2: ${down2}", screenWidth - 300, 800f, debugText)
//        canvas.drawText("up3: ${up3}", screenWidth - 300, 850f, debugText)
//        canvas.drawText("down3: ${down3}", screenWidth - 300, 900f, debugText)
//        canvas.drawText(if (highTouchedFirst) "HIGH" else "LOW", 300f, 950f, debugText)
    }



}