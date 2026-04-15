package utility

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import enums.GameState
import gameobjects.Player
import gameobjects.Settings
import physics.Ticker
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.core.graphics.withScale

object Drawing {

    lateinit var highScoreZone: RectF
    lateinit var lowScoreZone: RectF

    val countDownProgressTicker = Ticker(99, true)

    var wallWidthParticleCount = 0
    var wallHeightParticleCount = 0

    private var canScoreListener: ((Unit) -> Unit)? = null
    private var cantScoreListener: ((Unit) -> Unit)? = null

    fun initialize() {
        highScoreZone = RectF(0f, 0f, Settings.screenWidth, Settings.topGoalBottom)
        lowScoreZone = RectF(0f, Settings.bottomGoalTop, Settings.screenWidth, Settings.screenHeight)
        wallHeightParticleCount = (Settings.screenHeight.toInt() - Settings.topGoalBottom.toInt() * 2) / Settings.longParticleSide.toInt()
        wallWidthParticleCount = Settings.screenWidth.toInt() / Settings.longParticleSide.toInt()

        canScoreListener?.let { GameEvents.canScore.disconnect(it) }
        cantScoreListener?.let { GameEvents.cantScore.disconnect(it) }
        canScoreListener = { Settings.canScoreWallHiding = true }
        cantScoreListener = { Settings.canScoreWallHiding = false }
        GameEvents.canScore.connect(canScoreListener!!)
        GameEvents.cantScore.connect(cantScoreListener!!)
    }

    fun drawCanScoreWalls(canvas: Canvas) {
        val minDistance = Settings.screenRatio * 6f
        fun getAlpha(location: Float) = (1 - (location / minDistance)) * 200
        val highPlayer = Logic.highPlayer
        val lowPlayer = Logic.lowPlayer
        val effectPaint = PaintBucket.canScoreWallPaint
        val baseAlpha = 180

        for (x in 0 until wallWidthParticleCount) {
            val xPos = x * Settings.longParticleSide
            val xEnd = xPos + Settings.longParticleSide

            // Top canScore wall
            val topCY = Settings.canScoreTopWallCenterY
            val highDist = highPlayer.puck.distanceTo(xPos, topCY) - Settings.screenRatio
            val lowDist  = lowPlayer.puck.distanceTo(xPos, topCY)  - Settings.screenRatio
            var wallColor = PaintBucket.canScoreWallColor
            var proximityAlpha = 0f
            if (highDist < minDistance) {
                proximityAlpha = getAlpha(highDist)
                wallColor = if (lowDist < highDist) lowPlayer.puck.strokeColor else highPlayer.puck.strokeColor
            }
            if (lowDist < minDistance) {
                val a = getAlpha(lowDist)
                if (a > proximityAlpha) {
                    proximityAlpha = a
                    wallColor = if (highDist < lowDist) highPlayer.puck.strokeColor else lowPlayer.puck.strokeColor
                }
            }
            effectPaint.color = if (proximityAlpha > baseAlpha) wallColor else PaintBucket.canScoreWallColor
            effectPaint.alpha = maxOf(baseAlpha, proximityAlpha.toInt())
            canvas.drawRect(xPos, Settings.canScoreTopWallTop, xEnd, Settings.canScoreTopWallBottom, effectPaint)

            // Bottom canScore wall
            val botCY = Settings.canScoreBottomWallCenterY
            val highDistB = highPlayer.puck.distanceTo(xPos, botCY) - Settings.screenRatio
            val lowDistB  = lowPlayer.puck.distanceTo(xPos, botCY)  - Settings.screenRatio
            var wallColorB = PaintBucket.canScoreWallColor
            var proximityAlphaB = 0f
            if (highDistB < minDistance) {
                proximityAlphaB = getAlpha(highDistB)
                wallColorB = if (lowDistB < highDistB) lowPlayer.puck.strokeColor else highPlayer.puck.strokeColor
            }
            if (lowDistB < minDistance) {
                val a = getAlpha(lowDistB)
                if (a > proximityAlphaB) {
                    proximityAlphaB = a
                    wallColorB = if (highDistB < lowDistB) highPlayer.puck.strokeColor else lowPlayer.puck.strokeColor
                }
            }
            effectPaint.color = if (proximityAlphaB > baseAlpha) wallColorB else PaintBucket.canScoreWallColor
            effectPaint.alpha = maxOf(baseAlpha, proximityAlphaB.toInt())
            canvas.drawRect(xPos, Settings.canScoreBottomWallTop, xEnd, Settings.canScoreBottomWallBottom, effectPaint)
        }
    }

    fun drawArena(canvas: Canvas) {
        canvas.drawRect(0f, 0f, Settings.screenWidth, Settings.screenHeight, PaintBucket.backgroundPaint)
        canvas.drawRect(highScoreZone, PaintBucket.goalPaint)
        canvas.drawRect(lowScoreZone, PaintBucket.goalPaint)
    }

    fun drawCountDownRectangles(canvas: Canvas) {
        canvas.drawRect(0f, 0f, Settings.screenWidth * countDownProgressTicker.ratio, Settings.topGoalBottom, PaintBucket.highBallFillPaint)
        canvas.drawRect(0f, Settings.bottomGoalTop, Settings.screenWidth * countDownProgressTicker.ratio, Settings.screenHeight, PaintBucket.lowBallFillPaint)
    }

    private fun drawScore(canvas: Canvas, player: Player, popTicker: Ticker) {
        val xMargin = Settings.screenRatio * 3f
        val yMargin = Settings.screenRatio / 2f + 20f
        val scoreText = "${player.score}"
        val scoreX = xMargin
        val scoreY = Settings.screenHeight - yMargin
        if (Settings.scorePopEnabled && !popTicker.finished) {
            popTicker.tick
            val scale = 1f + sin(popTicker.ratio * Math.PI.toFloat())
            canvas.save()
            canvas.scale(scale, scale, scoreX, scoreY)
            canvas.drawText(scoreText, scoreX, scoreY, PaintBucket.alwaysBlackTextPaint)
            canvas.restore()
        } else {
            canvas.drawText(scoreText, scoreX, scoreY, PaintBucket.alwaysBlackTextPaint)
        }
    }

    fun drawScoreFlash(canvas: Canvas) {
        if (!Settings.scoreFlashEnabled || Settings.scoreFlashAlpha <= 0f) return
        PaintBucket.scoreFlashPaint.color = Settings.scoreFlashColor
        PaintBucket.scoreFlashPaint.alpha = Settings.scoreFlashAlpha.toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, Settings.screenWidth, Settings.screenHeight, PaintBucket.scoreFlashPaint)
        Settings.scoreFlashAlpha -= 8f
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

    fun drawScores(canvas: Canvas, highPlayer: Player, lowPlayer: Player) {
        canvas.save()
        canvas.scale(-1f, -1f, Settings.screenWidth / 2f, Settings.screenHeight / 2f)
        drawScore(canvas, highPlayer, Settings.highScorePopTicker)
        checkWinner(canvas, highPlayer, lowPlayer)
        canvas.restore()

        drawScore(canvas, lowPlayer, Settings.lowScorePopTicker)
        checkWinner(canvas, lowPlayer, highPlayer)
    }

    fun drawTouchHighlights(canvas: Canvas, highPlayer: Player, lowPlayer: Player) {
        if (highPlayer.isTouching) {
            canvas.drawRect(0f, 0f, Settings.screenWidth, Settings.middleY,
                PaintBucket.highPlayerHighlightPaint)
        }
        if (lowPlayer.isTouching) {
            canvas.drawRect(0f, Settings.middleY, Settings.screenWidth, Settings.screenHeight,
                PaintBucket.lowPlayerHighlightPaint)
        }
    }

    fun drawPlayers(canvas: Canvas) {
        Logic.highPlayer.drawTo(canvas)
        Logic.lowPlayer.drawTo(canvas)
    }

    private val aimArrowLinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val aimArrowFillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val aimArrowPath = Path()
    private var aimArrowFrame = 0

    fun drawAimArrows(canvas: Canvas) {
        aimArrowFrame++
        if (Settings.lowPlayerArrow) drawAimArrow(canvas, Logic.lowPlayer, isHigh = false)
        if (Settings.highPlayerArrow) drawAimArrow(canvas, Logic.highPlayer, isHigh = true)
    }

    private fun drawAimArrow(canvas: Canvas, player: Player, isHigh: Boolean) {
        if (!player.isFlingHeld) return

        // Tail = current finger position. Head/tip = touch-down position (launch direction).
        val tailX = player.flingCurrent.x
        val tailY = player.flingCurrent.y
        val headX = player.flingStart.x
        val headY = player.flingStart.y
        val dx = headX - tailX
        val dy = headY - tailY
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < Settings.screenRatio * 0.3f) return

        val themeColor = if (isHigh) PaintBucket.highBallStrokeColor else PaintBucket.lowBallStrokeColor
        val chargeColor = PaintBucket.effectColor

        // Fill reaches 100% when sweet spot begins, not when it ends.
        val range = (Settings.sweetSpotMin - Settings.chargeStart).toFloat()
        val ratio = ((player.charge - Settings.chargeStart) / range).coerceIn(0f, 1f)
        val inSweetSpot = player.charge >= Settings.sweetSpotMin && player.charge <= Settings.sweetSpotMax
        val overcharged = player.chargePowerLocked

        var themeAlpha = 255
        var chargeAlpha = 255
        var fillLen = dist * ratio
        var fillColor = chargeColor

        if (inSweetSpot) {
            val pulse = 0.7f + 0.3f * sin(aimArrowFrame * 0.35f)
            chargeAlpha = (255 * pulse).toInt().coerceIn(0, 255)
            themeAlpha = chargeAlpha
            fillLen = dist
        }
        if (overcharged) {
            val fade = (player.overchargeFrames / 12f).coerceIn(0f, 1f)
            fillColor = lerpColor(chargeColor, themeColor, fade)
        }

        val ux = dx / dist
        val uy = dy / dist
        aimArrowLinePaint.strokeWidth = Settings.strokeWidth * 1.3f

        val fillEndX = tailX + ux * fillLen
        val fillEndY = tailY + uy * fillLen

        if (fillLen < dist) {
            aimArrowLinePaint.color = themeColor
            aimArrowLinePaint.alpha = themeAlpha
            canvas.drawLine(fillEndX, fillEndY, headX, headY, aimArrowLinePaint)
        }
        if (fillLen > 0f) {
            aimArrowLinePaint.color = fillColor
            aimArrowLinePaint.alpha = chargeAlpha
            canvas.drawLine(tailX, tailY, fillEndX, fillEndY, aimArrowLinePaint)
        }

        val headFilled = fillLen >= dist
        val headColor = if (headFilled) fillColor else themeColor
        val headAlpha = if (headFilled) chargeAlpha else themeAlpha
        aimArrowFillPaint.color = headColor
        aimArrowFillPaint.alpha = headAlpha
        val headSize = Settings.screenRatio * 0.8f
        val perpX = -uy
        val perpY = ux
        aimArrowPath.reset()
        aimArrowPath.moveTo(headX + ux * headSize, headY + uy * headSize)
        aimArrowPath.lineTo(headX - ux * headSize * 0.2f + perpX * headSize * 0.75f,
                            headY - uy * headSize * 0.2f + perpY * headSize * 0.75f)
        aimArrowPath.lineTo(headX - ux * headSize * 0.2f - perpX * headSize * 0.75f,
                            headY - uy * headSize * 0.2f - perpY * headSize * 0.75f)
        aimArrowPath.close()
        canvas.drawPath(aimArrowPath, aimArrowFillPaint)
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
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

    fun drawGoalMenuHints(canvas: Canvas) {
        val cx = Settings.screenWidth / 2f
        val padding = Settings.screenRatio / 2
        val highGoalCenterOffset = (Settings.topGoalBottom / 2f)
        val lowGoalCenterOffset = ((Settings.screenHeight - Settings.bottomGoalTop) / 2)
        val highHintY = highGoalCenterOffset - padding
        val lowHintY = Settings.bottomGoalTop + lowGoalCenterOffset + padding

//        canvas.drawText("2 FINGER TOUCH", cx, highHintY, PaintBucket.menuHintPaint)
        canvas.withScale(-1f, -1f, cx, highHintY) {
            drawText("2 FINGER TOUCH", cx, highHintY, PaintBucket.menuHintPaint)
        }

        canvas.drawText("2 FINGER TOUCH", cx, lowHintY, PaintBucket.menuHintPaint)
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