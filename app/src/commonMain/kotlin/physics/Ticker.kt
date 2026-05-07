package physics

import gameobjects.Settings

open class Ticker(var value: Int, var accending: Boolean = false) {
    var referanceValue: Int = value
    private var _ratio = 0f
    var dirty = true
    var resetCount = 0
    var ticking = false

    init {
        if (accending) {
            referanceValue = value
            value = 0
        }
    }
    open val tick : Boolean
       get() {
           dirty = true
           ticking = true
           return if (Settings.pauseGame) {
               if (accending) {
                   value >= referanceValue
               } else {
                   value <= 0
               }
           } else if (accending) {
                   value++ >= referanceValue
           } else {
               value-- <= 0
           }
       }

    val isTicking : Boolean
        get() {
            return ticking && !finished
        }

    val finished : Boolean
        get() {
            return if (accending) {
                value >= referanceValue
            } else {
                value <= 0
            }
        }

   val ratio : Float
        get() {
            if (dirty) {
                _ratio = value / referanceValue.toFloat()
                dirty = false
            }
            return _ratio
        }

    val resetFlip : Boolean
        get() = resetCount % 2 == 0

    fun end() {
        dirty = true
        value = if (accending) {
            referanceValue
        } else {
            0
        }
    }

    fun reset(newValue: Int = referanceValue) {
        dirty = true
        ticking = false
        value = if (accending) 0 else newValue
        referanceValue = newValue
        resetCount++
    }
}