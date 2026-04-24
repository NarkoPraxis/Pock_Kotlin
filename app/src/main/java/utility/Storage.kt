package utility

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.example.puck.R
import enums.BallType
import java.io.*
import java.time.LocalDate

object Storage {

    lateinit var ad: SharedPreferences
    lateinit var settings: SharedPreferences

    private const val adPreferances = "adPreferences"
    private const val unlockProgressKey = "unlock_progress"
    private const val adsWatchedTodayKey = "ads_watched_today"
    private const val lastAdDateKey = "last_ad_date"
    private const val lastAdTimestampKey = "last_ad_timestamp_ms"
    private const val shareRewardClaimedKey = "share_reward_claimed"
    private const val highBallTypeKey = "high_ball_type"
    private const val lowBallTypeKey = "low_ball_type"
    private const val scoreOffsetHighKey = "score_offset_high"
    private const val scoreOffsetLowKey = "score_offset_low"

    private const val S = "small"
    private const val D = "default"
    private const val L = "large"

    private const val MAX_ADS_PER_DAY = 5
    private const val HOURLY_COOLDOWN_MS = 60 * 60 * 1000L

    private lateinit var context: Context

    fun initialize(context: Context) {
        this.context = context
        ad = context.getSharedPreferences(adPreferances, Context.MODE_PRIVATE)
        settings = PreferenceManager.getDefaultSharedPreferences(context)
    }

    // --- Unlock progress (0–100) ---

   // TODO: uncomment this before launching game
    //val unlockProgress: Int get() = ad.getInt(unlockProgressKey, 0)
     val unlockProgress: Int get() = 100

    /** True when the user is allowed to watch an ad right now. */
    fun canWatchAdNow(): Boolean {
        if (unlockProgress >= 100) return false
        val today = LocalDate.now().toString()
        val lastDate = ad.getString(lastAdDateKey, "")
        val watchedToday = if (lastDate == today) ad.getInt(adsWatchedTodayKey, 0) else 0
        if (watchedToday >= MAX_ADS_PER_DAY) return false
        val lastTimestamp = ad.getLong(lastAdTimestampKey, 0L)
        return System.currentTimeMillis() - lastTimestamp >= HOURLY_COOLDOWN_MS
    }

    /** How many ads the user has watched today (resets at midnight). */
    fun adsWatchedToday(): Int {
        val today = LocalDate.now().toString()
        val lastDate = ad.getString(lastAdDateKey, "")
        return if (lastDate == today) ad.getInt(adsWatchedTodayKey, 0) else 0
    }

    /** Minutes remaining before the hourly cooldown expires. Returns 0 if ready. */
    fun minutesUntilNextAd(): Long {
        val lastTimestamp = ad.getLong(lastAdTimestampKey, 0L)
        val msLeft = lastTimestamp + HOURLY_COOLDOWN_MS - System.currentTimeMillis()
        return if (msLeft > 0) (msLeft / 60_000L) + 1 else 0
    }

    /** Called when the user earns a reward: increments progress by 2% and records timestamps. */
    fun recordAdWatched() {
        val today = LocalDate.now().toString()
        val lastDate = ad.getString(lastAdDateKey, "")
        val watchedToday = if (lastDate == today) ad.getInt(adsWatchedTodayKey, 0) else 0
        ad.edit()
            .putInt(unlockProgressKey, (unlockProgress + 2).coerceAtMost(100))
            .putLong(lastAdTimestampKey, System.currentTimeMillis())
            .putInt(adsWatchedTodayKey, watchedToday + 1)
            .putString(lastAdDateKey, today)
            .apply()
    }

    /** Directly set unlock progress (e.g. after IAP purchase). */
    fun saveUnlockProgress(progress: Int) {
        ad.edit().putInt(unlockProgressKey, progress.coerceIn(0, 100)).apply()
    }

    /** Add bonus unlock progress (e.g. share reward = +10%). */
    fun addBonusProgress(percent: Int) {
        ad.edit()
            .putInt(unlockProgressKey, (unlockProgress + percent).coerceAtMost(100))
            .apply()
    }

    // --- Share reward ---

    val shareRewardClaimed: Boolean get() = ad.getBoolean(shareRewardClaimedKey, false)

    fun markShareRewardClaimed() {
        ad.edit().putBoolean(shareRewardClaimedKey, true).apply()
    }

    // --- Ball type persistence ---

    fun loadHighBallType(default: BallType): BallType = readBallType(highBallTypeKey, default)
    fun loadLowBallType(default: BallType): BallType = readBallType(lowBallTypeKey, default)

    fun saveHighBallType(type: BallType) = ad.edit().putString(highBallTypeKey, type.name).apply()
    fun saveLowBallType(type: BallType) = ad.edit().putString(lowBallTypeKey, type.name).apply()

    // --- Score position offsets ---

    val scoreOffsetHigh: Int get() = ad.getInt(scoreOffsetHighKey, 0)
    val scoreOffsetLow: Int get() = ad.getInt(scoreOffsetLowKey, 0)

    fun saveScoreOffsetHigh(offset: Int) = ad.edit().putInt(scoreOffsetHighKey, offset).apply()
    fun saveScoreOffsetLow(offset: Int) = ad.edit().putInt(scoreOffsetLowKey, offset).apply()

    fun resetScoreOffsets() {
        ad.edit().remove(scoreOffsetHighKey).remove(scoreOffsetLowKey).apply()
    }

    private fun readBallType(key: String, default: BallType): BallType {
        val stored = ad.getString(key, null) ?: return default
        val migrated = if (stored == "Spiral") "Spinner" else stored
        return try { BallType.valueOf(migrated) } catch (e: IllegalArgumentException) { default }
    }

    // --- App settings (from PreferenceManager) ---

    val darkMode: Boolean get() = settings.getBoolean("darkmode", false)

    val ballSize: String? get() = settings.getString("ball_sizes", D)
    val tailLength: Int get() {
        return when (settings.getString("tail_length", D)) {
            S -> 10
            D -> 20
            L -> 40
            else -> 20
        }
    }
    val maxBonusTickerTime: Int get() {
        return when (settings.getString("bonus_duration", D)) {
            S -> 100
            D -> 200
            L -> 400
            else -> 200
        }
    }
    val launchBonus: Float get() {
        return when (settings.getString("bounce_bonus", D)) {
            S -> 5f
            D -> 10f
            L -> 20f
            else -> 10f
        }
    }
    val chargeSpeed: Float get() {
        return when (settings.getString("charge_speed", D)) {
            S -> .3f
            D -> .7f
            L -> 1.2f
            else -> .7f
        }
    }
    val gameSpeed: Int get() {
        return when (settings.getString("game_speed", D)) {
            S -> 32
            D -> 16
            L -> 8
            else -> 16
        }
    }

    fun loadPointsToWin(): Int {
        return settings.getString("points_to_win", "5")?.toIntOrNull() ?: 5
    }

    val countdownFramesPerBeat: Int get() {
        return when (settings.getString("countdown_speed", "slow")) {
            "fast" -> 18
            "fastest" -> 10
            else -> 33
        }
    }

    val highPlayerArrow: Boolean get() = settings.getBoolean("high_player_arrow", true)
    val lowPlayerArrow: Boolean get() = settings.getBoolean("low_player_arrow", true)
    val highPlayerChargeFill: Boolean get() = settings.getBoolean("high_player_charge_fill", true)
    val lowPlayerChargeFill: Boolean get() = settings.getBoolean("low_player_charge_fill", true)

    private fun readFile(context: Context, fileName: String): String {
        val sb = StringBuilder()
        val inputStream = context.openFileInput(fileName)
        val streamReader = InputStreamReader(inputStream)
        val bufferedReader = BufferedReader(streamReader)
        var text: String? = null
        while ({ text = bufferedReader.readLine(); text }() != null) {
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
