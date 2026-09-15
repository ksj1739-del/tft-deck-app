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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.tftdeck.reader.ui.theme.FloaColors
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
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
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.ItemRef
import com.tftdeck.reader.data.TraitRef
import com.tftdeck.reader.data.Unit as DeckUnit
import com.tftdeck.reader.ui.costColor
import com.tftdeck.reader.ui.gradeColor
import com.tftdeck.reader.ui.iconUrl
import com.tftdeck.reader.ui.tierColor
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
        // 소환물은 회색 테두리, 캐리는 강조 테두리, 3성은 별로 구분한다. 소환물에는 별이 없다.
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
                    .border(2.dp, scheme.primary, HexagonShape)
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
 * 등급 배지.
 * 통계 등급(S~D)은 채운 배지, 편집 등급(SS~C, [editorial])은 테두리 배지로 모양을 달리한다 —
 * 두 체계가 같은 모양이면 편집자 의견이 통계처럼 읽힌다.
 * 등급이 없으면(표본 부족) 빈칸 대신 이유를 쓴다.
 */
@Composable
fun TierBadge(grade: String?, modifier: Modifier = Modifier, editorial: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    if (grade.isNullOrBlank()) {
        val color = gradeColor(null)
        Box(
            modifier = modifier
                .clip(shape)
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Text("표본 부족", color = color, style = MaterialTheme.typography.labelSmall)
        }
        return
    }
    val color = if (editorial) tierColor(grade) else gradeColor(grade)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (editorial) Color.Transparent else color.copy(alpha = 0.16f))
            .border(if (editorial) 1.5.dp else 1.dp, color.copy(alpha = if (editorial) 0.9f else 0.5f), shape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(grade, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/** metatft에 없는 덱임을 알리는 배지. 앱에서 호박색은 이 의미로만 쓴다. */
@Composable
fun OnlyInChinaBadge(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(scheme.secondaryContainer)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            "중국 한정",
            color = scheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun TraitChip(trait: TraitRef, assetBase: String) {
    val color = traitStyleColor(trait.style)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        trait.icon?.let {
            AsyncImage(
                model = iconUrl(assetBase, it),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            if (trait.count > 0) "${trait.count} ${trait.name}" else trait.name,
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun ItemIcons(items: List<ItemRef>, assetBase: String, size: Int = 20) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEach { item ->
            AsyncImage(
                model = iconUrl(assetBase, item.icon),
                contentDescription = item.name,
                modifier = Modifier
                    .size(size.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

/**
 * 챔피언 초상 하나. 카드의 캐리·얼굴 줄, 빌드업 행, 변형 행이 같은 모양을 쓴다.
 *
 * 소환물은 회색 테두리에 별을 달지 않는다. [newMark] 는 이전 레벨에 없던 유닛을 알리는 작은 '+' 점.
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
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (size >= 36.dp) 6.dp else 4.dp)
    val borderColor = when {
        pet -> scheme.outline
        carry -> scheme.primary
        else -> costColor(cost)
    }
    val placeholder = remember(cost) { ColorPainter(costColor(cost).copy(alpha = 0.25f)) }
    Box(modifier.size(size), contentAlignment = Alignment.TopCenter) {
        AsyncImage(
            model = iconUrl(assetBase, icon),
            contentDescription = name,
            placeholder = placeholder,
            error = placeholder,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .border(if (carry && !pet) 2.dp else 1.dp, borderColor, shape),
        )
        if (icon.isNullOrBlank()) {
            InitialMark(name, fontSize = if (size >= 36.dp) 13.sp else 10.sp, modifier = Modifier.align(Alignment.Center))
        }
        if (showStar && !pet && star >= 3) {
            ThreeStarMark(if (size >= 36.dp) 8.sp else 6.sp, Modifier.offset(y = (-3).dp))
        }
        if (newMark) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = scheme.onPrimary, fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold)
            }
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
    val border = when {
        unit.isPet -> scheme.outline
        emphasized -> scheme.primary
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
                    .border(if (emphasized) 2.dp else 1.5.dp, border, RoundedCornerShape(6.dp)),
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
            color = if (unit.carry) scheme.primary else scheme.onSurfaceVariant,
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
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier,
    )
}

/** 편집 등급 배지 곁에 붙이는 '표본 부족' 작은 글자. 통계 등급이 없는 이유를 알린다. */
@Composable
fun LowSampleNote(modifier: Modifier = Modifier) {
    Text("표본 부족", color = gradeColor(null), fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1, modifier = modifier)
}

/** TFT 보드가 8칸이라 한 줄 8개가 자연스럽다. 9명 이상이면 다음 줄로 넘어간다. */
private const val PER_ROW = 8
private val ITEM_ROW_HEIGHT = 11.dp
private val COMPACT_FACE = 26.dp
private val StarGold = FloaColors.Gold

/** 목록이 비었을 때 무엇을 해야 하는지 알려 준다. */
@Composable
fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

val ScreenPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
