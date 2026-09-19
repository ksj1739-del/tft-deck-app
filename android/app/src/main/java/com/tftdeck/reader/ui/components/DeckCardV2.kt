package com.tftdeck.reader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.BucketMeta
import com.tftdeck.reader.data.CatalogEntry
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.TraitRef
import com.tftdeck.reader.data.Variant
import com.tftdeck.reader.ui.formatAvg
import com.tftdeck.reader.ui.formatCount

/**
 * 통합 덱 카드(§6.2). 모든 카드가 같은 자리에 같은 정보를 둔다:
 *  1. 등급 배지 · 별칭(1줄) · 한 줄 설명 · 출처 배지 · 추세
 *  2. 운영 칩 · 'Lv N 완성' · 특성 칩 4개
 *  3. 캐리 3명(초상 40dp + 아이템) · 나머지 유닛 얼굴 줄
 *  4. 고정 4수치(평균 등수 / 픽률 / 승률 / TOP4)
 *  5. 표본 라벨
 * 길게 누르면 고정/숨김 메뉴, 변형이 2개 이상이면 '변형 N개'로 펼친다.
 *
 * [unitInfo] 는 변형 행처럼 id만 있는 유닛의 이름·아이콘을 찾는 데 쓴다.
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
    unitInfo: (String) -> CatalogEntry? = { null },
    onOpenVariant: ((String) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val stats = deck.displayStats(bucket)
    val lowSample = deck.isLowSample(bucket)
    val hasMenu = onTogglePinned != null || onToggleHidden != null
    var menuOpen by remember { mutableStateOf(false) }
    var showVariants by rememberSaveable(deck.id) { mutableStateOf(false) }

    Box(modifier.fillMaxWidth()) {
        Card(
            // 숨긴 덱은 '숨긴 덱 보기'에서만 나온다. 흐리게 해서 복구 대상임을 알린다.
            // 이 구간 표본이 모자란 덱(등급 없음)도 흐리게 해서 수치를 과신하지 않게 한다(§4.4).
            modifier = Modifier
                .fillMaxWidth()
                .alpha(
                    when {
                        hidden -> 0.55f
                        lowSample -> LOW_SAMPLE_ALPHA
                        else -> 1f
                    }
                ),
            colors = CardDefaults.cardColors(containerColor = scheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(
                if (pinned) 1.5.dp else 1.dp,
                if (pinned) scheme.primary.copy(alpha = 0.6f) else scheme.outlineVariant,
            ),
        ) {
            Column(
                Modifier
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = if (hasMenu) ({ menuOpen = true }) else null,
                        onLongClickLabel = if (hasMenu) "고정·숨김 메뉴" else null,
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeaderLine(deck, bucket, metatftCompared, pinned)
                OpsLine(deck, assetBase)
                UnitsLine(deck, assetBase, highlightUnit)
                // v1 피드(구간 없음)에는 수치가 하나도 없어 '-' 네 칸만 남는다. 그때는 줄을 뺀다.
                if (buckets.isNotEmpty()) StatsRow(stats)
                // 표본·출처 줄(n=… · 구간 · 출처 · KR 참고)은 카드에서 뺀다. 덱을 고르는 데 쓰지 않는 정보라
                // 사용자가 '굳이 싶은 정보'로 짚었다. 상세 화면에는 남아 있다.

                if (deck.variants.size >= 2) {
                    Text(
                        if (showVariants) "변형 접기" else "변형 ${deck.variants.size}개",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showVariants = !showVariants }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    if (showVariants) {
                        deck.variants.forEach { variant ->
                            VariantRow(
                                variant = variant,
                                bucket = bucket,
                                china = deck.isMeta,
                                assetBase = assetBase,
                                unitInfo = unitInfo,
                                onClick = {
                                    if (onOpenVariant != null) onOpenVariant(variant.id) else onClick()
                                },
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            onTogglePinned?.let { toggle ->
                DropdownMenuItem(
                    text = { Text(if (pinned) "고정 해제" else "맨 위에 고정") },
                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
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
                            if (hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
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

@Composable
private fun HeaderLine(deck: Deck, bucket: String, metatftCompared: Boolean, pinned: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TierBadge(deck.gradeFor(bucket), editorial = deck.showsEditorialGrade(bucket), global = deck.isGlobalOnly)
            // 편집 등급으로 대신 보여 줄 때도 이 구간 통계가 모자라다는 사실은 알린다.
            if (deck.isLowSample(bucket) && deck.showsEditorialGrade(bucket)) {
                LowSampleNote(Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // 제목은 목적을 담은 별칭, 그 아래 한 줄 설명. 예전 긴 이름('캐리 · 4 시너지 …')은 바로 아래 시너지 칩·캐리
            // 초상과 같은 정보라 카드에서는 뺐다(덱 상세의 부제로 남는다).
            Text(
                deck.displayAlias,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            deck.displaySummary.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SourceBadges(deck, metatftCompared)
        }
        if (pinned) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.PushPin,
                contentDescription = "고정한 덱",
                tint = scheme.primary,
                modifier = Modifier.size(15.dp),
            )
        }
        TrendGlyph(deck.statsFor(bucket)?.trend, Modifier.padding(start = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpsLine(deck: Deck, assetBase: String) {
    val ops = opsTexts(deck.global)
    val traits = cardTraits(deck)
    if (ops.isEmpty() && deck.finalLevel == null && traits.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ops.forEach { OpsChip(it) }
        deck.finalLevel?.let { OpsChip("Lv $it 완성") }
        traits.forEach { TraitChip(it, assetBase) }
    }
}

/** 주 특성을 먼저 두고, 모자라면 나머지 특성으로 4개를 채운다. */
internal fun cardTraits(deck: Deck): List<TraitRef> =
    (deck.mainTraits + deck.traits.filter { trait -> deck.mainTraits.none { it.id == trait.id } }).take(4)

/**
 * 캐리 3명은 크게(아이템과 함께), 나머지는 작은 얼굴로 옆에 붙인다.
 * 한 줄에 몰아야 카드 높이가 덱마다 크게 달라지지 않는다.
 */
@Composable
private fun UnitsLine(deck: Deck, assetBase: String, highlightUnit: String?) {
    val carries = deck.carries
    val rest = deck.units.filter { unit -> carries.none { it === unit } }
    if (carries.isEmpty() && rest.isEmpty()) return

    Row(verticalAlignment = Alignment.Top) {
        carries.forEach { unit ->
            Column(
                Modifier.padding(end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                UnitPortrait(
                    icon = unit.icon,
                    name = unit.name,
                    cost = unit.cost,
                    assetBase = assetBase,
                    size = CARRY_FACE,
                    star = unit.star,
                    carry = unit.carry || unit.name == highlightUnit,
                    pet = unit.isPet,
                )
                ItemIcons(unit.items.take(3), assetBase, size = 18)
            }
        }
        if (rest.isNotEmpty()) {
            UnitGrid(rest, assetBase, Modifier.weight(1f), highlightUnit = highlightUnit, compact = true)
        }
    }
}

@Composable
private fun VariantRow(
    variant: Variant,
    bucket: String,
    /** metatft 조합 덱에 합쳐진 lol.qq 변형이면 수치가 중국 값이라고 밝힌다(덱 수치는 metatft 값). */
    china: Boolean = false,
    assetBase: String,
    unitInfo: (String) -> CatalogEntry?,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val stats = variant.stats[bucket]
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaceRow(
            faces = variant.units.map { unit ->
                faceFor(unit.id, unitInfo(unit.id), star = unit.star ?: 1, carry = unit.id == variant.carryId)
            },
            assetBase = assetBase,
            size = VARIANT_FACE,
            modifier = Modifier.weight(1f),
            // 변형 조합 원본에는 성급이 없다. 별을 그리지 않는다.
            showStars = false,
        )
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (variant.representative) OutlineBadge("대표", scheme.onSurfaceVariant)
                if (variant.editorialId != null) OutlineBadge("편집", scheme.primary)
            }
            Text(
                if (stats != null) "${if (china) "중국 " else ""}n=${formatCount(stats.n)} · ${formatAvg(stats.avg)}등" else "표본 없음",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

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

private val CARRY_FACE = 40.dp
private val VARIANT_FACE = 22.dp

/** 표본 부족 카드의 투명도. 숨긴 덱(0.55)보다 진하게 두어 둘이 구분된다. */
private const val LOW_SAMPLE_ALPHA = 0.7f
