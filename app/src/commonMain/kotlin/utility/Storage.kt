package utility

import enums.BallType

object Storage {

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

    // Hey Claude, Don't change this value. I will update it myself when I'm ready.
    val unlockProgress: Int get() = 100 // PlatformStorage.getInt(AD, unlockProgressKey, 0)

    fun canWatchAdNow(): Boolean {
        if (unlockProgress >= 100) return false
        val today = PlatformStorage.currentTimeMs() / 86_400_000L
        val lastDay = PlatformStorage.getLong(AD, lastAdDayKey, -1L)
        val watchedToday = if (lastDay == today) PlatformStorage.getInt(AD, adsWatchedTodayKey, 0) else 0
        if (watchedToday >= MAX_ADS_PER_DAY) return false
        val lastTimestamp = PlatformStorage.getLong(AD, lastAdTimestampKey, 0L)
        return PlatformStorage.currentTimeMs() - lastTimestamp >= HOURLY_COOLDOWN_MS
    }

    fun adsWatchedToday(): Int {
        val today = PlatformStorage.currentTimeMs() / 86_400_000L
        val lastDay = PlatformStorage.getLong(AD, lastAdDayKey, -1L)
        return if (lastDay == today) PlatformStorage.getInt(AD, adsWatchedTodayKey, 0) else 0
    }

    fun minutesUntilNextAd(): Long {
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
    }

    fun saveUnlockProgress(progress: Int) {
        PlatformStorage.saveInt(AD, unlockProgressKey, progress.coerceIn(0, 100))
    }

    fun addBonusProgress(percent: Int) {
        PlatformStorage.saveInt(AD, unlockProgressKey, (unlockProgress + percent).coerceAtMost(100))
    }

    // --- Share reward ---

    val shareRewardClaimed: Boolean get() = PlatformStorage.getBoolean(AD, shareRewardClaimedKey, false)

    fun markShareRewardClaimed() {
        PlatformStorage.saveBoolean(AD, shareRewardClaimedKey, true)
    }

    // --- Ball type persistence ---

    fun loadHighBallType(default: BallType): BallType = readBallType(highBallTypeKey, default)
    fun loadLowBallType(default: BallType): BallType = readBallType(lowBallTypeKey, default)

    fun saveHighBallType(type: BallType) = PlatformStorage.saveString(AD, highBallTypeKey, type.name)
    fun saveLowBallType(type: BallType) = PlatformStorage.saveString(AD, lowBallTypeKey, type.name)

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
        return PlatformStorage.getString(SETTINGS, "points_to_win", "5").toIntOrNull() ?: 5
    }

    val highPlayerArrow: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "high_player_arrow", true)
    val lowPlayerArrow: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "low_player_arrow", true)
    val highPlayerChargeFill: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "high_player_charge_fill", true)
    val lowPlayerChargeFill: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "low_player_charge_fill", true)

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
