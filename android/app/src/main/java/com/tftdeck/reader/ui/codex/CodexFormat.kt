package com.tftdeck.reader.ui.codex

import androidx.compose.ui.graphics.Color
import com.tftdeck.reader.ui.theme.FloaColors
import com.tftdeck.reader.data.StatScope
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/*
 * 도감 화면의 숫자·라벨 표기. UiUtils는 다른 패키지 소유라 고칠 수 없어 자체로 둔다.
 * 표기 규칙은 lolchess 용어집을 따른다(평균 등수 / TOP4 / 승률 / 픽률 / 게임 수).
 */

/** 덱 피드가 아직 없을 때 쓰는 CommunityDragon 기본 경로(덱 피드 계약의 assetBase와 같다). */
internal const val DEFAULT_ASSET_BASE = "https://raw.communitydragon.org/latest/game/"

/**
 * 아이콘 URL. 수집기는 CommunityDragon 상대 경로를 쓰고, 소환물 같은 예외만 절대 URL을 싣는다.
 * 문자열 조립 방식은 UiUtils.iconUrl과 같아야 아이콘 팩(로컬 파일 대체)이 같은 키로 찾는다.
 */
internal fun codexIconUrl(assetBase: String, path: String?): String? {
    val trimmed = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    val base = assetBase.ifBlank { DEFAULT_ASSET_BASE }
    return base.trimEnd('/') + "/" + trimmed.trimStart('/')
}

// ---------------------------------------------------------------------------
// 숫자
// ---------------------------------------------------------------------------

/** 평균 등수 "4.37". 값이 없으면 "-". */
internal fun formatAvg(value: Double?): String =
    value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.2f", it) } ?: "-"

/** 비율(0~1)을 "52.5%"로. */
internal fun formatPct(value: Double?, digits: Int = 1): String =
    value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.${digits}f%%", it * 100) } ?: "-"

/** "17,059". */
internal fun formatCount(value: Long?): String =
    value?.let { String.format(Locale.US, "%,d", it) } ?: "-"

internal fun formatCount(value: Int?): String = formatCount(value?.toLong())

/**
 * 표 칸에 들어가는 짧은 수: 9,849 / 13.4만 / 152만 / 1.2억.
 * 반올림하면 표본을 부풀려 보이게 하므로 내림한다.
 */
internal fun formatCountShort(value: Long?): String {
    val v = value ?: return "-"
    return when {
        v < 10_000 -> formatCount(v)
        v < 1_000_000 -> oneDecimal(floor(v / 1_000.0) / 10.0) + "만"
        v < 100_000_000 -> "${v / 10_000}만"
        else -> oneDecimal(floor(v / 10_000_000.0) / 10.0) + "억"
    }
}

internal fun formatCountShort(value: Int?): String = formatCountShort(value?.toLong())

private fun oneDecimal(x: Double): String = String.format(Locale.US, "%.1f", x).removeSuffix(".0")

/**
 * 평균 등수 차이 "+0.07" / "-0.19".
 * 평균 등수는 낮을수록 좋아서 부호를 뒤집지 않는다. 좋고 나쁨은 [deltaColor]로 보여 준다.
 */
internal fun formatDelta(value: Double?, digits: Int = 2): String {
    val v = value?.takeIf { it.isFinite() } ?: return "-"
    val body = String.format(Locale.US, "%.${digits}f", abs(v))
    return when {
        body.toDoubleOrNull() == 0.0 -> body
        v > 0 -> "+$body"
        else -> "-$body"
    }
}

/** 기본 능력치 표기: 30.0 -> "30", 0.75 -> "0.75". */
internal fun formatNumber(value: Double): String {
    if (!value.isFinite()) return "-"
    if (value == floor(value)) return value.toLong().toString()
    return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}

// ---------------------------------------------------------------------------
// 날짜
// ---------------------------------------------------------------------------

/** ISO 시각 문자열을 epoch 밀리초로. 읽지 못하면 null. */
internal fun isoToEpochMillis(iso: String?): Long? {
    val text = iso?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
}

/** "2026-09-15T11:11:58Z" -> "9월 15일"(기기 시간대). 읽지 못하면 날짜 부분이나 원문. */
internal fun formatIsoDay(iso: String): String {
    isoToEpochMillis(iso)?.let { millis ->
        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        return "${date.monthValue}월 ${date.dayOfMonth}일"
    }
    val digits = iso.filter { it.isDigit() }
    if (digits.length >= 8) return "${digits.substring(4, 6).toInt()}월 ${digits.substring(6, 8).toInt()}일"
    return iso
}

/** "2026-09-15" 또는 "20260914" -> "9/15". lol.qq 기준일 표기용. */
internal fun formatShortDate(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.length < 8) return raw
    return "${digits.substring(4, 6).toInt()}/${digits.substring(6, 8).toInt()}"
}

// ---------------------------------------------------------------------------
// 라벨
// ---------------------------------------------------------------------------

/** 칩에 들어가는 짧은 스코프 이름. */
internal fun scopeLabel(key: String): String = when (key) {
    StatScope.GLOB_PLAT -> "글로벌 플래+"
    StatScope.KR_PLAT -> "KR 플래+"
    StatScope.KR_MASTER -> "KR 마스터+"
    StatScope.CN_PLAT -> "중국 플래+"
    StatScope.CN_MASTER -> "중국 마스터+"
    else -> key
}

/** 지역만: 착용자 대체 스코프 표시용. */
internal fun scopeRegionLabel(key: String): String = when {
    key.startsWith("glob") -> "글로벌"
    key.startsWith("kr") -> "KR"
    key.startsWith("cn") -> "중국"
    else -> key
}

internal val RARITY_KEYS = listOf("silver", "gold", "prismatic")
internal val TAG_KEYS = listOf("econ", "items", "combat", "trait", "scaling", "misc")

/**
 * 아이템 종류 칩. 재료(component)는 조합표와 부품 칩으로 보여 주므로 뺀다.
 * 화면은 이번 파일에 실제로 있는 종류만 칩으로 그린다 — 누르면 늘 빈 목록인 칩(시즌 18 의 '지원')을 두지 않도록.
 * other(물약 등)는 다른 칩으로 거를 수 없어 '기타' 칩을 둔다.
 */
internal val ITEM_KIND_FILTERS = listOf("completed", "emblem", "artifact", "radiant", "support", "other")

internal fun rarityLabel(rarity: String?): String = when (rarity?.lowercase()) {
    "silver" -> "실버"
    "gold" -> "골드"
    "prismatic" -> "프리즘"
    null -> ""
    else -> rarity
}

internal fun tagLabel(tag: String): String = when (tag.lowercase()) {
    "econ" -> "경제"
    "items" -> "아이템"
    "combat" -> "전투"
    "trait" -> "특성"
    "scaling" -> "성장"
    "misc" -> "기타"
    else -> tag
}

internal fun kindLabel(kind: String): String = when (kind.lowercase()) {
    "component" -> "재료"
    "completed" -> "일반"
    "emblem" -> "상징"
    "artifact" -> "유물"
    "radiant" -> "찬란"
    "support" -> "지원"
    else -> "기타"
}

/** 특성 등급 이름. 1 브론즈 … 4 프리즘. */
internal fun styleLabel(style: Int): String = when (style) {
    4 -> "프리즘"
    3 -> "골드"
    2 -> "실버"
    1 -> "브론즈"
    else -> ""
}

internal fun traitTypeLabel(type: String?): String = when (type?.lowercase()) {
    "origin" -> "계열"
    "class" -> "직업"
    else -> ""
}

internal fun changedLabel(changed: String): String = when (changed.lowercase()) {
    "buff" -> "버프"
    "nerf" -> "너프"
    "new" -> "신규"
    else -> changed
}

// ---------------------------------------------------------------------------
// 색
// ---------------------------------------------------------------------------

/** 등급 배지 바탕색. 글자는 어두운 색을 얹어 밝은 테마에서도 읽힌다. */
internal fun gradeColor(grade: String?): Color = when (grade?.uppercase()) {
    "SS", "S" -> FloaColors.TierS
    "A" -> FloaColors.TierA
    "B" -> FloaColors.TierB
    "C" -> FloaColors.TierC
    "D" -> FloaColors.TierD
    else -> FloaColors.NoGrade
}

internal fun rarityColor(rarity: String?): Color = when (rarity?.lowercase()) {
    "silver" -> Color(0xFFA8B3B8)
    "gold" -> Color(0xFFE0B348)
    "prismatic" -> Color(0xFFC77DE0)
    else -> Color(0xFF9AA8A4)
}

internal fun changedColor(changed: String): Color = when (changed.lowercase()) {
    "buff" -> BetterColor
    "nerf" -> WorseColor
    else -> FloaColors.Secondary
}

internal val BetterColor = FloaColors.Positive
internal val WorseColor = FloaColors.Negative

/** 평균 등수 차이 색: 낮아지면(좋아지면) 초록, 높아지면 빨강. */
internal fun deltaColor(delta: Double?, neutral: Color): Color = when {
    delta == null || !delta.isFinite() || abs(delta) < 0.005 -> neutral
    delta < 0 -> BetterColor
    else -> WorseColor
}

// ---------------------------------------------------------------------------
// 설명문
// ---------------------------------------------------------------------------

private val BREAK_TAG = Regex("(?i)<br\\s*/?>")
private val ANY_TAG = Regex("<[^>]+>")
private val PLACEHOLDER = Regex("@[^@\\s]+@")
private val SPACES = Regex("[ \\t]{2,}")

/** CommunityDragon 설명에 남은 태그(<br>, <magicDamage>)와 자리표시자를 걷어 낸다. */
internal fun cleanDesc(text: String): String =
    text.replace(BREAK_TAG, "\n")
        .replace(ANY_TAG, "")
        .replace(PLACEHOLDER, "")
        .replace(SPACES, " ")
        .trim()
