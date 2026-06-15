package enums

/**
 * Dark-mode preference. [On]/[Off] force the theme explicitly; [System] ("Match Device") follows
 * the device's OS-level dark mode so the app and the system splash screen always agree.
 */
enum class DarkModeSetting { On, Off, System }
