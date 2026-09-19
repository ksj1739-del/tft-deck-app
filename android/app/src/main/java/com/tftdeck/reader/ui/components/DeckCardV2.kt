package com.tftdeck.reader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.BucketMeta
import com.tftdeck.reader.data.CatalogEntry
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.DeckStats
import com.tftdeck.reader.data.Unit as DeckUnit
import com.tftdeck.reader.ui.formatAvgRank
import com.tftdeck.reader.ui.trendColor
import com.tftdeck.reader.ui.trendGlyph

/**
 * 덱 카드(MASTER §2 '덱 카드'). 모든 카드가 같은 자리에 같은 정보를 둔다:
 *  1. 등급 배지 · 별칭 · 추세 ▲▼ + 평균 등수('4.12등')
 *  2. 설명 한 줄 '{운영} · {주 특성1 n} · {주 특성2 n}'(난이도가 쉬움·어려움이면 덧붙임) + 필요할 때만 '중국'·'표본 적음' 글자 배지.
 *     카드의 유일한 보조 글자 줄이다(규칙 2).
 *  3. 캐리 1~2명(얼굴 40dp + 아이템 12dp×3, 폭이 얼굴과 같다) · 나머지 얼굴 22dp 한 줄(넘치면 '+N')
 *  4. 수치 3칸: 평균 등수·픽률·TOP4(L4 결정 — 승률은 상세에서 본다). 중국 한정 덱의 픽률은 '–'.
 * 표본·출처 줄, 출처 배지(KR·편집·이전 패치), 운영 칩, 'Lv N 완성', '변형 N개' 는 카드에서 뺐다(L2·L3·L11·V1·V3) — 상세에 있다.
 *
 * 등급 배지는 두 등급 체계를 모양으로 가른다: metatft 등급 = 채움, 중국 한정 덱(lol.qq 등급) = 테두리 + '중국' 배지,
 * 편집 등급 = 테두리 + '편'(L1·V5). 카드 면은 surfaceContainerHighest 에 테두리 없음, 고정한 덱만 primary 60% 1.5dp 테두리.
 * 길게 누르면 고정/숨김 메뉴가 뜬다.
 *
 * [highlightUnit] 은 목록 검색이 아이템 하나로 좁혀졌을 때 그 아이템을 드는 챔피언 이름이다 — 캐리가 아니어도 큰 얼굴 칸
 * (아이템과 함께)에 올리고 파랑 테두리로 표시한다(검색으로 고른 것 = 선택됨, 규칙 3).
 * [buckets] 가 비면(v1 피드) 수치가 없으므로 수치 줄을 뺀다. [unitInfo]·[onOpenVariant] 는 변형 펼치기를 뺀 뒤
 * 쓰지 않지만 예전 호출부(검색 탭)와 맞추려고 남겨 둔다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeckCardV2(
    deck: Deck,
    bucket: String,
    assetBase: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buckets: Map<String, BucketMeta> = emptyMap(),
    metatftCompared: Boolean = true,
    pinned: Boolean = false,
    hidden: Boolean = false,
    onTogglePinned: (() -> Unit)? = null,
    onToggleHidden: (() -> Unit)? = null,
    highlightUnit: String? = null,
    @Suppress("UNUSED_PARAMETER") unitInfo: (String) -> CatalogEntry? = { null },
    @Suppress("UNUSED_PARAMETER") onOpenVariant: ((String) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val stats = deck.displayStats(bucket)
    // metatft 대조를 못 한 날은 '중국 한정'을 믿을 수 없어 표시하지 않는다(오버레이와 같은 규칙).
    val chinaOnly = metatftCompared && deck.isOnlyInChina
    val hasMenu = onTogglePinned != null || onToggleHidden != null
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier.fillMaxWidth()) {
        Card(
            // 숨긴 덱은 '숨긴 덱 보기'에서만 나온다. 흐리게 해서 복구 대상임을 알린다.
            // 이 구간 표본이 모자란 덱(등급 없음)도 흐리게 해서 수치를 과신하지 않게 한다(§4.4).
            modifier = Modifier
                .fillMaxWidth()
                .alpha(
                    when {
                        hidden -> HIDDEN_ALPHA
                        deck.isLowSample(bucket) -> LOW_SAMPLE_ALPHA
                        else -> 1f
                    }
                ),
            shape = CARD_SHAPE,
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHighest),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = if (pinned) BorderStroke(PINNED_BORDER, scheme.primary.copy(alpha = 0.6f)) else null,
        ) {
            Column(
                Modifier
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = if (hasMenu) ({ menuOpen = true }) else null,
                        onLongClickLabel = if (hasMenu) "고정·숨김 메뉴" else null,
                    )
                    .padding(CARD_PADDING),
            ) {
                HeaderLine(deck, bucket, stats, chinaOnly)
                Spacer(Modifier.height(4.dp))
                DescriptionLine(deck, stats, chinaOnly)
                Spacer(Modifier.height(8.dp))
                UnitsLine(deck, assetBase, highlightUnit)
                // v1 피드(구간 없음)에는 수치가 하나도 없어 '-' 만 남는다. 그때는 줄을 뺀다.
                if (buckets.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    StatsRow(stats, showWin = false, pickAvailable = !chinaOnly)
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            onTogglePinned?.let { toggle ->
                DropdownMenuItem(
                    text = { Text(if (pinned) "고정 해제" else "맨 위에 고정") },
                    leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        toggle()
                    },
                )
            }
            onToggleHidden?.let { toggle ->
                DropdownMenuItem(
                    text = { Text(if (hidden) "숨김 해제" else "이 덱 숨기기") },
                    leadingIcon = {
                        Icon(
                            if (hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        toggle()
                    },
                )
            }
        }
    }
}

/**
 * 1줄: [등급 배지][별칭][▲▼]. 평균 등수는 바로 아래 수치 줄(평균 등수·TOP4·픽률)에 있으므로 여기서 되풀이하지 않는다
 * (사용자: '굳이 싶은 정보'). 추세 기호만 남긴다.
 */
@Composable
private fun HeaderLine(deck: Deck, bucket: String, stats: DeckStats?, chinaOnly: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        GradeBadge(deck.gradeFor(bucket).orEmpty(), cardBadgeStyle(deck, bucket, chinaOnly))
        Spacer(Modifier.width(8.dp))
        Text(
            deck.displayAlias,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (stats != null) {
            val glyph = trendGlyph(stats.trend)
            if (glyph.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                // 초록·빨강은 늘 ▲▼ 와 함께(규칙 3). 읽어 줄 때는 기호 대신 낱말로.
                val spoken = if (stats.trend == "up") "상승" else "하락"
                Text(
                    glyph,
                    style = MaterialTheme.typography.labelLarge,
                    color = trendColor(stats.trend),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                )
            }
        }
    }
}

/** 2줄: 설명 한 줄 + '중국'·'표본 적음' 배지. 배지는 오른쪽 끝에 붙여 설명이 길어도 잘리지 않게 한다. */
@Composable
private fun DescriptionLine(deck: Deck, stats: DeckStats?, chinaOnly: Boolean) {
    val text = cardDescription(deck)
    val badges = cardBadges(chinaOnly, stats)
    if (text.isEmpty() && badges.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            // 배지(20dp)가 있든 없든 줄 높이를 같게 두어 카드 높이가 덱마다 흔들리지 않게 한다.
            .heightIn(min = LINE2_MIN),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        badges.forEach { badge ->
            Spacer(Modifier.width(4.dp))
            TextBadge(badge)
        }
    }
}

/**
 * 3줄: 캐리 1~2명은 크게(아이템과 함께), 나머지는 22dp 얼굴 한 줄. 두 줄로 꺾이지 않게 넘치면 '+N' 으로 줄인다(L5·V17) —
 * 한 줄에 몰아야 카드 높이가 덱마다 달라지지 않는다.
 */
@Composable
private fun UnitsLine(deck: Deck, assetBase: String, highlightUnit: String?) {
    val big = cardCarries(deck, highlightUnit)
    val rest = deck.units.filter { unit -> big.none { it === unit } }
    if (big.isEmpty() && rest.isEmpty()) return

    Row(verticalAlignment = Alignment.Top) {
        big.forEachIndexed { index, unit ->
            if (index > 0) Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Face(unit, assetBase, CARRY_FACE, highlighted = highlightUnit != null && unit.name == highlightUnit)
                Spacer(Modifier.height(2.dp))
                // 아이템이 없는 캐리도 아이템 줄 높이를 차지해 카드 높이가 같게 남는다.
                Box(Modifier.height(ITEM_SIZE.dp)) {
                    ItemIcons(unit.items.take(3), assetBase, size = ITEM_SIZE, spacing = ITEM_GAP)
                }
            }
        }
        if (rest.isNotEmpty()) {
            if (big.isNotEmpty()) Spacer(Modifier.width(8.dp))
            RestFaces(
                rest,
                assetBase,
                Modifier
                    .weight(1f)
                    .height(CARRY_FACE),
            )
        }
    }
}

@Composable
private fun Face(unit: DeckUnit, assetBase: String, size: Dp, highlighted: Boolean) {
    Box {
        UnitPortrait(
            icon = unit.icon,
            name = unit.name,
            cost = unit.cost,
            assetBase = assetBase,
            size = size,
            star = unit.star,
            carry = unit.carry,
            pet = unit.isPet,
        )
        if (highlighted) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(HIGHLIGHT_BORDER, MaterialTheme.colorScheme.primary, RoundedCornerShape(if (size >= 36.dp) 6.dp else 4.dp))
            )
        }
    }
}

/** 나머지 얼굴 한 줄. 폭에 들어가는 만큼만 그리고 남는 수는 '+N' 칸 하나로. 큰 얼굴 줄의 가운데 높이에 맞춘다. */
@Composable
private fun RestFaces(units: List<DeckUnit>, assetBase: String, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val (shown, extra) = restFaceSlots(maxWidth.value, REST_FACE.value, REST_GAP.value, units.size)
        Row(
            horizontalArrangement = Arrangement.spacedBy(REST_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            units.take(shown).forEach { unit ->
                UnitPortrait(
                    icon = unit.icon,
                    name = unit.name,
                    cost = unit.cost,
                    assetBase = assetBase,
                    size = REST_FACE,
                    star = unit.star,
                    carry = unit.carry,
                    pet = unit.isPet,
                )
            }
            if (extra > 0) MoreFaces(extra)
        }
    }
}

@Composable
private fun MoreFaces(count: Int) {
    Box(
        Modifier
            .height(REST_FACE)
            .widthIn(min = REST_FACE)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceBright)
            .clearAndSetSemantics { contentDescription = "챔피언 ${count}명 더" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "+$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// 카드 규칙(순수 함수 — DeckListLogicTest 가 검사한다)
// ---------------------------------------------------------------------------

/**
 * 카드 등급 배지 모양. 편집 등급을 보여 주는 중이면 Editorial, 중국 한정 덱(lol.qq 등급)은 Outlined,
 * 그 밖(metatft 등급)은 Filled. [chinaOnly] 는 metatft 대조가 된 날의 '중국 한정' 여부다.
 */
internal fun cardBadgeStyle(deck: Deck, bucket: String, chinaOnly: Boolean): GradeBadgeStyle = when {
    deck.showsEditorialGrade(bucket) -> GradeBadgeStyle.Editorial
    chinaOnly -> GradeBadgeStyle.Outlined
    else -> GradeBadgeStyle.Filled
}

/** 운영 어휘(MASTER 규칙 8). 이 밖의 말('표준'·'N레벨 완성' 같은 옛 어휘)은 카드에 쓰지 않는다. */
private val OPS_WORDS = Regex("""빠른 \d+레벨|\d+레벨 리롤|표준 운영|최종 \d+레벨""")

/**
 * 카드 설명 줄의 운영. metatft 운영 방식(levelling) → 수집기 설명의 첫 마디 → 마무리 레벨('최종 N레벨') 순으로,
 * 운영 어휘에 맞는 첫 값을 쓴다. 중국 한정 덱은 levelling 이 없어 설명 첫 마디('최종 9레벨')를 쓴다.
 */
internal fun cardOps(deck: Deck): String? =
    listOfNotNull(deck.global?.levelling, deck.summary.substringBefore(" · "))
        .map { it.trim() }
        .firstOrNull { OPS_WORDS.matches(it) }
        ?: deck.finalLevel?.let { "최종 ${it}레벨" }

/**
 * 카드 2줄 설명: '{운영} · {주 특성1 n} · {주 특성2 n}', 난이도가 쉬움·어려움이면 ' · 난이도 쉬움' 을 덧붙인다('보통'은 뺀다, L3).
 * 수집기 설명의 셋째 마디(캐리 아이템)는 쓰지 않는다 — 아이템은 바로 아래 얼굴 줄이 보여 준다.
 * 주 특성이 없으면 전체 특성에서 활성 수가 가장 많은 하나를 쓴다.
 */
internal fun cardDescription(deck: Deck): String {
    val traits = deck.mainTraits.filter { it.name.isNotBlank() }
        .ifEmpty { deck.traits.filter { it.name.isNotBlank() }.sortedByDescending { it.count }.take(1) }
        .take(CARD_TRAITS)
        .map { trait -> if (trait.count > 0) "${trait.name.trim()} ${trait.count}" else trait.name.trim() }
    val difficulty = deck.global?.difficulty?.trim()?.takeIf { it in NOTABLE_DIFFICULTY }?.let { "난이도 $it" }
    return (listOfNotNull(cardOps(deck)) + traits + listOfNotNull(difficulty)).joinToString(" · ")
}

private const val CARD_TRAITS = 2
private val NOTABLE_DIFFICULTY = setOf("쉬움", "어려움")

/**
 * 설명 줄 끝의 글자 배지. 중국 한정 덱은 '중국'(수치 척도가 metatft 와 다르다는 표시), 이 구간 표본이 [DeckKeys.MIN_SAMPLE]
 * 보다 적거나 등급이 없으면 '표본 적음'. 표본 수 자체(n=)는 카드에 쓰지 않는다(규칙 2).
 */
internal fun cardBadges(chinaOnly: Boolean, stats: DeckStats?): List<String> = buildList {
    if (chinaOnly) add("중국")
    if (stats != null && (stats.n < DeckKeys.MIN_SAMPLE || stats.grade == null)) add("표본 적음")
}

/**
 * 큰 얼굴 칸에 올릴 챔피언: 캐리 순위 앞의 2명. [highlightUnit](검색한 아이템을 드는 챔피언)이 그 안에 없으면
 * 둘째 칸을 그 챔피언으로 바꾼다 — 아이템은 큰 얼굴 아래에만 보이므로, 검색한 아이템이 누구 손에 있는지 카드에서 보이게.
 */
internal fun cardCarries(deck: Deck, highlightUnit: String? = null): List<DeckUnit> {
    val top = deck.carries.take(CARD_CARRIES)
    val wearer = highlightUnit?.let { name -> deck.units.firstOrNull { it.name == name } } ?: return top
    if (top.any { it === wearer }) return top
    return top.take(CARD_CARRIES - 1) + wearer
}

private const val CARD_CARRIES = 2

/**
 * 나머지 얼굴 줄에 그릴 수. [available] 폭(dp)에 [face] 크기 얼굴을 [gap] 간격으로 몇 개 놓을 수 있는지 보고,
 * 다 들어가면 (전부, 0), 넘치면 마지막 칸을 '+N' 에 내주고 (얼굴 수, N) 을 돌려준다.
 */
internal fun restFaceSlots(available: Float, face: Float, gap: Float, count: Int): Pair<Int, Int> {
    if (count <= 0) return 0 to 0
    // 폭이 딱 맞는 경우가 부동소수 오차로 한 칸 모자라지 않게 아주 작은 값을 더한다.
    val fit = ((available + gap) / (face + gap) + 0.001f).toInt()
    return when {
        count <= fit -> count to 0
        fit <= 1 -> 0 to count
        else -> (fit - 1) to (count - fit + 1)
    }
}

// ---------------------------------------------------------------------------
// 얼굴 줄(덱 상세가 쓴다)
// ---------------------------------------------------------------------------

/** id 로만 아는 유닛 얼굴 하나를 그리는 데 필요한 값. */
data class FaceSpec(
    val id: String,
    val name: String,
    val icon: String?,
    val cost: Int?,
    val star: Int = 1,
    val carry: Boolean = false,
    val pet: Boolean = false,
    val newMark: Boolean = false,
)

/** catalog 항목으로 얼굴을 만든다. catalog 에 없으면 id 를 이름 삼아 빈 칸이라도 남긴다. */
fun faceFor(
    id: String,
    entry: CatalogEntry?,
    star: Int = 1,
    carry: Boolean = false,
    pet: Boolean = entry?.kind == DeckKeys.KIND_PET,
    newMark: Boolean = false,
): FaceSpec = FaceSpec(
    id = id,
    name = entry?.name ?: id,
    icon = entry?.icon,
    cost = entry?.cost,
    star = star,
    carry = carry,
    pet = pet,
    newMark = newMark,
)

/** 얼굴 줄. 폭이 모자라면 다음 줄로 넘긴다(가로 스크롤을 만들지 않는다). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FaceRow(
    faces: List<FaceSpec>,
    assetBase: String,
    size: Dp,
    modifier: Modifier = Modifier,
    showStars: Boolean = true,
    spacing: Dp = 2.dp,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing + 2.dp),
    ) {
        faces.forEach { face ->
            UnitPortrait(
                icon = face.icon,
                name = face.name,
                cost = face.cost,
                assetBase = assetBase,
                size = size,
                star = face.star,
                carry = face.carry,
                pet = face.pet,
                showStar = showStars,
                newMark = face.newMark,
            )
        }
    }
}

// 카드 치수(MASTER 규칙 4·9): 모서리 12, 안쪽 12, 캐리 얼굴 40 + 아이템 12×3(간격 2 → 40), 나머지 얼굴 22(간격 4).
private val CARD_SHAPE = RoundedCornerShape(12.dp)
private val CARD_PADDING = 12.dp
private val PINNED_BORDER = 1.5.dp
private val CARRY_FACE = 40.dp
private const val ITEM_SIZE = 12
private val ITEM_GAP = 2.dp
private val REST_FACE = 22.dp
private val REST_GAP = 4.dp
private val HIGHLIGHT_BORDER = 2.dp
private val LINE2_MIN = 20.dp

/** 숨긴 덱의 투명도. */
private const val HIDDEN_ALPHA = 0.55f

/** 표본 부족 카드의 투명도. 숨긴 덱(0.55)보다 진하게 두어 둘이 구분된다. */
private const val LOW_SAMPLE_ALPHA = 0.7f
