package com.tftdeck.reader.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tftdeck.reader.R
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.ui.components.GradeBadge
import com.tftdeck.reader.ui.components.GradeBadgeStyle
import com.tftdeck.reader.ui.components.TextBadge
import com.tftdeck.reader.ui.theme.FloaColors

/**
 * 펼친 패널의 머리줄: `[접기][←(덱 요약일 때만)][제목][⋯]` (WP-O1 — F1·F2·F3·F8).
 *
 * - '접기'는 칩이 있던 자리에 온다. 서비스가 '접기' 가운데를 칩 가운데에 맞춰 창을 놓고(OverlayPlacement.expandedPlacement),
 *   칩이 화면 오른쪽 절반이면 머리줄 좌우를 거울로 뒤집는다([mirrored] → `[⋯][제목][←][접기]`). 머리줄이 패널 위에 오는지
 *   아래에 오는지는 루트(OverlayContent)가 정한다. 그래서 칩을 누른 엄지 그대로 다시 누르면 접히고, 연달아 눌러도 펼쳤다
 *   접힐 뿐이다 — 예전에는 그 자리에 '←'가 와 고른 덱이 풀렸다.
 * - 드문 기능(넓게·티어 카드·코드 복사·구간·앱에서 열기·닫기)은 ⋯ 메뉴(OverlayMenu)로 옮겼다. 버튼은 모두 44×36dp 고정
 *   폭이라 무엇을 켜고 꺼도 버튼이 옆으로 밀리지 않는다(예전에는 '넓게'를 잘못 누르면 그 자리에 '앱 열기'가 와 게임을 떠났다).
 *   두 번 눌러 닫던 닫기 버튼(3초 대기)은 메뉴의 '오버레이 닫기'가 대신한다 — 두 번의 누름이 서로 다른 자리다.
 * - 제목: 목록이면 '덱 12' + 구간 짧은 이름 배지, 덱 요약이면 등급 배지 + 별칭. 머리줄 전체가 끌기 손잡이다.
 *   메뉴가 열려 있으면 제목을 눌러도 메뉴가 닫힌다([onTitleTap]).
 * - 하드웨어 Enter 가 머리줄 버튼을 누르지 않도록 버튼은 포커스를 받지 않는다(Q7).
 */
@Composable
internal fun PanelHeader(
    selected: Deck?,
    bucket: String,
    countText: String,
    bucketName: String?,
    metatftCompared: Boolean,
    mirrored: Boolean,
    menuOpen: Boolean,
    dragModifier: Modifier,
    onCollapse: () -> Unit,
    onBack: () -> Unit,
    onToggleMenu: () -> Unit,
    onTitleTap: (() -> Unit)? = null,
) {
    val collapseLabel = stringResource(R.string.overlay_collapse)
    val backLabel = stringResource(R.string.overlay_back_to_list)
    val menuLabel = stringResource(R.string.overlay_menu)
    Row(
        modifier = dragModifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT_DP.dp)
            .background(OverlayHeader),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!mirrored) {
            HeaderButton(Icons.Default.UnfoldLess, collapseLabel, OverlayText, onClick = onCollapse)
            if (selected != null) HeaderButton(Icons.AutoMirrored.Filled.ArrowBack, backLabel, OverlayText, onClick = onBack)
            HeaderTitle(selected, bucket, countText, bucketName, metatftCompared, alignEnd = false, onTitleTap, Modifier.weight(1f))
            HeaderButton(Icons.Default.MoreHoriz, menuLabel, OverlayMuted, selected = menuOpen, onClick = onToggleMenu)
        } else {
            HeaderButton(Icons.Default.MoreHoriz, menuLabel, OverlayMuted, selected = menuOpen, onClick = onToggleMenu)
            HeaderTitle(selected, bucket, countText, bucketName, metatftCompared, alignEnd = true, onTitleTap, Modifier.weight(1f))
            if (selected != null) HeaderButton(Icons.AutoMirrored.Filled.ArrowBack, backLabel, OverlayText, onClick = onBack)
            HeaderButton(Icons.Default.UnfoldLess, collapseLabel, OverlayText, onClick = onCollapse)
        }
    }
}

/**
 * 머리줄 제목 칸. 버튼을 다 놓고 남는 폭만 쓴다. 거울 모양(오른쪽 칩)에서는 제목을 ←·접기 쪽(오른쪽)에 붙인다.
 * 덱 요약의 별칭은 남는 폭이 [HEADER_ALIAS_MIN_WIDTH] 이상일 때만 둔다 — 한두 글자만 남은 별칭은 읽히지 않는다.
 */
@Composable
private fun HeaderTitle(
    selected: Deck?,
    bucket: String,
    countText: String,
    bucketName: String?,
    metatftCompared: Boolean,
    alignEnd: Boolean,
    onTap: (() -> Unit)?,
    modifier: Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .then(if (onTap != null) Modifier.pointerInput(onTap) { detectTapGestures { onTap() } } else Modifier)
            .padding(horizontal = 4.dp),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (selected == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TITLE_GAP)) {
                Text(countText, style = OverlayChromeType.title, color = OverlayText, maxLines = 1)
                // 구간 이름은 짧게(골드~에메). 바꾸는 곳은 ⋯ 메뉴의 '구간'이다 — 끌기 손잡이 안에서 한 번 눌러 바뀌던 것을 뺐다(F8).
                if (bucketName != null) TextBadge(bucketName)
            }
        } else {
            val grade = selected.gradeFor(bucket).orEmpty()
            val chinaOnly = metatftCompared && selected.isOnlyInChina
            // '중국' 배지는 별칭 자리를 남길 수 있을 때만 단다(좁은 세로 화면 + 티어 카드에서는 테두리 배지 모양만으로 구분).
            val showChina = chinaOnly && maxWidth >= CHINA_BADGE_ROOM
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TITLE_GAP)) {
                GradeBadge(grade, headerBadgeStyle(selected, bucket, chinaOnly))
                if (showChina) TextBadge(stringResource(R.string.overlay_china_badge))
                BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                    if (maxWidth >= HEADER_ALIAS_MIN_WIDTH) {
                        Text(
                            text = selected.displayAlias,
                            style = OverlayChromeType.title,
                            color = OverlayText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 등급 배지 모양(MASTER 규칙 5): 편집 등급 → 테두리 + '편', 중국 한정 → 테두리, 그 밖(metatft) → 채움. */
private fun headerBadgeStyle(deck: Deck, bucket: String, chinaOnly: Boolean): GradeBadgeStyle = when {
    deck.showsEditorialGrade(bucket) -> GradeBadgeStyle.Editorial
    chinaOnly -> GradeBadgeStyle.Outlined
    else -> GradeBadgeStyle.Filled
}

/**
 * 머리줄 버튼 44×36dp(MASTER 규칙 9), 아이콘 20dp. [selected] 면(메뉴가 열림) 선택 표시 한 모양(규칙 6)으로 채운다.
 * 하드웨어 Enter 로 눌리지 않게 포커스를 받지 않는다(Q7).
 */
@Composable
private fun HeaderButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = HEADER_BUTTON_WIDTH_DP.dp, height = HEADER_HEIGHT_DP.dp)
            .focusProperties { canFocus = false }
            .clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.background(FloaColors.SecondaryContainer) else Modifier)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) FloaColors.OnSecondaryContainer else tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 머리줄·칩·메뉴 글자(WP-O1). 오버레이 글자 크기 객체(OverlayTheme 의 OverlayType, WP-O4)와 같은 값이다 — 두 묶음을 합친 뒤
 * 용어 정리 단계(WP-T)가 OverlayType 으로 바꾼다. 여섯 단계(MASTER 규칙 1) 안의 12·11sp 만 쓴다.
 */
internal object OverlayChromeType {
    /** 제목·칩 글자: 12sp Bold, 줄 높이 15. */
    val title = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

    /** 메뉴 항목: 12sp, 줄 높이 16. */
    val item = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
}

/**
 * 덱 요약 머리줄에 별칭을 둘 최소 폭 — 버튼을 다 놓고 남는 폭이 이보다 좁으면(세로 화면 + 티어 카드) 별칭이
 * 한두 글자만 남아 두지 않는다. 가로 화면(게임 중, 패널 300dp 이상)에서는 남는다.
 */
private val HEADER_ALIAS_MIN_WIDTH = 56.dp

/** 제목 칸이 이만큼 넓어야 '중국' 배지를 단다: 등급 배지 20 + '중국' 약 34 + 간격 + 별칭 최소 폭. */
private val CHINA_BADGE_ROOM = 124.dp

/** 제목 안 조각 사이 간격. */
private val TITLE_GAP = 6.dp
