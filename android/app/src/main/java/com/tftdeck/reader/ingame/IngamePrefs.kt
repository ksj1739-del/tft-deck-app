package com.tftdeck.reader.ingame

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 게임 연동 설정.
 *
 * 설정 화면과 오버레이 서비스가 같은 값을 동시에 본다. 스위치를 바꾸면 떠 있는 서비스가
 * 곧바로 따라야 하므로 SharedPreferences 값을 StateFlow로도 들고 있는다.
 */
class IngamePrefs private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _detectEnabled = MutableStateFlow(prefs.getBoolean(KEY_DETECT, false))
    /** 'TFT 실행 감지'. 사용 기록 접근 권한이 있어야 실제로 돈다. */
    val detectEnabled: StateFlow<Boolean> = _detectEnabled.asStateFlow()

    private val _autoOverlay = MutableStateFlow(prefs.getBoolean(KEY_AUTO_OVERLAY, true))
    /** 'TFT가 켜지면 오버레이 자동 표시'. 감지를 켠 사람이 가장 바라는 동작이라 기본으로 켠다. */
    val autoOverlay: StateFlow<Boolean> = _autoOverlay.asStateFlow()

    private val _resultNotify = MutableStateFlow(prefs.getBoolean(KEY_RESULT_NOTIFY, true))
    /** '게임이 끝나면 결과 알림'. */
    val resultNotify: StateFlow<Boolean> = _resultNotify.asStateFlow()

    private val _showOutsideTft = MutableStateFlow(prefs.getBoolean(KEY_SHOW_OUTSIDE, false))
    /** 자동 표시 중에도 홈 화면 등 TFT 밖에서 오버레이를 계속 보일지. */
    val showOutsideTft: StateFlow<Boolean> = _showOutsideTft.asStateFlow()

    private val _liveSpectateAvailable = MutableStateFlow(prefs.getBoolean(KEY_LIVE_AVAILABLE, false))
    /**
     * 수집기가 매일 확인해 flags.json에 적는 값. 진행 중 게임 데이터가 열려 있을 때만 true다.
     * 2026-09 기준으로는 원천 데이터가 0건이라 false가 정상이다.
     */
    val liveSpectateAvailable: StateFlow<Boolean> = _liveSpectateAvailable.asStateFlow()

    /** flags.json의 checkedAt 원문(수집기 기준 시각). 표시·로그용. */
    val liveSpectateCheckedAt: String?
        get() = prefs.getString(KEY_LIVE_CHECKED_AT, null)

    /** flags.json을 마지막으로 받은 기기 시각. 하루 한 번만 받기 위해 쓴다. */
    val lastFlagsFetch: Long
        get() = prefs.getLong(KEY_FLAGS_FETCHED_AT, 0L)

    fun setDetectEnabled(value: Boolean) = put(KEY_DETECT, value, _detectEnabled)

    fun setAutoOverlay(value: Boolean) = put(KEY_AUTO_OVERLAY, value, _autoOverlay)

    fun setResultNotify(value: Boolean) = put(KEY_RESULT_NOTIFY, value, _resultNotify)

    fun setShowOutsideTft(value: Boolean) = put(KEY_SHOW_OUTSIDE, value, _showOutsideTft)

    fun setLiveSpectate(available: Boolean, checkedAt: String?, fetchedAt: Long) {
        prefs.edit()
            .putBoolean(KEY_LIVE_AVAILABLE, available)
            .putString(KEY_LIVE_CHECKED_AT, checkedAt)
            .putLong(KEY_FLAGS_FETCHED_AT, fetchedAt)
            .apply()
        _liveSpectateAvailable.value = available
    }

    private fun put(key: String, value: Boolean, flow: MutableStateFlow<Boolean>) {
        prefs.edit().putBoolean(key, value).apply()
        flow.value = value
    }

    companion object {
        private const val PREFS = "ingame"
        private const val KEY_DETECT = "detect_enabled"
        private const val KEY_AUTO_OVERLAY = "auto_overlay"
        private const val KEY_RESULT_NOTIFY = "result_notify"
        private const val KEY_SHOW_OUTSIDE = "show_outside_tft"
        private const val KEY_LIVE_AVAILABLE = "live_spectate_available"
        private const val KEY_LIVE_CHECKED_AT = "live_spectate_checked_at"
        private const val KEY_FLAGS_FETCHED_AT = "last_flags_fetch"

        @Volatile
        private var instance: IngamePrefs? = null

        fun get(context: Context): IngamePrefs =
            instance ?: synchronized(this) {
                instance ?: IngamePrefs(context).also { instance = it }
            }
    }
}
