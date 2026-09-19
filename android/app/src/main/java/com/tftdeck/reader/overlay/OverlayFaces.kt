package com.tftdeck.reader.overlay

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.BuildupPick
import com.tftdeck.reader.data.CatalogIndex
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.Unit as DeckUnit
import com.tftdeck.reader.ui.components.ThreeStarMark
import com.tftdeck.reader.ui.iconUrl

/**
 * 빌드업 구성의 얼굴 줄. 빌드업에는 유닛 id 만 있어서, 같은 id 가 최종 보드에 있으면
 * 넓게 볼 때 그 유닛의 아이템을 빌려 와 누가 무엇을 들게 되는지 함께 보여 준다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BuildFaces(pick: BuildupPick, deck: Deck, catalog: CatalogIndex?, assetBase: String, wide: Boolean) {
    val finalById = remember(deck) { deck.units.associateBy { it.id } }
    val carryId = pick.option?.carryId ?: deck.carryId
    // 작가 단계에는 칸마다 성급·캐리 표시가 있다. 통계 옵션에는 없어서 그때는 캐리 id 만 본다.
    val placements = remember(pick) { pick.stage?.units.orEmpty().associateBy { it.id } }
    if (wide) {
        pick.units.forEach { id ->
            val entry = catalog?.unit(id)
            val unit = finalById[id]
            val name = entry?.name ?: unit?.name ?: id
            val carry = placements[id]?.carry ?: (id == carryId)
            Row(verticalAlignment = Alignment.CenterVertically) {
                BuildFace(
                    icon = entry?.icon ?: unit?.icon,
                    name = name,
                    cost = entry?.cost ?: unit?.cost,
                    assetBase = assetBase,
                    size = 26.dp,
                    carry = carry,
                    pet = catalog?.isPet(id) == true || unit?.isPet == true,
                    star = placements[id]?.star ?: 1,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = name,
                    color = if (carry) OverlayAccent else OverlayText,
                    fontSize = 11.sp,
                    fontWeight = if (carry) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                unit?.items?.takeIf { it.isNotEmpty() }?.let { items ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        items.forEach { item ->
                            AsyncImage(
                                model = iconUrl(assetBase, item.icon),
                                contentDescription = item.name,
                                modifier = Modifier
                                    .size(17.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                        }
                    }
                }
            }
        }
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            pick.units.forEach { id ->
                val entry = catalog?.unit(id)
                val unit = finalById[id]
                BuildFace(
                    icon = entry?.icon ?: unit?.icon,
                    name = entry?.name ?: unit?.name ?: id,
                    cost = entry?.cost ?: unit?.cost,
                    assetBase = assetBase,
                    size = 32.dp,
                    carry = placements[id]?.carry ?: (id == carryId),
                    pet = catalog?.isPet(id) == true || unit?.isPet == true,
                    star = placements[id]?.star ?: 1,
                )
            }
        }
    }
}

/** 빌드업 얼굴 하나. 성급을 아는 경우(작가 단계)에만 3성 별을 얹는다. 소환물에는 별이 없다. */
@Composable
private fun BuildFace(
    icon: String?,
    name: String,
    cost: Int?,
    assetBase: String,
    size: Dp,
    carry: Boolean,
    pet: Boolean,
    star: Int,
) {
    Box(contentAlignment = Alignment.TopCenter) {
        IdFace(icon, name, cost, assetBase, size, carry = carry, pet = pet)
        if (!pet && star >= 3) {
            ThreeStarMark(6.sp, Modifier.offset(y = (-2).dp))
        }
    }
}

/** 넓게 볼 때 최종 보드 유닛 한 줄: 얼굴 · 이름 · 아이템. */
@Composable
internal fun UnitLine(unit: DeckUnit, assetBase: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Face(unit, assetBase, 26.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = unit.name + if (!unit.isPet && unit.star >= 3) " ★★★" else "",
            color = if (unit.carry) OverlayAccent else OverlayText,
            fontSize = 11.sp,
            fontWeight = if (unit.carry) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            unit.items.forEach { item ->
                AsyncImage(
                    model = iconUrl(assetBase, item.icon),
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(17.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

/**
 * 챔피언 얼굴 하나.
 * 캐리는 강조 테두리, 3성은 별로 구분한다 — 글자 없이 구분되는 정보만 남겼다. 소환물은 회색 테두리에 별이 없다.
 */
@Composable
internal fun Face(unit: DeckUnit, assetBase: String, size: Dp) {
    Box(contentAlignment = Alignment.TopCenter) {
        IdFace(unit.icon, unit.name, unit.cost, assetBase, size, carry = unit.carry, pet = unit.isPet)
        if (!unit.isPet && unit.star >= 3) {
            ThreeStarMark(6.sp, Modifier.offset(y = (-2).dp))
        }
    }
}

@Composable
private fun IdFace(
    icon: String?,
    name: String,
    cost: Int?,
    assetBase: String,
    size: Dp,
    carry: Boolean = false,
    pet: Boolean = false,
) {
    // 이미지가 오기 전에도 칸이 비지 않게 코스트색을 먼저 깐다.
    val placeholder = remember(cost) { ColorPainter(costTint(cost).copy(alpha = 0.35f)) }
    val borderColor = when {
        pet -> OverlayMuted
        carry -> OverlayAccent
        else -> costTint(cost)
    }
    AsyncImage(
        model = iconUrl(assetBase, icon),
        contentDescription = name,
        placeholder = placeholder,
        error = placeholder,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .border(if (carry && !pet) 1.8.dp else 1.dp, borderColor, RoundedCornerShape(4.dp)),
    )
}
