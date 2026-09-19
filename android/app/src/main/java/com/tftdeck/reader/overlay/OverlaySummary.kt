package com.tftdeck.reader.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.BuildupPlanner
import com.tftdeck.reader.data.CatalogIndex
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.ui.iconUrl
import com.tftdeck.reader.ui.traitStyleColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeckSummaryView(
    deck: Deck,
    catalog: CatalogIndex?,
    assetBase: String,
    wide: Boolean,
    savedLevel: Int?,
    onSelectLevel: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 레벨 칩: 빌드업(글로벌·중국·작가 단계)이 있는 레벨. 기본 선택 규칙은 덱 상세와 같다.
    val levels = remember(deck) { BuildupPlanner.levels(deck) }
    val defaultLevel = remember(deck, levels) { BuildupPlanner.defaultLevel(deck, levels) }
    // 고른 레벨은 서비스가 덱마다 기억한다 — 접었다 펴도, 서비스가 다시 떠도 그 레벨이다. 고른 적 없거나
    // 그 레벨 칩이 사라졌으면(패치로 빌드업이 바뀜) 기본 레벨.
    val level = resolveOverlayLevel(savedLevel, levels, defaultLevel)
    val pick = level?.let { BuildupPlanner.topPick(deck, it) }

    Column(
        modifier = modifier
            .heightIn(max = SUMMARY_MAX)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 덱 이름과 평균 등수·픽률·승률은 게임을 가려서 오버레이에서는 뺐다(앱의 덱 상세에서 본다).
        if (wide) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                deck.traits.take(6).forEach { trait ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(traitStyleColor(trait.style).copy(alpha = 0.22f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "${trait.count} ${trait.name}",
                            color = traitStyleColor(trait.style),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        if (levels.isNotEmpty()) {
            LevelChips(
                levels = levels,
                selected = level,
                dim = { BuildupPlanner.isLowSample(deck, it) },
                onSelect = onSelectLevel,
            )
        }

        if (pick != null) {
            // 그 레벨의 1순위 구성(글로벌 → 중국 → 작가). 보드 그림은 게임을 가려서 그리지 않는다.
            BuildFaces(pick, deck, catalog, assetBase, wide)
            Text(BuildupPlanner.caption(deck, pick.level), color = OverlayMuted, fontSize = 10.sp)
        } else if (wide) {
            deck.units.forEach { unit -> UnitLine(unit, assetBase) }
        } else {
            // 얼굴만. 이름과 아이템은 게임 화면을 가려서 좁게 볼 때는 뺀다.
            // 프로필 카드가 붙으면 패널이 좁아지므로 고정 칸수 대신 폭에 맞춰 접는다.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                deck.units.forEach { unit -> Face(unit, assetBase, 32.dp) }
            }
        }

        if (wide && deck.componentOrder.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("재료", color = OverlayMuted, fontSize = 10.sp)
                Spacer(Modifier.width(5.dp))
                deck.componentOrder.take(5).forEach { item ->
                    AsyncImage(
                        model = iconUrl(assetBase, item.icon),
                        contentDescription = item.name,
                        modifier = Modifier
                            .padding(end = 3.dp)
                            .size(18.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                }
            }
        }

    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelChips(levels: List<Int>, selected: Int?, dim: (Int) -> Boolean, onSelect: (Int) -> Unit) {
    // 판마다 누르는 칩이라 칸을 키우고(높이 30dp) 칩 사이를 벌려 옆 레벨이 눌리지 않게 한다.
    // 가로 화면 패널(300dp)에서는 4~10렙 일곱 개가 한 줄에 들어간다.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        levels.forEach { lv ->
            val on = lv == selected
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .heightIn(min = LEVEL_CHIP_HEIGHT)
                    .alpha(if (dim(lv) && !on) 0.5f else 1f)
                    .clip(shape)
                    .background(if (on) OverlayAccent.copy(alpha = 0.28f) else OverlayHeader)
                    .border(1.dp, if (on) OverlayAccent else OverlayBorder, shape)
                    .clickable { onSelect(lv) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${lv}렙",
                    color = if (on) OverlayAccent else OverlayText,
                    fontSize = 11.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 덱 요약 높이 상한(세로 화면 기준). 가로 화면에서는 남는 높이만 쓰고 그 안에서 스크롤한다. */
private val SUMMARY_MAX = 330.dp

/** 레벨 칩 높이. 게임 중 판마다 누르는 칩이라 글자보다 칸을 크게 둔다(예전 약 20dp). */
private val LEVEL_CHIP_HEIGHT = 30.dp
