package com.tftdeck.reader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import com.tftdeck.reader.ui.theme.FloaColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import android.net.Uri
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * 수집기는 아이콘을 상대 경로로 저장한다. 표시할 때 접두사를 붙인다.
 * 소환물처럼 CommunityDragon 에 없는 아이콘은 절대 URL로 오므로 그대로 쓴다.
 */
fun iconUrl(assetBase: String, path: String?): String? =
    path?.takeIf { it.isNotBlank() }?.let {
        if (it.startsWith("http", ignoreCase = true)) it else assetBase.trimEnd('/') + "/" + it.trimStart('/')
    }

/**
 * 클립보드 복사.
 * Android 13+ 는 시스템이 복사 알림을 띄워 주므로 토스트를 중복해서 띄우지 않는다.
 */
fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "복사했습니다", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 연결한 라이엇 ID 의 lolchess.gg 전적 페이지.
 * "랄라붕#KR1" -> https://lolchess.gg/profile/kr/랄라붕-KR1 (실측으로 확인한 형식)
 */
fun lolchessProfileUrl(riotId: String, region: String): String =
    "https://lolchess.gg/profile/" + region.lowercase() + "/" + riotIdPathSegment(riotId)

/**
 * metatft 선수 페이지. 사이트 라우트가 /player/:server/:playerName 이고
 * 이름의 # 을 - 로 바꿔 넣는다 (metatft 번들에서 확인).
 */
fun metatftProfileUrl(riotId: String, region: String): String =
    "https://www.metatft.com/player/" + region.lowercase() + "/" + riotIdPathSegment(riotId)

/** 경로 조각이라 공백은 + 가 아니라 %20 이어야 한다. URLEncoder 대신 Uri.encode 를 쓴다. */
private fun riotIdPathSegment(riotId: String): String {
    val name = riotId.substringBefore('#').trim()
    val tag = riotId.substringAfter('#', "").trim()
    return Uri.encode(name) + "-" + Uri.encode(tag)
}

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(context, "브라우저를 열 수 없습니다", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 편집 등급(SS~C) 색. 예전에는 팔레트를 한 칸 밀어(편집 S = A 색) 같은 'S' 가 목록과 오버레이에서 다른 색이었다(V5).
 * 이제 [gradeColor] 와 같다. 편집 등급은 색이 아니라 모양(테두리형 + '편')으로 구분한다.
 */
@Deprecated("gradeColor 와 같다. 편집 등급은 GradeBadge(style = Editorial) 로 구분한다", ReplaceWith("gradeColor(tier)"))
fun tierColor(tier: String): Color = gradeColor(tier)

/**
 * 등급 색(MASTER §3 등급 팔레트). 등급 배지에만 쓴다.
 * 편집 등급의 SS 는 S 와 같은 색이다. 등급이 없으면(표본 부족 등) 회색이라 등급이 있는 덱과 한눈에 구분된다.
 */
fun gradeColor(grade: String?): Color = when (grade?.trim()?.uppercase()) {
    "SS", "S" -> FloaColors.TierS
    "A" -> FloaColors.TierA
    "B" -> FloaColors.TierB
    "C" -> FloaColors.TierC
    "D" -> FloaColors.TierD
    else -> FloaColors.NoGrade
}

/** 추세 표시. 평탄하면 아무것도 붙이지 않는다. */
fun trendGlyph(trend: String?): String = when (trend) {
    "up" -> "▲"
    "down" -> "▼"
    else -> ""
}

/** 추세 색. 오르면 초록, 내리면 빨강. */
fun trendColor(trend: String?): Color = when (trend) {
    "up" -> FloaColors.Positive
    "down" -> FloaColors.Negative
    else -> FloaColors.OnSurfaceVariant
}

/** 등수 색. 1등 금색, 톱4 초록, 5·6등 회색, 7·8등 빨강. 분포 막대와 칩이 같은 규칙을 쓴다. */
fun placeColor(place: Int): Color = when (place) {
    1 -> FloaColors.Gold
    2, 3, 4 -> FloaColors.Positive
    5, 6 -> FloaColors.OnSurfaceVariant
    else -> FloaColors.Negative
}

/** 시너지 등급 색. 1=브론즈, 2=실버, 3=골드, 4=프리즘. */
fun traitStyleColor(style: Int): Color = when (style) {
    1 -> Color(0xFFB2703C)
    2 -> Color(0xFFA8B3B8)
    3 -> Color(0xFFE0B348)
    4 -> Color(0xFF8FD9F2)
    else -> Color(0xFF8FA29C)
}

/** 챔피언 코스트 색. 게임 안 상점 색과 같은 순서를 따른다. */
fun costColor(cost: Int?): Color = when (cost) {
    1 -> Color(0xFF9AA8A4)
    2 -> Color(0xFF4FC2A3)
    3 -> Color(0xFF6BA8E5)
    4 -> Color(0xFFC77DE0)
    5 -> Color(0xFFE0B348)
    else -> Color(0xFF6E7C78)
}

/**
 * 구간 키의 한국어 이름. 피드에 label 이 비어 있어도 같은 이름이 나오도록 앱에도 둔다.
 * 'low' 는 브론즈·아이언·실버라(골드 제외) '실버 이하'다(R8).
 */
fun bucketLabel(key: String): String = when (key) {
    "all" -> "전체"
    "master" -> "마스터+"
    "diamond" -> "다이아+"
    "goldem" -> "골드~에메랄드"
    "low" -> "실버 이하"
    else -> key
}

/** 좁은 칸(오버레이 머리줄·구간 드롭다운)에 쓰는 짧은 구간 이름. */
fun bucketShortLabel(key: String): String = when (key) {
    "all" -> "전체"
    "master" -> "마스터+"
    "diamond" -> "다이아+"
    "goldem" -> "골드~에메"
    "low" -> "실버 이하"
    else -> key
}

/** 통계 스코프 키의 한국어 이름. [short] 는 카드 한 줄에 맞춘 줄임말. */
fun scopeLabel(key: String, short: Boolean = false): String = when (key) {
    "glob_plat" -> if (short) "글로벌 플래+" else "글로벌 플래티넘+"
    "kr_plat" -> if (short) "KR 플래+" else "KR 플래티넘+"
    "kr_master" -> "KR 마스터+"
    "cn_plat" -> if (short) "중국 플래+" else "중국 플래티넘+"
    "cn_master" -> "중국 마스터+"
    else -> key
}

// 숫자 표기(MASTER 규칙 7). 화면 코드는 String.format 대신 아래 함수만 쓴다(V11).

/** 평균 등수 "3.61"(소수 2자리, 표의 수치 칸용). 값이 없으면 "-". 기기 언어와 무관하게 소수점은 점으로 쓴다. */
fun formatAvg(value: Double?): String =
    value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.2f", it) } ?: "-"

/** 문장 안의 평균 등수 "4.12등". 값이 없으면 "-". */
fun formatAvgRank(value: Double?): String =
    value?.takeIf { it.isFinite() }?.let { "${formatAvg(it)}등" } ?: "-"

/**
 * 0~1 비율을 "58.5%"로(소수 1자리). 0 보다 크지만 그 자리수로 0 이 되는 값은 "<0.1%" 로 쓴다 —
 * '0.0%' 는 아무도 안 하는 덱으로 읽힌다(L14). 값이 없거나 숫자가 아니면 "-".
 * [digits] 는 예전 호출부를 위해 남겨 둔 것이고, 새 화면은 기본값(1)만 쓴다.
 */
fun formatPct(value: Double?, digits: Int = 1): String {
    val ratio = value?.takeIf { it.isFinite() } ?: return "-"
    val places = digits.coerceAtLeast(0)
    val percent = ratio * 100
    val smallest = 10.0.pow(-places)
    // 1e-9: 0.001 처럼 경계값이 곱셈 오차로 0.0999… 가 되어도 '<' 로 떨어지지 않게 한다.
    if (percent > 0 && percent < smallest - 1e-9) {
        return "<" + String.format(Locale.US, "%.${places}f%%", smallest)
    }
    return String.format(Locale.US, "%.${places}f%%", percent)
}

/** 게임 수 "17,059". 값이 없으면 "-". */
fun formatCount(value: Int?): String =
    value?.let { String.format(Locale.US, "%,d", it) } ?: "-"

/**
 * 판 수: 1만 미만 "9,847판", 1만~100만 미만 "58.5만 판"(소수 1자리 고정), 100만 이상 "108만 판".
 * 만 단위는 반올림한다. 값이 없으면 "-".
 */
fun formatGames(value: Long?): String {
    val games = value ?: return "-"
    if (games < 10_000) return String.format(Locale.US, "%,d판", games)
    // 0.1만(= 1,000판) 단위로 반올림한다. 999,950 처럼 올려서 100만이 되면 아래 줄의 정수 표기로 넘긴다.
    val tenths = (games / 1_000.0).roundToLong()
    if (tenths < 1_000) return String.format(Locale.US, "%d.%d만 판", tenths / 10, tenths % 10)
    return String.format(Locale.US, "%,d만 판", (games / 10_000.0).roundToLong())
}

fun formatGames(value: Int?): String = formatGames(value?.toLong())

/**
 * 변화량 "▲ +0.07" / "▼ −0.13". 빼기는 U+2212 이고, 방향 기호(▲/▼)와 늘 짝을 이룬다 —
 * Positive/Negative 색만으로 좋고 나쁨을 전하지 않게 한다(MASTER 규칙 3). 기호와 수 사이는 줄바꿈 없는 공백(U+00A0).
 * [digits] 자리로 반올림해 0 이면 기호 없이 "0.00". 값이 없으면 "-".
 * 평균 등수처럼 낮을수록 좋은 값도 부호는 뒤집지 않는다. 좋고 나쁨의 색은 호출하는 쪽이 정한다.
 */
fun formatDelta(value: Double?, digits: Int = 2): String {
    val delta = value?.takeIf { it.isFinite() } ?: return "-"
    val body = String.format(Locale.US, "%.${digits.coerceAtLeast(0)}f", abs(delta))
    if (body.toDouble() == 0.0) return body
    return if (delta > 0) "▲ +$body" else "▼ −$body"
}

/** "20260915"·"2026-09-15"·"2026-09-15T13:14:40Z" 를 "9/15"로. 모양을 모르면 원문 그대로. */
fun formatShortDate(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val digits = raw.filter { it.isDigit() }
    if (digits.length < 8) return raw
    val month = digits.substring(4, 6).toIntOrNull() ?: return raw
    val day = digits.substring(6, 8).toIntOrNull() ?: return raw
    if (month !in 1..12 || day !in 1..31) return raw
    return "$month/$day"
}

/** "방금 전", "3시간 전", "2일 전". 마지막 갱신 시각 표시용. */
fun relativeTime(epochMillis: Long?): String {
    if (epochMillis == null) return "아직 갱신 안 됨"
    val diff = System.currentTimeMillis() - epochMillis
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "방금 전"
        minutes < 60 -> "${minutes}분 전"
        minutes < 60 * 24 -> "${minutes / 60}시간 전"
        else -> "${minutes / (60 * 24)}일 전"
    }
}

fun formatDate(epochMillis: Long?): String =
    epochMillis?.let {
        SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(it))
    } ?: "-"
