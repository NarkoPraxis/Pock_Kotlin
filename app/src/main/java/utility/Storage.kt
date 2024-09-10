package utility

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.example.puck.R
import java.io.*
import java.time.LocalDate
import java.time.LocalDateTime

object Storage {

    lateinit var ad: SharedPreferences
    lateinit var settings: SharedPreferences


    private const val adPreferances = "adPreferences"
    private const val remainingKey = "ads_remaining"
    private const val lastSeenAdKey = "last_seen_ad_date"

    private const val S = "small"
    private const val D = "default"
    private const val L = "large"

    private lateinit var context: Context

    fun initialize(context: Context) {
        this.context = context
        ad = context.getSharedPreferences(adPreferances, Context.MODE_PRIVATE)
        settings = PreferenceManager.getDefaultSharedPreferences(context)
    }

    val adsRemaining : Int get() = ad.getInt(remainingKey, 100)
    val lastSeenAdDate : String? get() = ad.getString(lastSeenAdKey, LocalDate.now().minusDays(1).toString())

    fun storeAdsRemaining(adsRemaining: Int) {
        val editor = ad.edit()
        editor.putInt(remainingKey, adsRemaining)
        editor.apply()
    }

    fun storeAndUpdateAdsRemaining(adsRemaining: Int) {
        val editor = ad.edit()
        editor.putInt(remainingKey, adsRemaining)
        editor.putString(lastSeenAdKey, LocalDateTime.now().toLocalDate().toString())
        editor.apply()
    }

    fun storeAdShownDate() {
        val editor = ad.edit()
        editor.putString(lastSeenAdKey, LocalDateTime.now().toLocalDate().toString())
        editor.apply()
    }

    val darkMode : Boolean get() {
        return settings.getBoolean("darkmode", false)
    }

    val ballSize : String? get() = settings.getString("ball_sizes", D)
    val tailLength : Int get() {
        return when(settings.getString("tail_length", D)) {
            S -> 10
            D -> 20
            L -> 40
            else -> 20
        }
    }
    val maxBonusTickerTime : Int get() {
        return when(settings.getString("bonus_duration", D)) {
            S -> 100
            D -> 200
            L -> 400
            else -> 200
        }
    }
    val launchBonus : Float get() {
        return when(settings.getString("bounce_bonus", D)) {
            S-> 5f
            D -> 10f
            L -> 20f
            else -> 10f
        }
    }
    val chargeSpeed : Float get() {
        return when(settings.getString("charge_speed", D)) {
            S -> .3f
            D -> .7f
            L -> 1.2f
            else -> .7f
        }
    }
    val gameSpeed : Int get() {
        return when(settings.getString("game_speed", D)) {
            S -> 32
            D -> 16
            L -> 8
            else -> 16
        }
    }




//    fun restoreDefaults(context: Context) {
//        val settings = GameSettings()
//        writeFile(context, customSettingsFileName, Json.stringify(GameSettings.serializer(), settings))
//    }
//
//    fun writeSettings(context: Context, settings: GameSettings) {
//        writeFile(context, customSettingsFileName, Json.stringify(GameSettings.serializer(), settings))
//    }
//
//    fun getSettings(context: Context) : GameSettings {
//        return try{
//            val settingsString = readFile(context, customSettingsFileName)
//            Json.parse(GameSettings.serializer(), settingsString)
//        } catch(exc: Exception) {
//            GameSettings()
//        }
//    }

    private fun readFile(context: Context, fileName: String) : String {
        val sb = StringBuilder()


        val inputStream = context.openFileInput(fileName)
        val streamReader = InputStreamReader(inputStream)
        val bufferedReader = BufferedReader(streamReader)
        var text: String? = null
        while ({ text = bufferedReader.readLine(); text  }() != null) {
            sb.append(text)
        }


        return sb.toString()
    }

    private fun writeFile(context: Context, fileName: String, data: String) {
        try {
            val outputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE)
            outputStream.write(data.toByteArray())
        } catch (e: IOException) {
            Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show()
        }
    }
}
