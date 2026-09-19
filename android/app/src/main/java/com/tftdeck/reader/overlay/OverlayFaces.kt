package com.tftdeck.reader.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.tftdeck.reader.data.BuildupPick
import com.tftdeck.reader.data.BuildupPlanner
import com.tftdeck.reader.data.CatalogIndex
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.Unit as DeckUnit
import com.tftdeck.reader.ui.components.InitialMark
import com.tftdeck.reader.ui.components.ItemIcons
import com.tftdeck.reader.ui.components.ThreeStarMark
import com.tftdeck.reader.ui.iconUrl
import com.tftdeck.reader.ui.theme.FloaColors

/** 빌드업 구성의 얼굴 하나를 그릴 값. 순서·캐리·새 유닛 판단은 [overlayBuildFaces] 가 한다. */
internal data class OverlayBuildFace(val id: String, val carry: Boolean, val isNew: Boolean, val star: Int)

/**
 * 한 레벨 1순위 구성(빌드업)의 얼굴 순서와 표시(R10 일부).
 *  - 캐리를 맨 앞에 둔다. 캐리는 크기([CARRY_FACE])와 순서로 구분한다 — 금색 테두리는 1등·3성 색이라 쓰지 않는다.
 *    나머지는 빌드업이 준 순서(코스트 오름차순) 그대로다.
 *  - 새 유닛 = 같은 출처의 바로 아래 레벨 1순위에 없던 유닛([BuildupPlanner.newUnits]). 첫 레벨은 비교할 것이 없어 없다.
 *  - 캐리·성급은 작가 단계의 칸 값이 있으면 그것을, 없으면(통계 옵션) 캐리 id 로만 본다. 통계 옵션은 성급을 몰라 1이다.
 */
internal fun overlayBuildFaces(deck: Deck, pick: BuildupPick): List<OverlayBuildFace> {
    val carryId = pick.option?.carryId ?: deck.carryId
    val placements = pick.stage?.units.orEmpty().associateBy { it.id }
    val fresh = BuildupPlanner.newUnits(deck, pick)
    val faces = pick.units.map { id ->
        OverlayBuildFace(
            id = id,
            carry = placements[id]?.carry ?: (id == carryId),
            isNew = id in fresh,
            star = placements[id]?.star ?: 1,
        )
    }
    return faces.filter { it.carry } + faces.filterNot { it.carry }
}

/** 최종 보드 얼굴 순서(빌드업이 없는 덱의 요약): 캐리를 맨 앞에, 나머지는 보드 순서 그대로. */
internal fun overlayBoardFaces(units: List<DeckUnit>): List<DeckUnit> =
    units.filter { it.carry } + units.filterNot { it.carry }

/**
 * 빌드업 구성의 얼굴 줄. 빌드업에는 유닛 id 만 있어서, 같은 id 가 최종 보드에 있으면
 * 넓게 볼 때 그 유닛의 아이템을 빌려 와 누가 무엇을 들게 되는지 함께 보여 준다.
 * 좁게 볼 때는 얼굴만: 캐리 40dp 를 맨 앞에, 나머지 32dp. 레벨을 바꿔 새로 들어온 유닛은 왼쪽 위 초록 점.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BuildFaces(pick: BuildupPick, deck: Deck, catalog: CatalogIndex?, assetBase: String, wide: Boolean) {
    val finalById = remember(deck) { deck.units.associateBy { it.id } }
    val faces = remember(deck, pick) { overlayBuildFaces(deck, pick) }
    if (wide) {
        faces.forEach { face ->
            val entry = catalog?.unit(face.id)
            val unit = finalById[face.id]
            val name = entry?.name ?: unit?.name ?: face.id
            Row(verticalAlignment = Alignment.CenterVertically) {
                IdFace(
                    icon = entry?.icon ?: unit?.icon,
                    name = name,
                    cost = entry?.cost ?: unit?.cost,
                    assetBase = assetBase,
                    size = LINE_FACE,
                    carry = face.carry,
                    pet = catalog?.isPet(face.id) == true || unit?.isPet == true,
                    star = face.star,
                    newMark = face.isNew,
                )
                Spacer(Modifier.width(8.dp))
                UnitName(name, carry = face.carry, modifier = Modifier.weight(1f))
                unit?.items?.takeIf { it.isNotEmpty() }?.let { items ->
                    ItemIcons(items, assetBase, size = ITEM_ICON, spacing = 2.dp)
                }
            }
        }
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FACE_GAP),
            verticalArrangement = Arrangement.spacedBy(FACE_GAP),
        ) {
            faces.forEach { face ->
                val entry = catalog?.unit(face.id)
                val unit = finalById[face.id]
                IdFace(
                    icon = entry?.icon ?: unit?.icon,
                    name = entry?.name ?: unit?.name ?: face.id,
                    cost = entry?.cost ?: unit?.cost,
                    assetBase = assetBase,
                    size = if (face.carry) CARRY_FACE else BUILD_FACE,
                    // 캐리(40dp)와 한 줄에 서면 32dp 얼굴을 세로 가운데에 맞춘다.
                    modifier = Modifier.align(Alignment.CenterVertically),
                    carry = face.carry,
                    pet = catalog?.isPet(face.id) == true || unit?.isPet == true,
                    star = face.star,
                    newMark = face.isNew,
                )
            }
        }
    }
}

/** 빌드업이 없는 덱의 요약 얼굴 줄(최종 보드): 캐리 40dp 를 맨 앞에, 나머지 32dp. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoardFaces(units: List<DeckUnit>, assetBase: String) {
    val ordered = remember(units) { overlayBoardFaces(units) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(FACE_GAP),
        verticalArrangement = Arrangement.spacedBy(FACE_GAP),
    ) {
        ordered.forEach { unit ->
            Face(
                unit,
                assetBase,
                if (unit.carry && !unit.isPet) CARRY_FACE else BUILD_FACE,
                Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

/** 넓게 볼 때 최종 보드 유닛 한 줄: 얼굴 · 이름 · 아이템. */
@Composable
internal fun UnitLine(unit: DeckUnit, assetBase: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Face(unit, assetBase, LINE_FACE)
        Spacer(Modifier.width(8.dp))
        UnitName(
            unit.name + if (!unit.isPet && unit.star >= 3) " ★★★" else "",
            carry = unit.carry,
            modifier = Modifier.weight(1f),
        )
        ItemIcons(unit.items, assetBase, size = ITEM_ICON, spacing = 2.dp)
    }
}

/** 넓게 보기의 유닛 이름. 캐리는 굵게(색은 본문색 — 파랑은 누를 수 있음·선택됨이다). */
@Composable
private fun UnitName(name: String, carry: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = name,
        color = OverlayText,
        style = if (carry) OverlayType.label.copy(fontWeight = FontWeight.Bold) else OverlayType.label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * 챔피언 얼굴 하나(최종 보드의 유닛).
 * 캐리는 밝은 테두리, 3성은 별로 구분한다 — 글자 없이 구분되는 정보만 남겼다. 소환물은 회색 테두리에 별이 없다.
 */
@Composable
internal fun Face(unit: DeckUnit, assetBase: String, size: Dp, modifier: Modifier = Modifier) {
    IdFace(
        icon = unit.icon,
        name = unit.name,
        cost = unit.cost,
        assetBase = assetBase,
        size = size,
        modifier = modifier,
        carry = unit.carry,
        pet = unit.isPet,
        star = unit.star,
    )
}

/**
 * 얼굴 칸. 앱의 UnitPortrait 와 같은 규칙을 오버레이 글자(OverlayType)로 그린다.
 *  - 자리 표시: 코스트색 바탕에 이름 첫 글자(10sp)를 먼저 깔고 초상이 오면 덮는다. 초상이 온 뒤에는 글자를 거둔다
 *    (투명한 소환물 아이콘에 글자가 비치지 않게).
 *  - 캐리: 밝은(본문색) 테두리. 예전 연파랑 테두리는 3코스트 테두리와 같은 파랑 계열이었다(R10).
 *  - 3성: 금색 별 8sp. 20dp 얼굴에서는 별 셋이 얼굴보다 조금 넓어 칸 폭은 얼굴에 두고 별만 양옆으로 넘치게 그린다.
 *  - [newMark]: 레벨을 올려 새로 들어온 유닛 — 왼쪽 위 6dp 초록 점.
 */
@Composable
private fun IdFace(
    icon: String?,
    name: String,
    cost: Int?,
    assetBase: String,
    size: Dp,
    modifier: Modifier = Modifier,
    carry: Boolean = false,
    pet: Boolean = false,
    star: Int = 1,
    newMark: Boolean = false,
) {
    val url = iconUrl(assetBase, icon)
    var loaded by remember(url) { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (size >= LARGE_FACE) 6.dp else 4.dp)
    val borderColor = when {
        pet -> OverlayMuted
        carry -> OverlayText
        else -> costTint(cost)
    }
    val borderWidth = when {
        !carry || pet -> 1.dp
        size >= BUILD_FACE -> 2.dp
        else -> 1.5.dp
    }
    Box(modifier.size(size), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(costTint(cost).copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!loaded) InitialMark(name, OverlayType.mark.fontSize)
        }
        AsyncImage(
            model = url,
            contentDescription = name,
            onState = { state -> loaded = state is AsyncImagePainter.State.Success },
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .border(borderWidth, borderColor, shape),
        )
        if (!pet && star >= 3) {
            ThreeStarMark(
                OverlayType.star.fontSize,
                Modifier
                    .wrapContentWidth(unbounded = true)
                    .offset(y = (-3).dp),
            )
        }
        if (newMark) NewUnitDot(Modifier.align(Alignment.TopStart))
    }
}

/** 새로 들어온 유닛 표시: 6dp 초록(Positive) 점에 바탕색 테두리 1dp — 초상 위에서도 점이 묻히지 않는다. */
@Composable
private fun NewUnitDot(modifier: Modifier = Modifier) {
    Box(
        modifier
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

/** 요약의 캐리 얼굴. 캐리는 크기와 순서로 구분한다(R10). */
private val CARRY_FACE = 40.dp

/** 요약의 나머지 얼굴. */
private val BUILD_FACE = 32.dp

/** 넓게 보기의 한 줄 얼굴(이름·아이템과 함께). */
private val LINE_FACE = 26.dp

/** 이 크기부터 모서리를 6dp 로(앱 UnitPortrait 와 같은 경계). */
private val LARGE_FACE = 36.dp

/** 요약 얼굴 사이 간격. */
private val FACE_GAP = 4.dp

/** 넓게 보기 한 줄의 아이템 아이콘(dp). 아이콘 크기는 16·20·24 만 쓴다(MASTER 규칙 4). */
private const val ITEM_ICON = 16

/** 새 유닛 점 지름. */
private val NEW_MARK = 6.dp
