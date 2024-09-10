package physics

import gameobjects.Settings

class TutorialTicker(value: Int, accending: Boolean = false) : Ticker(value, accending) {

    override val tick : Boolean
        get() {
            dirty = true
            return if (finished) {
                true
            } else {
                if (accending) {
                    value++ >= referanceValue
                } else {
                    value-- <= 0
                }
            }
        }
}