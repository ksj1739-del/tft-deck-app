package com.tftdeck.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.ItemRef
import com.tftdeck.reader.data.TraitRef
import com.tftdeck.reader.data.Unit as DeckUnit
import com.tftdeck.reader.ui.costColor
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

/** 보드에 놓인 한 칸. row/col은 lol.qq의 location("행,열")을 그대로 쓴다. */
data class BoardSlot(
    val row: Int,
    val col: Int,
    val name: String,
    val icon: String?,
    val cost: Int?,
    val star: Int,
    val carry: Boolean,
)

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
    Box(
        modifier = modifier
            .aspectRatio(0.88f)
            .clip(HexagonShape)
            .background(if (slot == null) scheme.surfaceVariant else costColor(slot.cost).copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        if (slot == null) return@Box

        AsyncImage(
            model = iconUrl(assetBase, slot.icon),
            contentDescription = slot.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(HexagonShape),
        )
        // 캐리는 테두리로, 3성은 별로 구분한다.
        if (slot.carry) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.88f)
                    .border(2.dp, scheme.primary, HexagonShape)
            )
        }
        if (slot.star >= 3) {
            Text(
                "★★★",
                color = Color(0xFFE0B348),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 배지
// ---------------------------------------------------------------------------

@Composable
fun TierBadge(tier: String, modifier: Modifier = Modifier) {
    val color = tierColor(tier)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(tier, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
            "${trait.count} ${trait.name}",
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

// ---------------------------------------------------------------------------
// 덱 카드
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
                TierBadge(deck.tier)
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
                if (deck.metatft.onlyInChina) {
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
 */
@Composable
fun UnitGrid(
    units: List<DeckUnit>,
    assetBase: String,
    modifier: Modifier = Modifier,
    highlightUnit: String? = null,
) {
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
    val border = if (emphasized) scheme.primary else costColor(unit.cost)

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
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .border(if (emphasized) 2.dp else 1.5.dp, border, RoundedCornerShape(6.dp)),
            )
            if (unit.star >= 3) {
                Text(
                    "★★★",
                    color = StarGold,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .offset(y = (-3).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(scheme.surface)
                        .padding(horizontal = 2.dp),
                )
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

/** TFT 보드가 8칸이라 한 줄 8개가 자연스럽다. 9명 이상이면 다음 줄로 넘어간다. */
private const val PER_ROW = 8
private val ITEM_ROW_HEIGHT = 11.dp
private val StarGold = Color(0xFFE0B348)

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
