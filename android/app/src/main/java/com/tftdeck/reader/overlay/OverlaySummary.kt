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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.BuildupPlanner
import com.tftdeck.reader.data.CatalogIndex
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.ui.components.ItemIcons
import com.tftdeck.reader.ui.components.TraitChip

/**
 * 덱 요약. 위에서부터
 *  1. `[등급 배지] 별칭` — 무슨 덱인지(R4). 별칭의 운영 접미사는 바로 아래 줄 첫머리에 나오므로 뗀다(목록 줄과 같다).
 *  2. `{운영} · {주 특성1 n} · {주 특성2 n}` — 어떻게 굴리는 덱인지.
 *  3. (넓게) 특성 칩.
 *  4. 레벨 칩과 바로 아래 캡션 `'{L}렙 {round} 도달 · 롤다운 {r}렙'`(R10 — 예전에는 얼굴 줄 밑 10sp 였다).
 *  5. 그 레벨 1순위 구성의 얼굴(캐리 40dp 맨 앞, 새 유닛 초록 점, 3성 별 8sp). (넓게) 이름·아이템과 재료.
 * 평균 등수·픽률·승률은 게임을 가려서 여전히 뺐다(앱의 덱 상세에서 본다).
 *
 * 등급 배지에 쓸 구간([bucket])과 metatft 비교 여부([metatftCompared])를 루트가 넘기지 않으면 앱과 함께 쓰는 조회 조건을
 * 직접 읽는다([rememberOverlayQuery], 루트와 같은 규칙).
 */
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
    bucket: String? = null,
    metatftCompared: Boolean? = null,
) {
    val query = rememberOverlayQuery()
    val gradeBucket = bucket ?: query.bucket
    val style = overlayGradeStyle(deck, gradeBucket, metatftCompared ?: query.metatftCompared)
    val alias = remember(deck) { overlayRowAlias(deck.displayAlias) }
    val line = remember(deck) { overlaySummaryLine(deck) }
    // 레벨 칩: 빌드업(글로벌·중국·작가 단계)이 있는 레벨. 기본 선택 규칙은 덱 상세와 같다.
    val levels = remember(deck) { BuildupPlanner.levels(deck) }
    val defaultLevel = remember(deck, levels) { BuildupPlanner.defaultLevel(deck, levels) }
    // 고른 레벨은 서비스가 덱마다 기억한다 — 접었다 펴도, 서비스가 다시 떠도 그 레벨이다. 고른 적 없거나
    // 그 레벨 칩이 사라졌으면(패치로 빌드업이 바뀜) 기본 레벨.
    val level = resolveOverlayLevel(savedLevel, levels, defaultLevel)
    val pick = remember(deck, level) { level?.let { BuildupPlanner.topPick(deck, it) } }
    val caption = remember(deck, level) { level?.let { overlayLevelCaption(deck, it) } }

    Column(
        modifier = modifier
            .heightIn(max = SUMMARY_MAX)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverlayGradeBadges(deck, gradeBucket, style)
                Spacer(Modifier.width(8.dp))
                Text(
                    alias,
                    color = OverlayText,
                    style = OverlayType.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (line.isNotBlank()) {
                Text(
                    line,
                    color = OverlaySubtext,
                    style = OverlayType.body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (wide && deck.traits.isNotEmpty()) {
            // 특성 칩은 공용 TraitChip(본문색 글자, 단계색은 아이콘 칸에만) — 같은 색 칩 위 같은 색 글자는 대비가 모자랐다(R13).
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                deck.traits.take(WIDE_TRAITS).forEach { trait -> TraitChip(trait, assetBase) }
            }
        }

        if (levels.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LevelChips(
                    levels = levels,
                    selected = level,
                    dim = { BuildupPlanner.isLowSample(deck, it) },
                    onSelect = onSelectLevel,
                )
                if (caption != null) {
                    Text(
                        caption,
                        color = OverlayMuted,
                        style = OverlayType.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        when {
            // 그 레벨의 1순위 구성(글로벌 → 중국 → 작가). 보드 그림은 게임을 가려서 그리지 않는다.
            pick != null -> BuildFaces(pick, deck, catalog, assetBase, wide)
            wide -> deck.units.forEach { unit -> UnitLine(unit, assetBase) }
            // 얼굴만. 이름과 아이템은 게임 화면을 가려서 좁게 볼 때는 뺀다.
            // 프로필 카드가 붙으면 패널이 좁아지므로 고정 칸수 대신 폭에 맞춰 접는다.
            else -> BoardFaces(deck.units, assetBase)
        }

        if (wide && deck.componentOrder.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("재료", color = OverlayMuted, style = OverlayType.label)
                Spacer(Modifier.width(8.dp))
                ItemIcons(deck.componentOrder.take(5), assetBase, size = MATERIAL_ICON, spacing = 4.dp)
            }
        }
    }
}

/** 운영 어휘(MASTER 규칙 8). 이 밖의 말('표준', 'N레벨 완성', 옛 metatft 원문)은 요약에 쓰지 않는다. */
private val OPERATION = Regex("""빠른 \d+레벨|\d+레벨 리롤|표준 운영|최종 \d+레벨""")

/**
 * 덱의 운영 한마디: 빠른 8레벨 · 빠른 9레벨 · N레벨 리롤 · 표준 운영 · 최종 N레벨.
 * 수집기가 설명(summary) 첫머리에 싣는 값을 먼저 쓴다 — 중국 한정 덱의 운영은 수집기만 계산할 수 있다.
 * 없으면 metatft 운영(levelling), 그것도 없으면 마무리 레벨로 '최종 N레벨'. 운영 어휘 밖의 말이면 버린다.
 */
internal fun overlayOperation(deck: Deck): String? {
    val head = deck.summary.substringBefore(" · ").trim()
    if (OPERATION.matches(head)) return head
    deck.global?.levelling?.trim()?.takeIf { OPERATION.matches(it) }?.let { return it }
    return deck.finalLevel?.let { "최종 ${it}레벨" }
}

/**
 * 요약 둘째 줄 `'{운영} · {주 특성1 n} · {주 특성2 n}'`(R4). 주 특성이 없는 덱은 전체 특성에서 인원 많은 순으로 둘.
 * 운영을 모르면 특성만, 둘 다 없으면 빈 글자.
 */
internal fun overlaySummaryLine(deck: Deck): String {
    val traits = deck.mainTraits.filter { it.name.isNotBlank() }
        .ifEmpty { deck.traits.filter { it.name.isNotBlank() }.sortedByDescending { it.count } }
    val traitParts = traits.take(SUMMARY_TRAITS).map { trait ->
        if (trait.count > 0) "${trait.name.trim()} ${trait.count}" else trait.name.trim()
    }
    return (listOfNotNull(overlayOperation(deck)) + traitParts).joinToString(" · ")
}

/**
 * 레벨 칩 아래 캡션 `'{L}렙 {round} 도달 · 롤다운 {r}렙'`. round = 그 레벨에 가장 흔히 도달하는 라운드
 * ([BuildupPlanner.timings], metatft), 없으면 그 레벨 작가 단계의 라운드. r = 판당 리롤이 가장 많은 레벨
 * ([BuildupPlanner.rollLevel]). 모르는 쪽은 빼고, 둘 다 모르면 캡션을 두지 않는다('8렙' 만으로는 칩과 같은 말이다).
 * 예전 `'주 리롤 N렙'` 은 뜻이 모호해 쓰지 않는다(R10·D9).
 */
internal fun overlayLevelCaption(deck: Deck, level: Int): String? {
    val round = BuildupPlanner.timings(deck).firstOrNull { it.first == level }?.second
        ?: deck.editorial?.stages.orEmpty()
            .firstOrNull { it.level == level && !it.round.isNullOrBlank() }?.round?.trim()
    val roll = BuildupPlanner.rollLevel(deck)
    val parts = listOfNotNull(
        round?.let { "${level}렙 $it 도달" },
        roll?.let { "롤다운 ${it}렙" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelChips(levels: List<Int>, selected: Int?, dim: (Int) -> Boolean, onSelect: (Int) -> Unit) {
    // 판마다 누르는 칩이라 칸을 키우고(높이 30dp) 칩 사이를 벌려 옆 레벨이 눌리지 않게 한다.
    // 가로 화면 패널(300dp)에서는 4~10렙 일곱 개가 한 줄에 들어간다. 고른 칩은 채움·테두리·글자색으로 보이고
    // 글자 굵기는 바꾸지 않는다 — 굵기가 바뀌면 칩 폭이 달라져 줄이 흔들린다(MASTER 규칙 6).
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
                    style = OverlayType.label,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 덱 요약 높이 상한(세로 화면 기준). 가로 화면에서는 남는 높이만 쓰고 그 안에서 스크롤한다. */
private val SUMMARY_MAX = 330.dp

/** 레벨 칩 높이. 게임 중 판마다 누르는 칩이라 글자보다 칸을 크게 둔다(예전 약 20dp). */
private val LEVEL_CHIP_HEIGHT = 30.dp

/** 넓게 볼 때 특성 칩 수. */
private const val WIDE_TRAITS = 6

/** 요약 둘째 줄의 주 특성 수. */
private const val SUMMARY_TRAITS = 2

/** 재료 아이콘(dp). 아이콘 크기는 16·20·24 만 쓴다(MASTER 규칙 4). */
private const val MATERIAL_ICON = 20
