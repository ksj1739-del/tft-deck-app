package com.tftdeck.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.tftdeck.reader.ui.theme.FloaColors
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.ItemRef
import com.tftdeck.reader.data.TraitRef
import com.tftdeck.reader.data.Unit as DeckUnit
import com.tftdeck.reader.ui.costColor
import com.tftdeck.reader.ui.gradeColor
import com.tftdeck.reader.ui.iconUrl
import com.tftdeck.reader.ui.traitStyleColor

// ---------------------------------------------------------------------------
// 헥사곤 보드
// ---------------------------------------------------------------------------

/** 꼭짓점이 위아래를 향하는 육각형. TFT 보드 칸 모양. */
object HexagonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(size.width * 0.5f, 0f)
            lineTo(size.width, size.height * 0.25f)
            lineTo(size.width, size.height * 0.75f)
            lineTo(size.width * 0.5f, size.height)
            lineTo(0f, size.height * 0.75f)
            lineTo(0f, size.height * 0.25f)
            close()
        }
        return Outline.Generic(path)
    }
}

private const val BOARD_ROWS = 4
private const val BOARD_COLS = 7

/**
 * 보드에 놓인 한 칸. row/col은 lol.qq의 location("행,열")을 그대로 쓴다.
 *
 * [heat]·[heatLabel]·[diverges]는 '실측 배치'를 켰을 때만 채운다:
 * heat 는 칸 배경 진하기(0~1), heatLabel 은 사용률 글자, diverges 는 작가 좌표와 실측 최빈 칸이 다르다는 표시.
 */
data class BoardSlot(
    val row: Int,
    val col: Int,
    val name: String,
    val icon: String?,
    val cost: Int?,
    val star: Int,
    val carry: Boolean,
    val kind: String? = null,
    val heat: Float? = null,
    val heatLabel: String? = null,
    val diverges: Boolean = false,
) {
    val isPet: Boolean get() = kind == DeckKeys.KIND_PET
}

/**
 * 4행 7열 배치를 실제 모양대로 그린다.
 * 짝수 행을 반 칸 밀어 벌집 배열을 만든다.
 */
@Composable
fun HexBoard(
    slots: List<BoardSlot>,
    assetBase: String,
    modifier: Modifier = Modifier,
) {
    val byPosition = slots.associateBy { it.row to it.col }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (row in 1..BOARD_ROWS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (row % 2 == 0) 18.dp else 0.dp, end = if (row % 2 == 0) 0.dp else 18.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (col in 1..BOARD_COLS) {
                    HexCell(
                        slot = byPosition[row to col],
                        assetBase = assetBase,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HexCell(slot: BoardSlot?, assetBase: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val heat = slot?.heat
    val background = when {
        slot == null -> scheme.surfaceVariant
        heat != null -> scheme.primary.copy(alpha = 0.18f + 0.62f * heat.coerceIn(0f, 1f))
        slot.isPet -> scheme.outline.copy(alpha = 0.35f)
        else -> costColor(slot.cost).copy(alpha = 0.25f)
    }
    Box(
        modifier = modifier
            .aspectRatio(0.88f)
            .clip(HexagonShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (slot == null) return@Box

        // 탭을 바꾸거나 처음 열 때 이미지가 오기 전에도 칸이 비어 보이지 않도록 코스트색을 먼저 깐다.
        val placeholder = remember(slot.cost) { ColorPainter(costColor(slot.cost).copy(alpha = 0.25f)) }
        AsyncImage(
            model = iconUrl(assetBase, slot.icon),
            contentDescription = slot.name,
            placeholder = placeholder,
            error = placeholder,
            modifier = Modifier
                // 실측 배치에서는 얼굴을 조금 줄여 칸 배경(사용률 진하기)이 테두리처럼 보이게 한다.
                .padding(if (heat != null) 4.dp else 0.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(HexagonShape),
        )
        // 아이콘을 끝내 못 구한 칸(새 소환물 등)은 이름 첫 글자라도 그린다. 회색 칸만 남으면 무엇인지 알 수 없다.
        if (slot.icon.isNullOrBlank()) {
            InitialMark(slot.name, fontSize = 12.sp)
        }
        // 소환물은 회색 테두리, 캐리는 밝은 테두리, 3성은 별로 구분한다. 소환물에는 별이 없다.
        // 캐리를 파랑(primary)으로 칠하지 않는다: 파랑은 누를 수 있음·선택됨이고 3코스트 색과도 겹친다(V10).
        if (slot.isPet) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.88f)
                    .border(1.5.dp, scheme.outline, HexagonShape)
            )
        } else if (slot.carry) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.88f)
                    .border(CARRY_BORDER, CarryColor, HexagonShape)
            )
        }
        if (!slot.isPet && slot.star >= 3) {
            ThreeStarMark(9.sp, Modifier.align(Alignment.TopCenter))
        }
        slot.heatLabel?.let { label ->
            Text(
                label,
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = Shadow(Color(0xE6000000), Offset(0f, 1f), 2.5f)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
            )
        }
        if (slot.diverges) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(scheme.secondary)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 배지
// ---------------------------------------------------------------------------

/**
 * 등급 배지 모양(MASTER 규칙 5). 두 등급 체계를 색이 아니라 모양으로 가른다(R2·L1·V5).
 * - [Filled]: 등급색 채움 + 어두운 글자. metatft 통계 등급(주 목록).
 * - [Outlined]: 1.5dp 등급색 테두리 + 등급색 글자, 채움 없음. 중국 한정 덱(lol.qq 등급).
 * - [Editorial]: [Outlined] 오른쪽에 '편' 글자 배지. 편집 등급.
 */
enum class GradeBadgeStyle { Filled, Outlined, Editorial }

/**
 * 등급 배지 한 모양: 높이 20dp, 최소 폭 20dp, 모서리 6dp, 좌우 6dp, 글자 11sp Bold.
 * 앱 테마 밖(오버레이 창)에서도 같은 색이 나오도록 색은 [FloaColors] 에서 읽는다. 큰 글자 설정에서는 높이가 늘어난다.
 */
@Composable
fun GradeBadge(grade: String, style: GradeBadgeStyle = GradeBadgeStyle.Filled, modifier: Modifier = Modifier) {
    val label = grade.trim().uppercase().ifEmpty { "–" }
    val color = gradeColor(label)
    when (style) {
        GradeBadgeStyle.Filled -> GradeBox(label, color, filled = true, description = "등급 $label", modifier = modifier)
        GradeBadgeStyle.Outlined -> GradeBox(label, color, filled = false, description = "중국 한정 등급 $label", modifier = modifier)
        GradeBadgeStyle.Editorial -> Row(
            modifier.clearAndSetSemantics { contentDescription = "편집 등급 $label" },
            horizontalArrangement = Arrangement.spacedBy(BADGE_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradeBox(label, color, filled = false, description = null)
            TextBadge("편")
        }
    }
}

@Composable
private fun GradeBox(label: String, color: Color, filled: Boolean, description: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(BADGE_CORNER)
    Box(
        modifier
            .heightIn(min = BADGE_HEIGHT)
            .widthIn(min = BADGE_HEIGHT)
            .clip(shape)
            .then(if (filled) Modifier.background(color) else Modifier.border(OUTLINED_BADGE_BORDER, color, shape))
            .padding(horizontal = BADGE_PADDING)
            .then(if (description != null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (filled) FloaColors.Background else color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * 등급이 아닌 글자 배지 한 모양('중국'·'표본 적음'·'편'·구간 이름): 높이 20dp, 모서리 6dp, 좌우 6dp, 11sp Medium.
 * 채움은 surfaceBright — surfaceVariant 는 카드 면(surfaceContainerHighest) 위에서 1.04:1 로 사라진다(MASTER 규칙 5 주석).
 * [emphasis] 면 글자를 primary 로('편' 처럼 한 번 더 눈이 가야 하는 것). 오버레이는 [textStyle] 로 10sp 를 줄 수 있다.
 */
@Composable
fun TextBadge(
    text: String,
    emphasis: Boolean = false,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall,
) {
    val shape = RoundedCornerShape(BADGE_CORNER)
    Box(
        modifier
            .heightIn(min = BADGE_HEIGHT)
            .widthIn(min = BADGE_HEIGHT)
            .clip(shape)
            .background(FloaColors.SurfaceBright)
            .padding(horizontal = BADGE_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (emphasis) FloaColors.Primary else FloaColors.OnSurfaceVariant,
            style = textStyle,
            maxLines = 1,
        )
    }
}

/**
 * 예전 등급 배지. [GradeBadge] 로 위임한다: 통계 등급 → Filled, 편집 등급([editorial]) → Editorial,
 * 등급이 없으면 '표본 부족' 글자 배지. 중국 한정 덱의 Outlined 는 이 함수가 모르므로 호출부가 GradeBadge 로 바꿀 때 고른다.
 * [global](옛 metatft 전용 덱)은 metatft 등급이라 Filled 로 그린다.
 */
@Deprecated(
    "GradeBadge 를 쓴다(중국 한정 덱은 GradeBadgeStyle.Outlined)",
    ReplaceWith("GradeBadge(grade.orEmpty(), GradeBadgeStyle.Filled, modifier)"),
)
@Composable
fun TierBadge(grade: String?, modifier: Modifier = Modifier, editorial: Boolean = false, global: Boolean = false) {
    if (grade.isNullOrBlank()) {
        TextBadge("표본 부족", modifier = modifier)
        return
    }
    val style = if (editorial && !global) GradeBadgeStyle.Editorial else GradeBadgeStyle.Filled
    GradeBadge(grade, style, modifier)
}

/** 예전 '중국 한정' 배지. 글자 배지 한 모양의 '중국'으로 그린다(MASTER 규칙 5). */
@Deprecated("TextBadge(\"중국\") 를 쓴다", ReplaceWith("TextBadge(\"중국\", modifier = modifier)"))
@Composable
fun OnlyInChinaBadge(modifier: Modifier = Modifier) {
    TextBadge("중국", modifier = modifier)
}

// ---------------------------------------------------------------------------
// 제목·칩
// ---------------------------------------------------------------------------

/**
 * 섹션 제목(MASTER 규칙 3·9, D16·V8): titleSmall(14sp SemiBold) onSurface, 위 24dp 아래 8dp.
 * 제목은 파랑(primary)으로 칠하지 않는다 — 파랑은 누를 수 있음·선택됨이다. [trailing] 은 오른쪽 끝(ⓘ·'더 보기' 등).
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = SECTION_TITLE_TOP, bottom = SECTION_TITLE_BOTTOM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = FloaColors.OnSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * 선택 칩 한 모양(MASTER 규칙 6): 높이 32dp(누름 영역은 48dp 까지 넓힌다), 모서리 8dp, 테두리 없음.
 * 미선택 = surfaceVariant 채움 + onSurfaceVariant 글자, 선택 = secondaryContainer 채움 + onSecondaryContainer 글자.
 * 취소선·체크 아이콘·굵기 변화로 선택을 나타내지 않는다. [leading] 은 LocalContentColor 로 글자색을 받는다.
 */
@Composable
fun FloaFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(CHIP_CORNER)
    val container = if (selected) FloaColors.SecondaryContainer else FloaColors.SurfaceVariant
    val content = if (selected) FloaColors.OnSecondaryContainer else FloaColors.OnSurfaceVariant
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .height(CHIP_HEIGHT)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(container)
            .selectable(selected = selected, enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            CompositionLocalProvider(LocalContentColor provides content) { leading() }
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

/**
 * 특성 칩: surfaceVariant 채움 + onSurface 12sp 글자, 앞에 특성 아이콘 16dp(V18·R13).
 * 칩 채움과 글자에서 단계 색(브론즈·실버·골드·프리즘)을 뺐다 — 칩 위 브론즈 글자는 4.1:1 이었고 실버 칩은 운영 칩과 구분되지 않았다.
 * 단계 색은 아이콘 칸에만 남긴다(게임처럼 단계색 바탕에 어두운 실루엣). 아이콘 경로가 없으면 자리를 두지 않는다.
 */
@Composable
fun TraitChip(trait: TraitRef, assetBase: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(CHIP_CORNER))
            .background(FloaColors.SurfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        iconUrl(assetBase, trait.icon)?.let { url -> TraitIcon(url, trait.style) }
        Text(
            if (trait.count > 0) "${trait.count} ${trait.name}" else trait.name,
            color = FloaColors.OnSurface,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

/** 특성 아이콘 16dp. 단계가 있으면 단계색 바탕에 어두운 실루엣, 없으면 흐린 바탕에 밝은 실루엣. */
@Composable
private fun TraitIcon(url: String, style: Int) {
    val styled = style in 1..4
    Box(
        Modifier
            .size(TRAIT_ICON)
            .clip(RoundedCornerShape(4.dp))
            .background(if (styled) traitStyleColor(style) else FloaColors.OutlineVariant),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (styled) FloaColors.Background else FloaColors.OnSurface),
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
        )
    }
}

/**
 * 아이템 아이콘 줄. 자리마다 1dp outlineVariant 빈 상자를 먼저 깔고 이미지가 오면 덮는다 —
 * 이미지가 오기 전·실패 때 아이템 칸이 투명하게 비어 보이지 않게(L12·V16).
 * [spacing] 은 아이콘 사이 간격(12dp 3개를 40dp 얼굴 폭에 맞추려면 2dp).
 */
@Composable
fun ItemIcons(items: List<ItemRef>, assetBase: String, size: Int = 20, spacing: Dp = 3.dp) {
    val shape = RoundedCornerShape(4.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
        items.forEach { item ->
            Box(Modifier.size(size.dp)) {
                Box(
                    Modifier
                        .matchParentSize()
                        .border(1.dp, FloaColors.OutlineVariant, shape)
                )
                AsyncImage(
                    model = iconUrl(assetBase, item.icon),
                    contentDescription = item.name,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape),
                )
            }
        }
    }
}

/**
 * 챔피언 초상 하나. 카드의 캐리·얼굴 줄, 빌드업 행, 변형 행이 같은 모양을 쓴다.
 *
 * 자리 표시(L12·V16): 코스트색 바탕에 이름 첫 글자를 늘 먼저 깔고, 이미지가 오면 그 위를 덮는다 —
 * 첫 디코드가 늦거나 실패해도 빈 색 상자로 남지 않는다. 투명한 아이콘(소환물)에 글자가 비치지 않게 이미지가 온 뒤에는 글자를 거둔다.
 *
 * 캐리는 2dp 밝은(onSurface) 테두리. 파랑은 누를 수 있음·선택됨이고 3코스트 테두리와 겹친다(V10).
 * 소환물은 회색 테두리에 별을 달지 않는다. [newMark] 는 이전 레벨에 없던 유닛을 알리는 왼쪽 위 6dp Positive 점
 * (예전 파랑 '+' 원은 '추가' 버튼처럼 보였다, V21).
 */
@Composable
fun UnitPortrait(
    icon: String?,
    name: String,
    cost: Int?,
    assetBase: String,
    size: Dp,
    modifier: Modifier = Modifier,
    star: Int = 1,
    carry: Boolean = false,
    pet: Boolean = false,
    showStar: Boolean = true,
    newMark: Boolean = false,
) {
    val large = size >= 36.dp
    val shape = RoundedCornerShape(if (large) 6.dp else 4.dp)
    val borderColor = when {
        pet -> FloaColors.Outline
        carry -> CarryColor
        else -> costColor(cost)
    }
    val url = iconUrl(assetBase, icon)
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier.size(size), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(costColor(cost).copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!loaded) {
                InitialMark(
                    name,
                    fontSize = if (large) MaterialTheme.typography.titleSmall.fontSize else MaterialTheme.typography.labelSmall.fontSize,
                )
            }
        }
        AsyncImage(
            model = url,
            contentDescription = name,
            onState = { state -> loaded = state is AsyncImagePainter.State.Success },
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .border(if (carry && !pet) CARRY_BORDER else 1.dp, borderColor, shape),
        )
        if (showStar && !pet && star >= 3) {
            ThreeStarMark(if (large) 8.sp else 6.sp, Modifier.offset(y = (-3).dp))
        }
        if (newMark) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-2).dp, y = (-2).dp)
                    .size(NEW_MARK + 2.dp)
                    .clip(CircleShape)
                    .background(FloaColors.Background)
                    .padding(1.dp)
                    .clip(CircleShape)
                    .background(FloaColors.Positive)
                    .clearAndSetSemantics { contentDescription = "새로 들어옴" },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 덱 카드(이전 모양). 새 목록은 DeckCardV2 를 쓴다.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeckCard(
    deck: Deck,
    assetBase: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlightUnit: String? = null,
) {
    val scheme = MaterialTheme.colorScheme

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                TierBadge(deck.tier, editorial = true)
                Spacer(Modifier.width(7.dp))
                Text(
                    deck.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (deck.isOnlyInChina) {
                    OnlyInChinaBadge()
                    Spacer(Modifier.width(6.dp))
                }
                deck.finalLevel?.let {
                    Text(
                        "${it}레벨",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                deck.traits.take(4).forEach { TraitChip(it, assetBase) }
            }

            // 덱 전체 구성. 캐리 몇 명만 보여 주면 이 덱이 뭘로 굴러가는지 알 수 없다.
            UnitGrid(deck.units, assetBase, highlightUnit = highlightUnit)
        }
    }
}

/**
 * 덱의 모든 챔피언을 코스트 테두리, 별, 아이템과 함께 보여 준다.
 *
 * 한 줄에 정확히 [PER_ROW]개씩 넣고 남는 칸은 빈 자리로 채운다.
 * 폭을 무게로 나누기 때문에 화면 크기와 무관하게 열이 맞고,
 * 아이템이 없는 유닛도 아이템 줄 높이를 차지해 이름 줄이 어긋나지 않는다.
 *
 * [compact] 면 이름·아이템 없이 26dp 얼굴만 폭에 맞춰 줄바꿈한다(카드의 '나머지 유닛' 줄).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnitGrid(
    units: List<DeckUnit>,
    assetBase: String,
    modifier: Modifier = Modifier,
    highlightUnit: String? = null,
    compact: Boolean = false,
) {
    if (compact) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            units.forEach { unit ->
                UnitPortrait(
                    icon = unit.icon,
                    name = unit.name,
                    cost = unit.cost,
                    assetBase = assetBase,
                    size = COMPACT_FACE,
                    star = unit.star,
                    carry = unit.carry || unit.name == highlightUnit,
                    pet = unit.isPet,
                )
            }
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        units.chunked(PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { unit ->
                    UnitCell(
                        unit = unit,
                        assetBase = assetBase,
                        highlight = unit.name == highlightUnit,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 마지막 줄이 덜 찼을 때 남은 칸을 비워 두어야 열 폭이 유지된다.
                repeat(PER_ROW - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun UnitCell(
    unit: DeckUnit,
    assetBase: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val emphasized = unit.carry || highlight
    // 검색으로 고른 유닛(선택됨)만 파랑, 캐리는 밝은 테두리(V10).
    val border = when {
        unit.isPet -> scheme.outline
        highlight -> scheme.primary
        unit.carry -> CarryColor
        else -> costColor(unit.cost)
    }
    val placeholder = remember(unit.cost) { ColorPainter(costColor(unit.cost).copy(alpha = 0.25f)) }

    Column(
        modifier = modifier
            .then(
                if (highlight) {
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(scheme.primaryContainer)
                } else Modifier
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            AsyncImage(
                model = iconUrl(assetBase, unit.icon),
                contentDescription = unit.name,
                placeholder = placeholder,
                error = placeholder,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .border(if (emphasized) CARRY_BORDER else 1.5.dp, border, RoundedCornerShape(6.dp)),
            )
            if (unit.icon.isNullOrBlank()) {
                InitialMark(unit.name, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
            }
            if (!unit.isPet && unit.star >= 3) {
                ThreeStarMark(7.sp, Modifier.offset(y = (-3).dp))
            }
        }

        // 아이템은 챔피언 바로 아래에 붙여야 누가 뭘 드는지 한눈에 보인다.
        // 아이템이 없어도 높이는 차지해서 아래 이름 줄이 나란히 서게 한다.
        Row(
            modifier = Modifier.height(ITEM_ROW_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            unit.items.take(3).forEach { item ->
                AsyncImage(
                    model = iconUrl(assetBase, item.icon),
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(ITEM_ROW_HEIGHT)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }

        Text(
            unit.name,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            color = if (unit.carry) scheme.onSurface else scheme.onSurfaceVariant,
            fontWeight = if (unit.carry) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 3성 표시. 초상화 위에 얹히므로 바탕 상자를 깔지 않고 그림자로만 읽히게 한다.
 * 상자가 있으면 얼굴 윗부분을 가리고 흰 띠처럼 튄다.
 */
@Composable
fun ThreeStarMark(fontSize: TextUnit, modifier: Modifier = Modifier) {
    Text(
        "★★★",
        color = StarGold,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        style = TextStyle(
            shadow = Shadow(color = Color(0xE6000000), offset = Offset(0f, 1f), blurRadius = 2.5f),
        ),
        modifier = modifier,
    )
}

/**
 * 아이콘이 없는 유닛 자리에 그리는 이름 첫 글자. 수집기가 아이콘을 못 구한 새 소환물 같은 칸이
 * 무엇인지 알 수 없는 회색 칸으로만 남지 않게 한다.
 */
@Composable
internal fun InitialMark(name: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val initial = name.trim().firstOrNull()?.toString() ?: return
    Text(
        initial,
        color = FloaColors.OnSurface,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier,
    )
}

/** 편집 등급 배지 곁에 붙이는 '표본 부족' 글자. 통계 등급이 없는 이유를 알린다. 9sp 는 규칙 1 밖이라 11sp(labelSmall). */
@Composable
fun LowSampleNote(modifier: Modifier = Modifier) {
    Text("표본 부족", color = gradeColor(null), style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = modifier)
}

/** TFT 보드가 8칸이라 한 줄 8개가 자연스럽다. 9명 이상이면 다음 줄로 넘어간다. */
private const val PER_ROW = 8
private val ITEM_ROW_HEIGHT = 11.dp
private val COMPACT_FACE = 26.dp
private val StarGold = FloaColors.Gold

/** 캐리 테두리: 2dp 밝은 테두리(파랑 아님, V10). */
private val CarryColor = FloaColors.OnSurface
private val CARRY_BORDER = 2.dp
private val NEW_MARK = 6.dp

// 배지 한 모양(MASTER 규칙 5)
private val BADGE_HEIGHT = 20.dp
private val BADGE_CORNER = 6.dp
private val BADGE_PADDING = 6.dp
private val BADGE_GAP = 4.dp
private val OUTLINED_BADGE_BORDER = 1.5.dp

// 칩(MASTER 규칙 4·6)
private val CHIP_HEIGHT = 32.dp
private val CHIP_CORNER = 8.dp
private val TRAIT_ICON = 16.dp
private const val DISABLED_ALPHA = 0.38f

// 섹션 제목 여백(MASTER 규칙 9)
private val SECTION_TITLE_TOP = 24.dp
private val SECTION_TITLE_BOTTOM = 8.dp

/** 목록이 비었을 때 무엇을 해야 하는지 알려 준다. */
@Composable
fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 화면 좌우 여백 16dp(MASTER 규칙 9). 간격은 4 · 8 · 12 · 16 · 24 만 쓴다. */
val ScreenPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
