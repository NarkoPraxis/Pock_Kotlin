package utility

object LanguageHelper {
    private const val STORE = "settings"
    private const val KEY = "language"

    fun getCurrentCode(): String = PlatformStorage.getString(STORE, KEY, "")

    fun saveLanguage(code: String) = PlatformStorage.saveString(STORE, KEY, code)

    fun flagForCode(code: String): String = when (code) {
        "de" -> "🇩🇪"
        "es" -> "🇪🇸"
        "fr" -> "🇫🇷"
        "ja" -> "🇯🇵"
        "zh" -> "🇨🇳"
        else -> "🇺🇸"
    }
}
