package com.tftdeck.reader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import android.net.Uri

/** 수집기는 아이콘을 상대 경로로 저장한다. 표시할 때 접두사를 붙인다. */
fun iconUrl(assetBase: String, path: String?): String? =
    path?.takeIf { it.isNotBlank() }?.let { assetBase.trimEnd('/') + "/" + it.trimStart('/') }

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

/** 티어 색. SS가 가장 강하고 아래로 갈수록 차분해진다. */
fun tierColor(tier: String): Color = when (tier.uppercase()) {
    "SS" -> Color(0xFFFF6B8A)
    "S" -> Color(0xFFFFA94D)
    "A" -> Color(0xFF4FC2A3)
    "B" -> Color(0xFF6BA8E5)
    else -> Color(0xFF9AA8A4)
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
