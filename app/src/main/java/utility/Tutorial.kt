package utility

import android.graphics.Canvas
import android.view.MotionEvent
import com.example.puck.R
import enums.TutorialState
import gameobjects.Settings
import shapes.TutorialBox

object Tutorial {

    private lateinit var intro : TutorialBox
    private lateinit var intro2 : TutorialBox
    private lateinit var intro3 : TutorialBox
    private lateinit var intro4 : TutorialBox
    private lateinit var intro5 : TutorialBox
    private lateinit var fingerSelection : TutorialBox
    private lateinit var fingerSelection2 : TutorialBox
    private lateinit var aiming : TutorialBox
    private lateinit var aiming2 : TutorialBox
    private lateinit var aiming3 : TutorialBox
    private lateinit var aiming4 : TutorialBox
    private lateinit var chargingBonus : TutorialBox
    private lateinit var chargingBonus2 : TutorialBox
    private lateinit var chargingBonus3 : TutorialBox
    private lateinit var chargingBonus4 : TutorialBox
    private lateinit var chargingBonus5 : TutorialBox
    private lateinit var chargingBonus6 : TutorialBox
    private lateinit var chargingBonusCanceled : TutorialBox
    private lateinit var chargingBonusCanceled2 : TutorialBox
    private lateinit var teleport : TutorialBox
    private lateinit var teleport2 : TutorialBox
    private lateinit var teleport3 : TutorialBox
    private lateinit var topWinsPausing : TutorialBox
    private lateinit var bottomWinsPausing : TutorialBox
    private lateinit var paused : TutorialBox
    private lateinit var paused2 : TutorialBox
    private lateinit var howtoPause2 : TutorialBox
    private lateinit var howtoPause : TutorialBox

    private lateinit var currentBox : TutorialBox

    var shownBoxes = MutableList(0) {TutorialState.TopWinsExplain};


    var state = TutorialState.None
    var showing = false;

    fun initialize() {
        fingerSelection2 = TutorialBox(R.string.tutorial_FingerSelection2)
        fingerSelection = TutorialBox(R.string.tutorial_FingerSelection, fingerSelection2)
        intro5 = TutorialBox(R.string.tutorial_Intro5, fingerSelection);
        intro4 = TutorialBox(R.string.tutorial_Intro4, intro5)
        intro3 = TutorialBox(R.string.tutorial_Intro3, intro4)
        intro2 = TutorialBox(R.string.tutorial_Intro2, intro3)
        intro = TutorialBox(R.string.tutorial_Intro, intro2)
        aiming4 = TutorialBox(R.string.tutorial_Aiming4)
        aiming3 = TutorialBox(R.string.tutorial_Aiming3, aiming4)
        aiming2 = TutorialBox(R.string.tutorial_Aiming2, aiming3)
        aiming = TutorialBox(R.string.tutorial_Aiming, aiming2)
        chargingBonus6 = TutorialBox(R.string.tutorial_Charging_Bonus6)
        chargingBonus5 = TutorialBox(R.string.tutorial_Charging_Bonus5, chargingBonus6)
        chargingBonus4 = TutorialBox(R.string.tutorial_Charging_Bonus4, chargingBonus5)
        chargingBonus3 = TutorialBox(R.string.tutorial_Charging_Bonus3, chargingBonus4)
        chargingBonus2 = TutorialBox(R.string.tutorial_Charging_Bonus2, chargingBonus3)
        chargingBonus = TutorialBox(R.string.tutorial_Charging_Bonus, chargingBonus2)
        chargingBonusCanceled2 = TutorialBox(R.string.tutorial_Charging_Bonus_Canceled2)
        chargingBonusCanceled = TutorialBox(
            R.string.tutorial_Charging_Bonus_Canceled,
            chargingBonusCanceled2
        )
        teleport3 = TutorialBox(R.string.tutorial_Teleport3)
        teleport2 = TutorialBox(R.string.tutorial_Teleport2, teleport3)
        teleport = TutorialBox(R.string.tutorial_Teleport, teleport2)

        paused2 = TutorialBox(R.string.tutorial_Paused2)
        paused = TutorialBox(R.string.tutorial_Paused, paused2)
        howtoPause2 = TutorialBox(R.string.tutorial_HowToPause2)
        howtoPause = TutorialBox(R.string.tutorial_HowToPause, howtoPause2)
        topWinsPausing = TutorialBox(R.string.tutorial_Losing, howtoPause, R.string.tutorial_Winning)
        bottomWinsPausing = TutorialBox(R.string.tutorial_Winning, howtoPause, R.string.tutorial_Losing)
        currentBox = intro
    }

    fun drawTo(canvas: Canvas) {
        if (showing) {
            currentBox.drawTo(canvas)
        }
    }

    fun assignBox(state: TutorialState) {
        if (shownBoxes.contains(state)) {
            return
        }
        shownBoxes.add(state)
        showing = true
        this.state = state
        Settings.pauseGame = true
        Settings.tutorialPaused = true
        currentBox.reset()
        currentBox = when(this.state) {
            TutorialState.None -> currentBox
            TutorialState.Intro -> intro
            TutorialState.BasicGameplayExplain -> aiming
            TutorialState.ChargeExplain -> chargingBonus
            TutorialState.ChargeBonusCanceledExplain -> chargingBonusCanceled
            TutorialState.TeleportBonusExplain -> teleport
            TutorialState.TopWinsExplain -> topWinsPausing
            TutorialState.BottomWinsExplain -> bottomWinsPausing
            TutorialState.PauseExplain -> paused
            else -> currentBox
        }
    }

    fun onTouchEvent(event: MotionEvent?) {
        if (showing) {
            if (event?.action == MotionEvent.ACTION_UP) {
                currentBox.closing = true
            }
        }
    }

    fun checkProgress() {
        if (showing && currentBox.closed) {
            currentBox = currentBox.getNext()
            if (currentBox.closed) {
                Settings.tutorialPaused = false
                Settings.pauseGame = false
                showing = false
            }
        }
    }
}