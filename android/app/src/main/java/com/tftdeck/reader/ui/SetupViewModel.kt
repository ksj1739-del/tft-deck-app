package com.tftdeck.reader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tftdeck.reader.data.DeckRepository
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.FeedVersion
import com.tftdeck.reader.data.PlayerProfile
import com.tftdeck.reader.data.ProfileRepository
import com.tftdeck.reader.data.ProfileState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 앱을 쓸 준비를 돕는 화면 상태: '게임 위에 띄우기' 시트와 첫 동기화 알림.
 *
 * 액티비티 범위에서 하나다(MainActivity 의 AppRoot 가 만든다). 권한 화면에 다녀오는 동안 기억할 요청을 들고 있어
 * 그사이 화면이 회전해도 이어서 띄운다. 판단 규칙은 아래 파일 수준 함수로 두어 단위 테스트한다(FirstRunStateTest).
 */
class SetupViewModel(app: Application) : AndroidViewModel(app) {

    // -- '게임 위에 띄우기' 시트 ---------------------------------------------

    private val _overlaySheet = MutableStateFlow<OverlaySheet?>(null)

    /** 열려 있는 시트. null 이면 닫힘. 덱 탭 앱바·덱 상세·내 정보의 오버레이 스위치가 같은 시트를 연다. */
    val overlaySheet: StateFlow<OverlaySheet?> = _overlaySheet.asStateFlow()

    fun openOverlaySheet(deckId: String?) {
        _overlaySheet.value = OverlaySheet(deckId)
    }

    fun closeOverlaySheet() {
        _overlaySheet.value = null
    }

    /** '다른 앱 위에 표시' 권한 화면에 다녀오는 동안 기억하는 띄우기 요청. */
    private val pendingLaunch = PendingRequest<OverlayLaunch>()

    fun awaitOverlayPermission(launch: OverlayLaunch) = pendingLaunch.request(launch)

    /** 화면이 다시 보일 때. 권한이 생겼으면 이어서 띄울 요청을 돌려준다. 어느 쪽이든 요청은 지운다. */
    fun resumeLaunch(canDrawOverlays: Boolean): OverlayLaunch? = pendingLaunch.consume(canDrawOverlays)

    // -- 첫 동기화 ----------------------------------------------------------

    private val _syncNotice = MutableStateFlow<String?>(null)

    /** 앱에 담긴 데이터를 쓰던 중에 새로고침이 끝났을 때 한 번 띄울 문구(N3). */
    val syncNotice: StateFlow<String?> = _syncNotice.asStateFlow()

    init {
        viewModelScope.launch {
            var previous: FeedStamp? = null
            DeckRepository.get(app).state.collect { state ->
                val ready = state as? FeedState.Ready ?: return@collect
                val current = FeedStamp(ready.lastSyncedAt, ready.fromBundle, patchLabel(ready.feed.version))
                bundleRefreshNotice(previous, current)?.let { _syncNotice.value = it }
                previous = current
            }
        }
    }

    fun consumeSyncNotice() {
        _syncNotice.value = null
    }
}

// ---------------------------------------------------------------------------
// '게임 위에 띄우기' 시트
// ---------------------------------------------------------------------------

/** 열린 시트. [deckId] 가 null 이면 덱 목록으로, 있으면 그 덱 요약으로 띄운다. */
data class OverlaySheet(val deckId: String?)

/** 띄운 뒤 갈 곳. */
enum class LaunchTarget {
    /** TFT 를 연다. 앱이 뒤로 가면서 오버레이가 곧바로 보인다. */
    Tft,

    /** 앱을 뒤로 보낸다(moveTaskToBack). 홈 화면이나 직전 앱 위에 보인다. */
    Home,
}

data class OverlayLaunch(val deckId: String?, val target: LaunchTarget)

/** 시트에서 띄우기를 눌렀을 때 먼저 할 일. */
enum class LaunchStep { OverlayPermission, NotificationPermission, Start }

/**
 * 권한 화면 → (Android 13+ 에서 아직 없으면) 알림 권한 → 띄우기 순서.
 * 알림은 시트를 연 동안 한 번만 묻는다 — 거부해도 오버레이는 뜨고, 닫은 창을 알림에서 되살릴 수 없을 뿐이다.
 */
internal fun nextLaunchStep(canDrawOverlays: Boolean, notificationsGranted: Boolean, notificationAsked: Boolean): LaunchStep =
    when {
        !canDrawOverlays -> LaunchStep.OverlayPermission
        !notificationsGranted && !notificationAsked -> LaunchStep.NotificationPermission
        else -> LaunchStep.Start
    }

/**
 * 시스템 권한 화면에 다녀오는 동안 기억하는 요청 하나.
 * 돌아오면(ON_RESUME) 권한이 있든 없든 한 번에 소비한다 — 허용하지 않고 돌아왔으면 사용자가 그만둔 것이라,
 * 나중에 다른 길로 권한이 생겼을 때 뜻밖에 켜지지 않게 한다.
 */
internal class PendingRequest<T : Any> {
    private var value: T? = null

    val isPending: Boolean get() = value != null

    fun request(request: T) {
        value = request
    }

    fun cancel() {
        value = null
    }

    /** 권한이 생겼으면 기억한 요청을, 아니면 null. 둘 다 요청은 지운다. */
    fun consume(granted: Boolean): T? {
        val pending = value
        value = null
        return pending?.takeIf { granted }
    }
}

// ---------------------------------------------------------------------------
// 첫 실행 안내
// ---------------------------------------------------------------------------

/**
 * 설치 뒤 첫 실행에 안내를 띄울지. 전적 연결·오버레이 권한 중 빠진 것이 있을 때만 묻는다(사용자 요청 그대로).
 * 전적은 여기서는 저장된 ID 로 본다 — 앱을 켠 직후에는 조회 결과가 아직 없다. 연결 여부 표시는 [profileLink] 가 맡는다.
 */
internal fun firstRunNeeded(savedRiotId: String, overlayGranted: Boolean): Boolean =
    !savedRiotId.contains('#') || !overlayGranted

/** 첫 실행 안내 세 단계의 진행. 셋째(게임 중 자동으로 띄우기)는 선택이라 '완료' 조건에 넣지 않는다. */
internal data class FirstRunProgress(
    val profileLinked: Boolean,
    val overlayGranted: Boolean,
    val autoLaunch: Boolean,
) {
    /** '완료' 를 누를 수 있는지. */
    val requiredDone: Boolean get() = profileLinked && overlayGranted

    /** 닫을 때 '내 정보에서 언제든' 알림이 필요 없는지. */
    val allDone: Boolean get() = requiredDone && autoLaunch
}

/** 전적 연결 상태. 저장된 ID 가 아니라 실제 조회 결과로 정한다 — 틀린 ID 에 '연결됨' 을 띄우지 않는다(N1). */
sealed interface ProfileLink {
    data object NotLinked : ProfileLink

    /** 조회 중. */
    data object Checking : ProfileLink

    /** 이 계정의 요약을 실제로 받았다. [tier] 는 '다이아몬드 IV', 랭크 기록이 없으면 '언랭크'. */
    data class Linked(val riotId: String, val region: String, val tier: String) : ProfileLink {
        /** '다이아몬드 IV · 랄라붕#KR1'. 맞는 계정인지 눈으로 확인하게 한다. */
        val label: String get() = "$tier · $riotId"
    }

    /** 조회에 실패했고 이 계정으로 받은 요약도 없다. [message] 는 '무엇이 · 다음 행동' 한 줄. */
    data class Failed(val message: String) : ProfileLink
}

/**
 * 조회 상태를 연결 상태로 옮긴다.
 * 이전 요약(previous)이 있으면 연결된 것으로 본다: 계정을 바꾸면 저장소가 이전 요약을 먼저 지우므로, 남아 있는 요약은
 * 지금 계정으로 받은 것이다. 그래야 LP 기록을 다시 받는 동안(Loading) '확인하는 중' 으로 깜빡이지 않는다.
 * 저장된 ID 가 있는데 아직 불러오기 전(NotConfigured)이면 조회 중으로 본다.
 */
internal fun profileLink(state: ProfileState, savedRiotId: String): ProfileLink = when (state) {
    ProfileState.NotConfigured -> if (savedRiotId.contains('#')) ProfileLink.Checking else ProfileLink.NotLinked
    is ProfileState.Loading -> state.previous?.let { linkOf(it, savedRiotId) } ?: ProfileLink.Checking
    is ProfileState.Ready -> linkOf(state.profile, savedRiotId)
    is ProfileState.Failed -> state.previous?.let { linkOf(it, savedRiotId) } ?: ProfileLink.Failed(profileErrorText(state.message))
}

private fun linkOf(profile: PlayerProfile, savedRiotId: String): ProfileLink.Linked = ProfileLink.Linked(
    riotId = profile.riotId.ifBlank { savedRiotId },
    region = profile.region.ifBlank { ProfileRepository.DEFAULT_REGION },
    tier = profile.tier.ifBlank { "언랭크" },
)

/**
 * 전적 조회 실패 문구를 '무엇이 · 다음 행동' 한 줄로(N20). 저장소는 영문 예외 원문을 그대로 넘기기도 한다.
 * 저장소 문구가 바뀌어도 모르는 것은 네트워크 안내로 떨어진다.
 */
internal fun profileErrorText(raw: String?): String {
    val text = raw.orEmpty()
    return when {
        "찾지 못" in text -> "소환사를 찾지 못했습니다 · 이름#태그 를 확인해 주세요"
        "랭크 기록" in text -> "이번 세트 랭크 기록이 없습니다 · 랭크 게임을 한 판 한 뒤 다시 시도"
        "형식" in text -> "라이엇 ID 형식이 아닙니다 · 이름#태그 로 적어 주세요"
        text.startsWith("HTTP") -> "전적 서버가 응답하지 않습니다 · 잠시 뒤 다시 시도"
        else -> "전적을 불러오지 못했습니다 · 네트워크 확인 뒤 다시 시도"
    }
}

/** 지역 고르기 목록. 한국 사용자가 주 대상이라 KR·JP·NA 를 앞에 두고, 나머지는 metatft 목록 순서다. */
internal fun regionOptions(): List<String> {
    val head = listOf("KR", "JP", "NA").filter { it in ProfileRepository.REGIONS }
    return head + ProfileRepository.REGIONS.filterNot { it in head }
}

/** 지역 코드의 한국어 이름. 모르는 코드는 그대로. */
internal fun regionLabel(code: String): String = when (code.uppercase()) {
    "KR" -> "한국"
    "JP" -> "일본"
    "NA" -> "북미"
    "EUW" -> "유럽 서부"
    "EUNE" -> "유럽 북동부"
    "BR" -> "브라질"
    "OCE" -> "오세아니아"
    "TR" -> "튀르키예"
    "RU" -> "러시아"
    "LAN" -> "라틴 아메리카 북부"
    "LAS" -> "라틴 아메리카 남부"
    "SG" -> "동남아시아"
    "TW" -> "대만"
    "VN" -> "베트남"
    else -> code
}

// ---------------------------------------------------------------------------
// 덱 데이터 새로고침 표시
// ---------------------------------------------------------------------------

/** 첫 동기화 알림을 판단하는 데 쓰는 피드 상태 요약. */
internal data class FeedStamp(val lastSyncedAt: Long?, val fromBundle: Boolean, val patch: String)

/** 패치 표기 '18.2'. 글로벌 패치가 없는 옛 피드면 중국 패치 번호. */
internal fun patchLabel(version: FeedVersion): String = version.patchGlobal.ifBlank { version.patch }

/**
 * 앱에 담긴 데이터(또는 새로고침한 적 없는 캐시)를 쓰던 중에 새로고침이 끝났으면 알릴 문구, 아니면 null.
 * 성공은 마지막 새로고침 시각이 바뀐 것으로 안다 — 받은 데이터가 동봉본과 같아도(UpToDate) 시각은 바뀐다.
 * 이미 한 번 새로고침한 뒤의 주기 동기화는 조용히 넘긴다.
 */
internal fun bundleRefreshNotice(previous: FeedStamp?, current: FeedStamp): String? {
    if (previous == null) return null
    if (!previous.fromBundle && previous.lastSyncedAt != null) return null
    val synced = current.lastSyncedAt ?: return null
    if (synced == previous.lastSyncedAt) return null
    return if (current.patch.isBlank()) "덱 데이터를 새로고침했습니다" else "덱 데이터를 새로고침했습니다 (패치 ${current.patch})"
}

/** 내 정보 '덱 데이터' 첫 줄. '패치 18.2 · 3시간 전 새로고침', 새로고침한 적이 없으면 '패치 18.2 · 앱에 담긴 데이터'. */
internal fun deckDataLine(patch: String, lastSyncedAt: Long?, now: Long): String {
    val parts = mutableListOf<String>()
    if (patch.isNotBlank()) parts += "패치 $patch"
    parts += if (lastSyncedAt == null) "앱에 담긴 데이터" else "${agoText(now - lastSyncedAt)} 새로고침"
    return parts.joinToString(" · ")
}

/** '방금', '3분 전', '3시간 전', '2일 전'. 시계가 뒤로 가 음수가 되면 '방금'. */
internal fun agoText(elapsedMs: Long): String {
    val minutes = elapsedMs.coerceAtLeast(0L) / 60_000L
    return when {
        minutes < 1 -> "방금"
        minutes < 60 -> "${minutes}분 전"
        minutes < 60 * 24 -> "${minutes / 60}시간 전"
        else -> "${minutes / (60 * 24)}일 전"
    }
}
