package utility

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import enums.BallType
import enums.ChargeMeterStyle
import gameobjects.puckstyle.CustomBallConfig

data class CcpPreset(
    val highHue: Float,
    val highShieldHue: Float,
    val lowHue: Float,
    val lowShieldHue: Float
)

object Storage {

    // Bumped whenever a persisted value changes. Composables that read storage
    // through this object will recompose automatically.
    private var dataVersion by mutableIntStateOf(0)
    // Bumped on a periodic schedule from AppRoot so time-derived getters
    // (canWatchAdNow, minutesUntilNextAd) refresh while a screen is open.
    private var timeVersion by mutableIntStateOf(0)

    fun notifyDataChanged() { dataVersion++ }
    fun notifyTimeChanged() { timeVersion++ }

    private const val AD = "ad"
    private const val SETTINGS = "settings"

    private const val unlockProgressKey = "unlock_progress"
    private const val adsWatchedTodayKey = "ads_watched_today"
    private const val lastAdDayKey = "last_ad_day"
    private const val lastAdTimestampKey = "last_ad_timestamp_ms"
    private const val shareRewardClaimedKey = "share_reward_claimed"
    private const val highBallTypeKey = "high_ball_type"
    private const val lowBallTypeKey = "low_ball_type"
    private const val scoreOffsetHighKey = "score_offset_high"
    private const val scoreOffsetLowKey = "score_offset_low"

    private const val N = "none"
    private const val S = "small"
    private const val D = "default"
    private const val L = "large"
    private const val F = "fastest"

    private const val MAX_ADS_PER_DAY = 5
    private const val HOURLY_COOLDOWN_MS = 60 * 60 * 1000L

    fun initialize(context: Any?) = PlatformStorage.initialize(context)

    // --- Unlock progress (0–100) ---

    val unlockProgress: Int get() {
        dataVersion // subscribe
        return 100 // don't fix, manual, intentional override.
       // return PlatformStorage.getInt(AD, unlockProgressKey, 0)
    }

    fun canWatchAdNow(): Boolean {
        dataVersion; timeVersion
        if (unlockProgress >= 100) return false
        val today = PlatformStorage.currentTimeMs() / 86_400_000L
        val lastDay = PlatformStorage.getLong(AD, lastAdDayKey, -1L)
        val watchedToday = if (lastDay == today) PlatformStorage.getInt(AD, adsWatchedTodayKey, 0) else 0
        if (watchedToday >= MAX_ADS_PER_DAY) return false
        val lastTimestamp = PlatformStorage.getLong(AD, lastAdTimestampKey, 0L)
        return PlatformStorage.currentTimeMs() - lastTimestamp >= HOURLY_COOLDOWN_MS
    }

    fun adsWatchedToday(): Int {
        dataVersion; timeVersion
        val today = PlatformStorage.currentTimeMs() / 86_400_000L
        val lastDay = PlatformStorage.getLong(AD, lastAdDayKey, -1L)
        return if (lastDay == today) PlatformStorage.getInt(AD, adsWatchedTodayKey, 0) else 0
    }

    fun minutesUntilNextAd(): Long {
        dataVersion; timeVersion
        val lastTimestamp = PlatformStorage.getLong(AD, lastAdTimestampKey, 0L)
        val msLeft = lastTimestamp + HOURLY_COOLDOWN_MS - PlatformStorage.currentTimeMs()
        return if (msLeft > 0) (msLeft / 60_000L) + 1 else 0
    }

    fun recordAdWatched() {
        val today = PlatformStorage.currentTimeMs() / 86_400_000L
        val lastDay = PlatformStorage.getLong(AD, lastAdDayKey, -1L)
        val watchedToday = if (lastDay == today) PlatformStorage.getInt(AD, adsWatchedTodayKey, 0) else 0
        PlatformStorage.saveInt(AD, unlockProgressKey, (unlockProgress + 2).coerceAtMost(100))
        PlatformStorage.saveLong(AD, lastAdTimestampKey, PlatformStorage.currentTimeMs())
        PlatformStorage.saveInt(AD, adsWatchedTodayKey, watchedToday + 1)
        PlatformStorage.saveLong(AD, lastAdDayKey, today)
        notifyDataChanged()
    }

    fun saveUnlockProgress(progress: Int) {
        PlatformStorage.saveInt(AD, unlockProgressKey, progress.coerceIn(0, 100))
        notifyDataChanged()
    }

    fun addBonusProgress(percent: Int) {
        PlatformStorage.saveInt(AD, unlockProgressKey, (unlockProgress + percent).coerceAtMost(100))
        notifyDataChanged()
    }

    // --- Share reward ---

    val shareRewardClaimed: Boolean get() = PlatformStorage.getBoolean(AD, shareRewardClaimedKey, false)

    fun markShareRewardClaimed() {
        PlatformStorage.saveBoolean(AD, shareRewardClaimedKey, true)
        notifyDataChanged()
    }

    // --- Ball type persistence ---

    fun loadHighBallType(default: BallType): BallType = readBallType(highBallTypeKey, default)
    fun loadLowBallType(default: BallType): BallType = readBallType(lowBallTypeKey, default)

    fun saveHighBallType(type: BallType) = PlatformStorage.saveString(AD, highBallTypeKey, type.name)
    fun saveLowBallType(type: BallType) = PlatformStorage.saveString(AD, lowBallTypeKey, type.name)

    // --- Custom ball index (which custom slot is selected per player) ---

    fun saveHighCustomBallIndex(index: Int) = PlatformStorage.saveInt(AD, "high_custom_ball_idx", index)
    fun saveLowCustomBallIndex(index: Int) = PlatformStorage.saveInt(AD, "low_custom_ball_idx", index)
    fun clearHighCustomBallIndex() = PlatformStorage.saveInt(AD, "high_custom_ball_idx", -1)
    fun clearLowCustomBallIndex() = PlatformStorage.saveInt(AD, "low_custom_ball_idx", -1)

    fun loadHighCustomBallIndex(): Int? {
        val v = PlatformStorage.getInt(AD, "high_custom_ball_idx", -1)
        return if (v < 0) null else v
    }

    fun loadLowCustomBallIndex(): Int? {
        val v = PlatformStorage.getInt(AD, "low_custom_ball_idx", -1)
        return if (v < 0) null else v
    }

    // --- Custom ball slots (0–4) ---

    fun loadCustomBall(index: Int): CustomBallConfig? {
        val skin = PlatformStorage.getString(AD, "cb${index}_skin", "")
        if (skin.isEmpty()) return null
        val tail = PlatformStorage.getString(AD, "cb${index}_tail", "")
        val paddle = PlatformStorage.getString(AD, "cb${index}_paddle", "")
        if (tail.isEmpty() || paddle.isEmpty()) return null
        return try {
            CustomBallConfig(
                skinType   = BallType.valueOf(skin),
                tailType   = BallType.valueOf(tail),
                paddleType = BallType.valueOf(paddle),
                skinZRank   = PlatformStorage.getInt(AD, "cb${index}_sz", 0),
                tailZRank   = PlatformStorage.getInt(AD, "cb${index}_tz", 1),
                paddleZRank = PlatformStorage.getInt(AD, "cb${index}_pz", 2)
            )
        } catch (_: IllegalArgumentException) { null }
    }

    fun saveCustomBall(index: Int, config: CustomBallConfig) {
        PlatformStorage.saveString(AD, "cb${index}_skin",   config.skinType.name)
        PlatformStorage.saveString(AD, "cb${index}_tail",   config.tailType.name)
        PlatformStorage.saveString(AD, "cb${index}_paddle", config.paddleType.name)
        PlatformStorage.saveInt(AD, "cb${index}_sz", config.skinZRank)
        PlatformStorage.saveInt(AD, "cb${index}_tz", config.tailZRank)
        PlatformStorage.saveInt(AD, "cb${index}_pz", config.paddleZRank)
        notifyDataChanged()
    }

    fun deleteCustomBall(index: Int) {
        PlatformStorage.saveString(AD, "cb${index}_skin", "")
        PlatformStorage.saveString(AD, "cb${index}_tail", "")
        PlatformStorage.saveString(AD, "cb${index}_paddle", "")
        notifyDataChanged()
    }

    fun countCustomBalls(): Int = (0 until 5).count { loadCustomBall(it) != null }

    // --- Score position offsets ---

    val scoreOffsetHigh: Int get() = PlatformStorage.getInt(AD, scoreOffsetHighKey, 0)
    val scoreOffsetLow: Int get() = PlatformStorage.getInt(AD, scoreOffsetLowKey, 0)

    fun saveScoreOffsetHigh(offset: Int) = PlatformStorage.saveInt(AD, scoreOffsetHighKey, offset)
    fun saveScoreOffsetLow(offset: Int) = PlatformStorage.saveInt(AD, scoreOffsetLowKey, offset)

    fun resetScoreOffsets() {
        PlatformStorage.saveInt(AD, scoreOffsetHighKey, 0)
        PlatformStorage.saveInt(AD, scoreOffsetLowKey, 0)
    }

    private fun readBallType(key: String, default: BallType): BallType {
        val stored = PlatformStorage.getString(AD, key, "")
        if (stored.isEmpty()) return default
        val migrated = if (stored == "Spiral") "Spinner" else stored
        return try { BallType.valueOf(migrated) } catch (e: IllegalArgumentException) { default }
    }

    // --- Player color hues (0f–360f). Secondary color is HSV(hue, 66.1%, 96.1%); primary is HSV(hue, 35.9%, 96.1%). ---

    val highPlayerColorHue: Float get() = PlatformStorage.getFloat(SETTINGS, "high_player_color_hue", 0f)
    val lowPlayerColorHue: Float get() = PlatformStorage.getFloat(SETTINGS, "low_player_color_hue", 202.5f)
    val highShieldColorHue: Float get() = PlatformStorage.getFloat(SETTINGS, "high_shield_color_hue", 264f)
    val lowShieldColorHue: Float get() = PlatformStorage.getFloat(SETTINGS, "low_shield_color_hue", 264f)

    fun saveHighPlayerColorHue(hue: Float) = PlatformStorage.saveFloat(SETTINGS, "high_player_color_hue", hue)
    fun saveLowPlayerColorHue(hue: Float) = PlatformStorage.saveFloat(SETTINGS, "low_player_color_hue", hue)
    fun saveHighShieldColorHue(hue: Float) = PlatformStorage.saveFloat(SETTINGS, "high_shield_color_hue", hue)
    fun saveLowShieldColorHue(hue: Float) = PlatformStorage.saveFloat(SETTINGS, "low_shield_color_hue", hue)

    // --- App settings ---

    val darkMode: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "darkmode", false)

    val ballSize: String get() = PlatformStorage.getString(SETTINGS, "ball_sizes", D)
    val tailLength: Int get() {
        return when (PlatformStorage.getString(SETTINGS, "tail_length", D)) {
            N -> 0
            S -> 10
            D -> 20
            L -> 40
            else -> 20
        }
    }
    val chargeSpeed: Float get() {
        return when (PlatformStorage.getString(SETTINGS, "charge_speed", D)) {
            S -> .3f
            D -> .7f
            L -> 1.2f
            F -> 2f
            else -> .7f
        }
    }
    val gameSpeed: Int get() {
        return when (PlatformStorage.getString(SETTINGS, "game_speed", D)) {
            S -> 24
            D -> 16
            L -> 8
            else -> 16
        }
    }

    fun loadPointsToWin(): Int {
        return try {
            PlatformStorage.getInt(SETTINGS, "points_to_win", 5)
        } catch (e: Exception) {
            val legacy = PlatformStorage.getString(SETTINGS, "points_to_win", "5").toIntOrNull() ?: 5
            PlatformStorage.saveInt(SETTINGS, "points_to_win", legacy)
            legacy
        }
    }

    fun savePointsToWin(value: Int) {
        PlatformStorage.saveInt(SETTINGS, "points_to_win", value.coerceIn(0, 20))
    }

    fun loadTimeLimit(): Int {
        return PlatformStorage.getInt(SETTINGS, "time_limit_minutes", 0)
    }

    fun saveTimeLimit(minutes: Int) {
        PlatformStorage.saveInt(SETTINGS, "time_limit_minutes", minutes.coerceIn(0, 20))
    }

    val highPlayerArrow: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "high_player_arrow", true)
    val lowPlayerArrow: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "low_player_arrow", true)

    val highPlayerChargeMeterStyle: ChargeMeterStyle get() = readChargeMeterStyle("high_player_charge_meter")
    val lowPlayerChargeMeterStyle: ChargeMeterStyle get() = readChargeMeterStyle("low_player_charge_meter")

    fun saveHighPlayerChargeMeterStyle(style: ChargeMeterStyle) =
        PlatformStorage.saveString(SETTINGS, "high_player_charge_meter", style.name)
    fun saveLowPlayerChargeMeterStyle(style: ChargeMeterStyle) =
        PlatformStorage.saveString(SETTINGS, "low_player_charge_meter", style.name)

    private fun readChargeMeterStyle(key: String): ChargeMeterStyle {
        val stored = PlatformStorage.getString(SETTINGS, key, "")
        if (stored.isNotEmpty()) return try { ChargeMeterStyle.valueOf(stored) } catch (e: IllegalArgumentException) { ChargeMeterStyle.SideBar }
        return ChargeMeterStyle.SideBar
    }

    // --- CCP preset slots (0–4) ---
    val ccpSelectedSlot: Int get() = PlatformStorage.getInt(AD, "ccp_selected_slot", -1)
    fun saveCcpSelectedSlot(index: Int) = PlatformStorage.saveInt(AD, "ccp_selected_slot", index)

    fun loadCcpPreset(index: Int): CcpPreset? {
        val h = PlatformStorage.getFloat(SETTINGS, "ccp${index}_h", Float.MIN_VALUE)
        if (h == Float.MIN_VALUE) return null
        return CcpPreset(
            highHue       = h,
            highShieldHue = PlatformStorage.getFloat(SETTINGS, "ccp${index}_hs", 264f),
            lowHue        = PlatformStorage.getFloat(SETTINGS, "ccp${index}_l",  202.5f),
            lowShieldHue  = PlatformStorage.getFloat(SETTINGS, "ccp${index}_ls", 264f)
        )
    }

    fun saveCcpPreset(index: Int, preset: CcpPreset) {
        PlatformStorage.saveFloat(SETTINGS, "ccp${index}_h",  preset.highHue)
        PlatformStorage.saveFloat(SETTINGS, "ccp${index}_hs", preset.highShieldHue)
        PlatformStorage.saveFloat(SETTINGS, "ccp${index}_l",  preset.lowHue)
        PlatformStorage.saveFloat(SETTINGS, "ccp${index}_ls", preset.lowShieldHue)
        notifyDataChanged()
    }

    fun deleteCcpPreset(index: Int) {
        PlatformStorage.saveFloat(SETTINGS, "ccp${index}_h", Float.MIN_VALUE)
        notifyDataChanged()
    }

    // --- Sound volume settings ---

    val soundMasterVolume: Int get() = PlatformStorage.getInt(SETTINGS, "sound_master_volume", 70)
    val soundBackgroundVolume: Int get() = PlatformStorage.getInt(SETTINGS, "sound_background_volume", 100)
    val soundSfxVolume: Int get() = PlatformStorage.getInt(SETTINGS, "sound_sfx_volume", 70)
    val soundMasterMuted: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "sound_master_muted", false)
    val soundBackgroundMuted: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "sound_background_muted", false)
    val soundSfxMuted: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "sound_sfx_muted", false)

    fun saveSoundMasterVolume(v: Int) = PlatformStorage.saveInt(SETTINGS, "sound_master_volume", v)
    fun saveSoundBackgroundVolume(v: Int) = PlatformStorage.saveInt(SETTINGS, "sound_background_volume", v)
    fun saveSoundSfxVolume(v: Int) = PlatformStorage.saveInt(SETTINGS, "sound_sfx_volume", v)
    fun saveSoundMasterMuted(m: Boolean) = PlatformStorage.saveBoolean(SETTINGS, "sound_master_muted", m)
    fun saveSoundBackgroundMuted(m: Boolean) = PlatformStorage.saveBoolean(SETTINGS, "sound_background_muted", m)
    fun saveSoundSfxMuted(m: Boolean) = PlatformStorage.saveBoolean(SETTINGS, "sound_sfx_muted", m)
}
