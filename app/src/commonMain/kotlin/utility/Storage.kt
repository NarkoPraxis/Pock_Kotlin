package utility

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import enums.BallType
import enums.ChargeMeterStyle
import enums.DarkModeSetting
import enums.DesignerPane
import enums.ScoreMenuSide
import enums.ScoreWindow
import enums.TouchScheme
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.CustomBallConfig

data class CcpPreset(
    val highHue: Float,
    val highShieldHue: Float,
    val lowHue: Float,
    val lowShieldHue: Float,
    // Rainbow colour overrides (see gameobjects.puckstyle.RainbowOverride). Stored as an override of
    // the hue, not a replacement: when true the hue above is ignored and that colour strobes; when
    // toggled off the hue returns.
    val highRainbow: Boolean = false,
    val highShieldRainbow: Boolean = false,
    val lowRainbow: Boolean = false,
    val lowShieldRainbow: Boolean = false
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
    private const val scoreMenuSideKey = "score_menu_side"
    private const val scoreWindowKey = "score_window_mode"
    private const val persistentEffectsKey = "persistent_effects"
    private const val ballDesignerUnifiedKey = "ball_designer_unified"
    private const val ballDesignerPaneKey = "ball_designer_pane"

    private const val N = "none"
    private const val S = "small"
    private const val D = "default"
    private const val L = "large"
    private const val F = "fastest"

    private const val MAX_ADS_PER_HOUR = 3
    private const val HOUR_MS = 60 * 60 * 1000L
    private const val recentAdTimestampsKey = "recent_ad_ts"

    /** Number of custom ball slots (2 free + 8 unlocked at 30%/40%…100%). */
    const val SLOT_COUNT = 10

    private const val unlockedSkinsKey   = "unlocked_skins"
    private const val unlockedTailsKey   = "unlocked_tails"
    private const val unlockedPaddlesKey = "unlocked_paddles"
    private const val unlockedColorsKey  = "unlocked_colors"
    // Bumped to _v2 when the seeded defaults were corrected (PokPok/Classic order + natural z-index
    // draw order for ball slots, plus a free-color CCP preset). A reinstall starts this false.
    private const val defaultsSeededKey  = "default_slots_seeded_v2"

    /**
     * Preset color carousel indices unlocked for free — the game's default branding colors only:
     * Red (0) = high-player default 0°, Sky Blue (8) = low-player default 202.5°, Purple (10) =
     * shield default 264°. Every other preset is ad-unlockable. (These hues must stay in lockstep
     * with the defaults in [highPlayerColorHue]/[lowPlayerColorHue]/[*ShieldColorHue] and the
     * matching entries in ColorCarousel.PRESETS, or a default color would resolve as locked.)
     */
    private val FREE_COLOR_INDICES = setOf(0, 8, 10)
    private const val CUSTOM_COLOR_INDEX = 13

    fun initialize(context: Any?) {
        PlatformStorage.initialize(context)
        ensureDefaultSlots()
    }

    // Ad --- Unlock progress (0–100) ---
    val unlockProgress: Int get() {
        dataVersion // subscribe
        return 100 // don't fix, manual, intentional override.
        //return PlatformStorage.getInt(AD, unlockProgressKey, 0)
    }

    /** Timestamps of ads watched within the last rolling hour (cap [MAX_ADS_PER_HOUR]). */
    private fun recentAdTimestamps(): List<Long> {
        val now = PlatformStorage.currentTimeMs()
        return PlatformStorage.getString(AD, recentAdTimestampsKey, "")
            .split(",")
            .mapNotNull { it.toLongOrNull() }
            .filter { now - it < HOUR_MS }
    }

    fun canWatchAdNow(): Boolean {
        dataVersion; timeVersion
        if (unlockProgress >= 100) return false
        return recentAdTimestamps().size < MAX_ADS_PER_HOUR
    }

    fun adsWatchedThisHour(): Int {
        dataVersion; timeVersion
        return recentAdTimestamps().size
    }

    /** Back-compat alias for the deprecated BallUnlock screens. */
    fun adsWatchedToday(): Int = adsWatchedThisHour()

    /** Minutes until the next ad becomes available (0 if available now). */
    fun minutesUntilNextAd(): Long {
        dataVersion; timeVersion
        val ts = recentAdTimestamps().sorted()
        if (ts.size < MAX_ADS_PER_HOUR) return 0
        val msLeft = ts.first() + HOUR_MS - PlatformStorage.currentTimeMs()
        return if (msLeft > 0) (msLeft / 60_000L) + 1 else 0
    }

    fun recordAdWatched() {
        val now = PlatformStorage.currentTimeMs()
        val updated = (recentAdTimestamps() + now).takeLast(MAX_ADS_PER_HOUR)
        PlatformStorage.saveString(AD, recentAdTimestampsKey, updated.joinToString(","))
        PlatformStorage.saveInt(AD, unlockProgressKey, (unlockProgress + 2).coerceAtMost(100))
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

    fun countCustomBalls(): Int = (0 until SLOT_COUNT).count { loadCustomBall(it) != null }

    /**
     * Seeds the free defaults on first run so a fresh install is fully playable without editing a
     * single custom setting, and using ONLY already-unlocked pieces (no slot starts on a locked
     * cosmetic — the game makes no safety checks on save files, so the defaults themselves must be
     * clean): slot 0 = PokPok, slot 1 = Classic (the only two free balls), each composed from its
     * own components' natural z-index draw order, plus CCP preset 0 built from the three free colors.
     * Guarded by a flag so deleting all custom balls/presets won't silently re-add them.
     */
    fun ensureDefaultSlots() {
        if (PlatformStorage.getBoolean(AD, defaultsSeededKey, false)) return
        if (countCustomBalls() == 0) {
            saveCustomBall(0, BallStyleFactory.naturalCustomConfig(BallType.PokPok, BallType.PokPok, BallType.PokPok))
            saveCustomBall(1, BallStyleFactory.naturalCustomConfig(BallType.Classic, BallType.Classic, BallType.Classic))
        }
        ensureDefaultColorPreset()
        PlatformStorage.saveBoolean(AD, defaultsSeededKey, true)
    }

    /**
     * Seeds CCP preset slot 0 with the three free colors and selects it: Red (top color), Sky Blue
     * (bottom color), Purple (both shields). Hues match [FREE_COLOR_INDICES] / ColorCarousel.PRESETS
     * exactly so they resolve as unlocked. The live player hues are persisted too, so the selected
     * preset and the gameplay palette agree from the first frame.
     */
    private fun ensureDefaultColorPreset() {
        if (loadCcpPreset(0) != null) return
        val preset = CcpPreset(highHue = 0f, highShieldHue = 264f, lowHue = 202.5f, lowShieldHue = 264f)
        saveCcpPreset(0, preset)
        saveCcpSelectedSlot(0)
        saveHighPlayerColorHue(preset.highHue)
        saveHighShieldColorHue(preset.highShieldHue)
        saveLowPlayerColorHue(preset.lowHue)
        saveLowShieldColorHue(preset.lowShieldHue)
    }

    // --- Custom ball slot gating (0–1 free; 2–9 unlock at 30%/40%…100%) ---

    fun isSlotUnlocked(index: Int): Boolean =
        index < 2 || unlockProgress >= slotRequiredPercent(index)

    fun slotRequiredPercent(index: Int): Int = if (index < 2) 0 else 30 + (index - 2) * 10

    // --- Per-component unlock state (skins / tails / paddles) ---

    fun isSkinUnlocked(type: BallType): Boolean   = isComponentUnlocked(type, unlockedSkinsKey)
    fun isTailUnlocked(type: BallType): Boolean   = isComponentUnlocked(type, unlockedTailsKey)
    fun isPaddleUnlocked(type: BallType): Boolean = isComponentUnlocked(type, unlockedPaddlesKey)

    fun unlockSkin(type: BallType)   = addToCsvSet(unlockedSkinsKey, type.name)
    fun unlockTail(type: BallType)   = addToCsvSet(unlockedTailsKey, type.name)
    fun unlockPaddle(type: BallType) = addToCsvSet(unlockedPaddlesKey, type.name)

    private fun isComponentUnlocked(type: BallType, key: String): Boolean {
        dataVersion // subscribe
        if (unlockProgress >= 100) return true
        return when (BallStyleFactory.tierOf(type)) {
            BallStyleFactory.Tier.Free    -> true
            BallStyleFactory.Tier.Premium -> false
            BallStyleFactory.Tier.Ad      -> readCsvSet(key).contains(type.name)
        }
    }

    // --- Per-preset color unlock state ---

    fun isColorUnlocked(presetIndex: Int): Boolean {
        dataVersion // subscribe
        if (presetIndex == CUSTOM_COLOR_INDEX) return unlockProgress >= 100
        if (presetIndex in FREE_COLOR_INDICES) return true
        if (unlockProgress >= 100) return true
        return readCsvSet(unlockedColorsKey).contains(presetIndex.toString())
    }

    fun unlockColor(presetIndex: Int) = addToCsvSet(unlockedColorsKey, presetIndex.toString())

    private fun readCsvSet(key: String): Set<String> {
        dataVersion // subscribe
        return PlatformStorage.getString(AD, key, "")
            .split(",")
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun addToCsvSet(key: String, value: String) {
        val set = readCsvSet(key).toMutableSet()
        if (set.add(value)) {
            PlatformStorage.saveString(AD, key, set.joinToString(","))
            notifyDataChanged()
        }
    }

    // --- Score dial side (replaces the old Score Placement offsets) ---
    // Which side edge the score dial / pause menu lives against. Default Left. Applies live
    // (Drawing reads Settings.scoreMenuSide each frame), so no restart is needed on change.

    var scoreMenuSide: ScoreMenuSide
        get() {
            val stored = PlatformStorage.getString(SETTINGS, scoreMenuSideKey, ScoreMenuSide.Left.name)
            return try { ScoreMenuSide.valueOf(stored) } catch (e: IllegalArgumentException) { ScoreMenuSide.Left }
        }
        set(value) {
            PlatformStorage.saveString(SETTINGS, scoreMenuSideKey, value.name)
            notifyDataChanged()
        }

    // --- Score window: how tightly the goal closes after a collision (see enums.ScoreWindow). ---
    // Read into Settings.scoreWindowMode in initializeForScreen and applied live from the settings
    // screen, so a change takes effect on the next match without a restart. Default Normal (the
    // original full-decay behaviour).

    var scoreWindowMode: ScoreWindow
        get() {
            val stored = PlatformStorage.getString(SETTINGS, scoreWindowKey, ScoreWindow.Normal.name)
            return try { ScoreWindow.valueOf(stored) } catch (e: IllegalArgumentException) { ScoreWindow.Normal }
        }
        set(value) = PlatformStorage.saveString(SETTINGS, scoreWindowKey, value.name)

    // --- Persistent effects toggle (Settings → Graphics). When off, Effects.drawEffects skips the
    // persistent-effect layer entirely (priority/score effects are unaffected). Default on. ---

    var persistentEffectsEnabled: Boolean
        get() = PlatformStorage.getBoolean(SETTINGS, persistentEffectsKey, true)
        set(value) = PlatformStorage.saveBoolean(SETTINGS, persistentEffectsKey, value)

    // --- Ball Designer remembered location: the Unified/Separate toggle and which pane (Style/Color)
    // was last open, so entering the designer from the main menu lands exactly where the player left.
    // Unified defaults on for a fresh install. ---

    var ballDesignerUnified: Boolean
        get() = PlatformStorage.getBoolean(SETTINGS, ballDesignerUnifiedKey, true)
        set(value) = PlatformStorage.saveBoolean(SETTINGS, ballDesignerUnifiedKey, value)

    var ballDesignerPane: DesignerPane
        get() {
            val stored = PlatformStorage.getString(SETTINGS, ballDesignerPaneKey, DesignerPane.Style.name)
            return try { DesignerPane.valueOf(stored) } catch (e: IllegalArgumentException) { DesignerPane.Style }
        }
        set(value) = PlatformStorage.saveString(SETTINGS, ballDesignerPaneKey, value.name)

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

    // --- Rainbow colour overrides (live values; mirrors the per-slot flags in CcpPreset). ---

    val highPlayerRainbow: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "high_player_rainbow", false)
    val highPlayerRainbowShield: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "high_shield_rainbow", false)
    val lowPlayerRainbow: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "low_player_rainbow", false)
    val lowPlayerRainbowShield: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "low_shield_rainbow", false)

    fun saveHighPlayerRainbow(on: Boolean) = PlatformStorage.saveBoolean(SETTINGS, "high_player_rainbow", on)
    fun saveHighPlayerRainbowShield(on: Boolean) = PlatformStorage.saveBoolean(SETTINGS, "high_shield_rainbow", on)
    fun saveLowPlayerRainbow(on: Boolean) = PlatformStorage.saveBoolean(SETTINGS, "low_player_rainbow", on)
    fun saveLowPlayerRainbowShield(on: Boolean) = PlatformStorage.saveBoolean(SETTINGS, "low_shield_rainbow", on)

    // --- App settings ---

    // Dark mode is a single tri-state preference (On / Off / Match Device). It is the only persisted
    // state; [darkMode] derives a concrete light/dark boolean from it against the live device theme.
    // The Android Activity recreate keys on this same pref change (see MainActivity).
    const val DARK_MODE_PREF_KEY = "dark_mode_setting"

    var darkModeSetting: DarkModeSetting
        get() {
            val stored = PlatformStorage.getString(SETTINGS, DARK_MODE_PREF_KEY, DarkModeSetting.System.name)
            return try { DarkModeSetting.valueOf(stored) } catch (e: IllegalArgumentException) { DarkModeSetting.System }
        }
        set(value) = PlatformStorage.saveString(SETTINGS, DARK_MODE_PREF_KEY, value.name)

    /** The tri-state preference resolved to a concrete light/dark boolean for the current device. */
    val darkMode: Boolean get() = when (darkModeSetting) {
        DarkModeSetting.On -> true
        DarkModeSetting.Off -> false
        DarkModeSetting.System -> PlatformStorage.isSystemInDarkMode()
    }

    // Touch-ownership scheme: how a touch-down is assigned to a puck. Defaults to ByProximity
    // (nearest puck wins / "Closest") on a fresh install.
    const val TOUCH_SCHEME_PREF_KEY = "touch_scheme"

    var touchScheme: TouchScheme
        get() {
            val stored = PlatformStorage.getString(SETTINGS, TOUCH_SCHEME_PREF_KEY, TouchScheme.ByProximity.name)
            return try { TouchScheme.valueOf(stored) } catch (e: IllegalArgumentException) { TouchScheme.ByProximity }
        }
        set(value) = PlatformStorage.saveString(SETTINGS, TOUCH_SCHEME_PREF_KEY, value.name)

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
            S -> 20
            D -> 16
            L -> 12
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

    // --- CCP preset slot gating (0 free; 1–4 unlock at 20%/40%/60%/80%) ---
    // Mirrors the custom-ball slot gating but on 20% increments, so the fully-free custom color
    // selector at 100% is the ultimate progress reward.

    fun isCcpSlotUnlocked(index: Int): Boolean =
        index < 1 || unlockProgress >= ccpSlotRequiredPercent(index)

    fun ccpSlotRequiredPercent(index: Int): Int = if (index < 1) 0 else index * 20

    fun loadCcpPreset(index: Int): CcpPreset? {
        val h = PlatformStorage.getFloat(SETTINGS, "ccp${index}_h", Float.MIN_VALUE)
        if (h == Float.MIN_VALUE) return null
        return CcpPreset(
            highHue       = h,
            highShieldHue = PlatformStorage.getFloat(SETTINGS, "ccp${index}_hs", 264f),
            lowHue        = PlatformStorage.getFloat(SETTINGS, "ccp${index}_l",  202.5f),
            lowShieldHue  = PlatformStorage.getFloat(SETTINGS, "ccp${index}_ls", 264f),
            highRainbow       = PlatformStorage.getBoolean(SETTINGS, "ccp${index}_rb_h",  false),
            highShieldRainbow = PlatformStorage.getBoolean(SETTINGS, "ccp${index}_rb_hs", false),
            lowRainbow        = PlatformStorage.getBoolean(SETTINGS, "ccp${index}_rb_l",  false),
            lowShieldRainbow  = PlatformStorage.getBoolean(SETTINGS, "ccp${index}_rb_ls", false)
        )
    }

    fun saveCcpPreset(index: Int, preset: CcpPreset) {
        PlatformStorage.saveFloat(SETTINGS, "ccp${index}_h",  preset.highHue)
        PlatformStorage.saveFloat(SETTINGS, "ccp${index}_hs", preset.highShieldHue)
        PlatformStorage.saveFloat(SETTINGS, "ccp${index}_l",  preset.lowHue)
        PlatformStorage.saveFloat(SETTINGS, "ccp${index}_ls", preset.lowShieldHue)
        PlatformStorage.saveBoolean(SETTINGS, "ccp${index}_rb_h",  preset.highRainbow)
        PlatformStorage.saveBoolean(SETTINGS, "ccp${index}_rb_hs", preset.highShieldRainbow)
        PlatformStorage.saveBoolean(SETTINGS, "ccp${index}_rb_l",  preset.lowRainbow)
        PlatformStorage.saveBoolean(SETTINGS, "ccp${index}_rb_ls", preset.lowShieldRainbow)
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

    // --- Menu sound toggle (independent of the in-game volume/mute settings). ---
    // A single all-or-nothing switch: when muted, both the menu ambiance and the demo game's SFX
    // are silenced across all menu screens (Main Menu, Settings, Ball Designer). Stored as a
    // "muted" boolean to mirror the other sound mutes; default false = plays.
    // Gates Sounds.playMenuAmbiance (music) and effectiveSfxVol while Settings.isDemoMode (SFX).

    val menusMuted: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "menus_muted", false)

    fun saveMenusMuted(m: Boolean) = PlatformStorage.saveBoolean(SETTINGS, "menus_muted", m)

    // --- Dev-only frame profiler toggle (see utility.FrameProfiler). ---
    // Default false; only writable from the dev Settings toggle gated behind FrameProfiler.DEV_TOOLS,
    // so a release build with DEV_TOOLS=false can never turn it on and the profiler stays a no-op.

    val profilerEnabled: Boolean get() = PlatformStorage.getBoolean(SETTINGS, "profiler_enabled", false)

    fun saveProfilerEnabled(on: Boolean) = PlatformStorage.saveBoolean(SETTINGS, "profiler_enabled", on)
}
