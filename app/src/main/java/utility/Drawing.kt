package utility

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import enums.GameState
import gameobjects.Player
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import physics.Ticker
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.core.graphics.withScale

object Drawing {

    lateinit var highScoreZone: RectF
    lateinit var lowScoreZone: RectF

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
        canScoreListener = { Settings.canScore = true }
        cantScoreListener = { Settings.canScore = false }
        GameEvents.canScore.connect(canScoreListener!!)
        GameEvents.cantScore.connect(cantScoreListener!!)
        Effects.clearPersistentEffects()
        Effects.clearCollisionEffects()
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

    fun drawArenaBackground(canvas: Canvas) {
        canvas.drawRect(0f, 0f, Settings.screenWidth, Settings.screenHeight, PaintBucket.backgroundPaint)
        canvas.drawRect(highScoreZone, PaintBucket.goalPaint)
        canvas.drawRect(lowScoreZone, PaintBucket.goalPaint)
        drawTouchHighlights(canvas, Logic.highPlayer, Logic.lowPlayer)
    }

    fun drawArenaForeground(canvas: Canvas) {
        drawGoalMenuHints(canvas)
    }

    private var chargeFillFrame = 0

    fun drawChargeFill(canvas: Canvas) {
        chargeFillFrame++
        if (Settings.highPlayerChargeFill) drawPlayerChargeFill(canvas, Logic.highPlayer, isHigh = true)
        if (Settings.lowPlayerChargeFill) drawPlayerChargeFill(canvas, Logic.lowPlayer, isHigh = false)
    }

    private fun drawPlayerChargeFill(canvas: Canvas, player: Player, isHigh: Boolean) {
        val effect = player.puck.renderer.effect ?: return
        val ph = effect.phase
        if (ph == ChargePhase.Idle || ph == ChargePhase.Inert) return
        val ratio = effect.chargeFillRatio
        if (ratio <= 0f) return
        val theme = effect.theme
        val paint = if (isHigh) PaintBucket.chargeFillHighPaint else PaintBucket.chargeFillLowPaint
        val color = when (ph) {
            ChargePhase.Building, ChargePhase.Draining -> theme.main.primary
            ChargePhase.SweetSpot -> theme.effect.primary
            else -> return
        }
        val alpha = when (ph) {
            ChargePhase.Building, ChargePhase.Draining -> 128
            ChargePhase.SweetSpot -> {
                val pulse = 0.7f + 0.3f * sin(chargeFillFrame * 0.35f)
                (pulse * 255).toInt().coerceIn(0, 255)
            }
            else -> return
        }
        paint.color = color
        paint.alpha = alpha
        if (isHigh) {
            val bottom = Settings.topGoalBottom + ratio * (Settings.middleY - Settings.topGoalBottom)
            canvas.drawRect(0f, Settings.topGoalBottom, Settings.screenWidth, bottom, paint)
        } else {
            val top = Settings.bottomGoalTop - ratio * (Settings.bottomGoalTop - Settings.middleY)
            canvas.drawRect(0f, top, Settings.screenWidth, Settings.bottomGoalTop, paint)
        }
    }

    private fun drawScore(canvas: Canvas, player: Player, popTicker: Ticker, xOffset: Float = 0f) {
        val paint = PaintBucket.alwaysBlackTextPaint
        paint.textSize = Settings.topGoalBottom * 0.85f
        val xMargin = Settings.screenRatio * 3f
        val yMargin = Settings.topGoalBottom * 0.2f
        val scoreText = "${player.score}"
        val scoreX = xMargin + xOffset
        val scoreY = Settings.screenHeight - yMargin
        if (Settings.scorePopEnabled && !popTicker.finished) {
            popTicker.tick
            val scale = 1f + sin(popTicker.ratio * Math.PI.toFloat())
            canvas.save()
            canvas.scale(scale, scale, scoreX, scoreY)
            canvas.drawText(scoreText, scoreX, scoreY, paint)
            canvas.restore()
        } else {
            canvas.drawText(scoreText, scoreX, scoreY, paint)
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
        drawScore(canvas, highPlayer, Settings.highScorePopTicker, Settings.scoreOffsetHigh)
        checkWinner(canvas, highPlayer, lowPlayer)
        canvas.restore()

        drawScore(canvas, lowPlayer, Settings.lowScorePopTicker, Settings.scoreOffsetLow)
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

        val ux = dx / dist
        val uy = dy / dist

        // Clamp visual arrow length to 1/3 screen width; head stays fixed, tail moves inward
        val maxArrowLength = Settings.screenWidth / 3f
        val visualDist = dist.coerceAtMost(maxArrowLength)
        val visualTailX = headX - ux * visualDist
        val visualTailY = headY - uy * visualDist

        var themeAlpha = 255
        var chargeAlpha = 255
        var fillLen = visualDist * ratio
        var fillColor = chargeColor

        if (inSweetSpot) {
            val pulse = 0.7f + 0.3f * sin(aimArrowFrame * 0.35f)
            chargeAlpha = (255 * pulse).toInt().coerceIn(0, 255)
            themeAlpha = chargeAlpha
            fillLen = visualDist
        }
        if (overcharged) {
            val fade = (player.overchargeFrames / 12f).coerceIn(0f, 1f)
            fillColor = lerpColor(chargeColor, themeColor, fade)
        }

        aimArrowLinePaint.strokeWidth = Settings.strokeWidth * 1.3f

        val fillEndX = visualTailX + ux * fillLen
        val fillEndY = visualTailY + uy * fillLen

        if (fillLen < visualDist) {
            aimArrowLinePaint.color = themeColor
            aimArrowLinePaint.alpha = themeAlpha
            canvas.drawLine(fillEndX, fillEndY, headX, headY, aimArrowLinePaint)
        }
        if (fillLen > 0f) {
            aimArrowLinePaint.color = fillColor
            aimArrowLinePaint.alpha = chargeAlpha
            canvas.drawLine(visualTailX, visualTailY, fillEndX, fillEndY, aimArrowLinePaint)
        }

        val headFilled = fillLen >= visualDist
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

    fun mirrorText(canvas: Canvas, topText: String, bottomText: String, x: Float, y: Float, textPaint: Paint) {
        canvas.save()
        canvas.scale(-1f, -1f, Settings.screenWidth / 2, Settings.screenHeight / 2)
        canvas.drawText(topText, x, y, textPaint)
        canvas.restore()
        canvas.drawText(bottomText, x, y, textPaint) //bottom score
    }

    private val tipPages: List<List<String>> = listOf(
        listOf("Scoring:", "   Knock your opponent", "   into either purple zone", "   to score a point."),
        listOf("Charging:", "   Hold to build power.", "   Release when purple", "   Gain a shield!"),
        listOf("Purple Shields:", "   A shielded puck", "   wins every bounce", "   so time your release!"),
        listOf("Overcharge:", "   Charging too long", "   resets your power.", "   Don't hold forever!"),
        listOf("Both Shielded:", "   If both pucks are", "   shielded, both shields", "   cancel out. Plan ahead.")
    )

    var highTipIndex: Int = 0
        private set
    var lowTipIndex: Int = 0
        private set

    fun resetTipIndices() {
        highTipIndex = 0
        lowTipIndex = 0
    }

    fun cycleHighTip() {
        highTipIndex = pickNewTipIndex(highTipIndex)
    }

    fun cycleLowTip() {
        lowTipIndex = pickNewTipIndex(lowTipIndex)
    }

    private fun pickNewTipIndex(current: Int): Int {
        val size = tipPages.size
        if (size <= 1) return current
        val newIndex = current + 1
        if (newIndex >= size) {
            return 0
        }
        return newIndex
        //val choices = (0 until size).filter { it != current }
        //return choices.random()
    }

    fun drawRules(canvas: Canvas) {
        val highPage = tipPages[highTipIndex]
        val lowPage = tipPages[lowTipIndex]
        val textX = Settings.screenRatio * 2f
        val lineHeight = Settings.screenRatio * 1.8f
        val startY = Settings.bottomGoalTop - Settings.screenRatio * 11f
        val lineCount = maxOf(highPage.size, lowPage.size)
        for (i in 0 until lineCount) {
            val y = startY + i * lineHeight
            val paint = if (i == 0) PaintBucket.rulesTitlePaint else PaintBucket.rulesTextPaint
            mirrorText(canvas, highPage.getOrElse(i) { "" }, lowPage.getOrElse(i) { "" }, textX, y, paint)
        }
    }

    fun drawGoalMenuHints(canvas: Canvas) {
    }

    fun showDebugInfo(canvas: Canvas) {
//        canvas.drawText("high Y: ${highPlayer.puck.y}", 300f, 200f, debugText)
//        canvas.drawText("low Y: ${lowPlayer.puck.y}", 300f, 250f, debugText)
//        canvas.drawText("scoreRange: ${Settings.topGoalBottom + highPlayer.pRadius}", 300f, 300f, debugText)
//        canvas.drawText("low T: ${Settings.bottomGoalTop - highPlayer.pRadius}", 300f, 350f, debugText)
//        canvas.drawText("can score: ${Settings.canScore}", 300f, 400f, debugText)
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