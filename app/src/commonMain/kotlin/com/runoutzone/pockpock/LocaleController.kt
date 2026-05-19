package com.runoutzone.pockpock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Triggers a forced recomposition of the resource subtree after the platform
 * locale changes. iOS picks up the new `AppleLanguages` value via
 * `NSLocale.preferredLanguages` on the next composition, but nothing else
 * invalidates the cached `ResourceEnvironment`. Bumping `version` from
 * `key(LocaleController.version)` discards remembered string resources so
 * subsequent reads pull the new locale.
 */
object LocaleController {
    var version by mutableIntStateOf(0)
        private set

    fun bumpLocale() { version++ }
}
