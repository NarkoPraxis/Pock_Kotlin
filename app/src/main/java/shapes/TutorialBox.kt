package shapes

import android.graphics.Canvas
import com.example.puck.R
import gameobjects.Settings
import physics.TutorialTicker
import utility.Logic
import utility.PaintBucket


class TutorialBox(bottomMessageId: Int, private val nextBox: TutorialBox? = null, var topMessageId: Int = -1) {

    var topMessage = ""
    var bottomMessage = Logic.activity.getString(bottomMessageId)
    var leftBoarder = 0f
    var rightBoarder = 0f
    var size = 0
    var width = 0f
    var bottomTextList = MutableList(0) { String() }
    var topTextList = MutableList(0) { String() }
    var bottomTextHeight = 0
    var topTextHeight = 0

    var startingY = Settings.bottomGoalTop

    var animationSpeed = 20

    var closing = false
    var closed = false

    val appearTicker = TutorialTicker(animationSpeed, true)
    val riseTicker = TutorialTicker(animationSpeed, true)
    val discendTicker = TutorialTicker(animationSpeed)
    val dissappearTicker = TutorialTicker(animationSpeed)



    init {
        leftBoarder = Settings.screenRatio
        rightBoarder = Settings.screenWidth - leftBoarder *3
        size = PaintBucket.resources.getDimensionPixelSize(R.dimen.tutorial_text_size)
        width = (rightBoarder - leftBoarder)

        bottomTextHeight = ExtractLines(bottomMessage, bottomTextList)
        if (topMessageId != -1) {
            topMessage = Logic.activity.getString(topMessageId)
            topTextHeight = ExtractLines(topMessage, topTextList)
        }
        PaintBucket.tutorialTextPaint.apply{
            textSize = size.toFloat()
        }
    }

    private fun ExtractLines(message: String, textList: MutableList<String>) : Int {
        val list = message.split(' ');
        var remainingCount = width
        var newLine = ""
        for (word in list) {
            val length = PaintBucket.tutorialTextPaint.measureText(word)
            if (length > remainingCount) { //reset for the next line
                remainingCount = width - length
                textList.add(newLine)
                newLine = word
            } else if (length <= remainingCount) { //add to current line
                newLine +=
                    if (newLine == "") {
                        remainingCount -= length
                        word
                    } else {
                        remainingCount -= PaintBucket.tutorialTextPaint.measureText(" $word")
                        " $word"
                    }
            }
        }
        textList.add(newLine)

        return textList.count()
    }

    fun drawTo(canvas: Canvas) {
        canvas.save()
        canvas.scale(-1f, -1f, Settings.screenWidth / 2f, Settings.screenHeight / 2f)
        if (topMessageId != -1) {
            draw(canvas, topTextList, topTextHeight)
        } else {
            draw(canvas, bottomTextList, bottomTextHeight)
        }
        canvas.restore()
        draw(canvas, bottomTextList, bottomTextHeight)
    }

    fun reset() {
        closed = false
        closing = false
        appearTicker.reset()
        riseTicker.reset()
        discendTicker.reset()
        dissappearTicker.reset()
    }

    fun getNext() : TutorialBox {
        return nextBox ?: this
    }

    private fun draw(canvas: Canvas, textList: MutableList<String>, textHeight: Int) {
        try{
            val textY = startingY - (size * textHeight) + size / 3
            if (!appearTicker.tick) {
                canvas.drawLine(
                    Settings.middleX - Settings.middleX * appearTicker.ratio,
                    startingY,
                    Settings.middleX + Settings.middleX * appearTicker.ratio,
                    startingY,
                    PaintBucket.lowBallStrokePaint
                )
            } else if (!riseTicker.tick) {
                canvas.drawRect(0f, startingY - (size * riseTicker.ratio) - (size * textHeight * riseTicker.ratio), Settings.screenWidth, startingY, PaintBucket.backgroundPaint)
                canvas.drawLine(0f, startingY, Settings.screenWidth, startingY, PaintBucket.lowBallStrokePaint)
                canvas.drawLine(0f, startingY - (size * riseTicker.ratio) - (size * textHeight * riseTicker.ratio), Settings.screenWidth,startingY - (size * riseTicker.ratio) - (size * textHeight * riseTicker.ratio), PaintBucket.lowBallStrokePaint)
                canvas.clipRect(0f, startingY - (size * riseTicker.ratio) - (size * textHeight * riseTicker.ratio), Settings.screenWidth, startingY)
                for (x in textHeight-1 downTo 0) {
                    canvas.drawText(textList[x], leftBoarder,textY + (size * x), PaintBucket.tutorialTextPaint)
                }

//                canvas.drawRect(0f, startingY - (size * discendTicker.ratio) - (size * textHeight * riseTicker.ratio), Settings.screenWidth, startingY, PaintBucket.highBallFillPaint)
            }
            else if (!closing) {
                canvas.drawRect(0f, startingY - size - (size * textHeight), Settings.screenWidth, startingY, PaintBucket.backgroundPaint)
                canvas.drawLine(0f, startingY, Settings.screenWidth, startingY, PaintBucket.lowBallStrokePaint)
                canvas.drawLine(0f, startingY - size - (size * textHeight), Settings.screenWidth, startingY - size - (size * textHeight), PaintBucket.lowBallStrokePaint)
                for (x in textHeight-1 downTo 0) {
                    canvas.drawText(textList[x], leftBoarder, textY + (size * x), PaintBucket.tutorialTextPaint)
                }
            }
            else {
                if (!discendTicker.tick) {
                    canvas.drawRect(0f, startingY - (size * discendTicker.ratio) - (size * textHeight * discendTicker.ratio), Settings.screenWidth, startingY, PaintBucket.backgroundPaint)
                    canvas.drawLine(0f, startingY, Settings.screenWidth, startingY, PaintBucket.lowBallStrokePaint)
                    canvas.drawLine(0f, startingY - (size * discendTicker.ratio) - (size * textHeight * discendTicker.ratio), Settings.screenWidth,startingY - (size * discendTicker.ratio) - (size * textHeight * discendTicker.ratio), PaintBucket.lowBallStrokePaint)
                    canvas.clipRect(0f, startingY - (size * discendTicker.ratio) - (size * textHeight * discendTicker.ratio), Settings.screenWidth, startingY)
                    for (x in textHeight-1 downTo 0) {
                        canvas.drawText(textList[x], leftBoarder, textY + (size * x), PaintBucket.tutorialTextPaint)
                    }

                } else if (!dissappearTicker.tick) {
                    canvas.drawLine(
                        Settings.middleX - Settings.middleX * dissappearTicker.ratio,
                        startingY,
                        Settings.middleX + Settings.middleX * dissappearTicker.ratio,
                        startingY,
                        PaintBucket.lowBallStrokePaint
                    )
                }
                else {
                    closed = true;
                }
            }

        }
        catch(e: Exception) {
            canvas.drawLine(0f, 0f, Settings.screenWidth, Settings.screenHeight, PaintBucket.lowBallStrokePaint)
        }
    }
}