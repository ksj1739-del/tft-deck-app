package com.tftdeck.reader.ui.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.ChampionAbility
import com.tftdeck.reader.data.ChampionBase
import com.tftdeck.reader.data.ChampionBuildStat
import com.tftdeck.reader.data.ChampionRow
import com.tftdeck.reader.data.SearchAxis
import com.tftdeck.reader.data.StatsState
import com.tftdeck.reader.ui.costColor

/**
 * 챔피언 상세: 헤더 → 스킬 → 범위별 성적 → 지난 패치 대비 → 추천 아이템·빌드 → 성급별 →
 * 중국 착용 아이템 → 이 챔피언 덱.
 */
@Composable
fun ChampionDetailScreen(
    id: String,
    viewModel: CodexViewModel,
    onOpenDeck: (String) -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val scope by viewModel.scope.collectAsState()
    val assetBase by viewModel.assetBase.collectAsState()
    val deckLinks by viewModel.deckLinks.collectAsState()

    val ready = when (val current = state) {
        is StatsState.Loading -> {
            CodexLoading()
            return
        }
        is StatsState.Missing -> {
            CodexMissing()
            return
        }
        is StatsState.Ready -> current
    }
    val champion = ready.championsById[id]
    if (champion == null) {
        CodexNotFound("챔피언")
        return
    }

    val file = ready.champions
    val scopes = CodexQuery.availableScopes(file.scopes)
    val selected = CodexQuery.resolveScope(scope, scopes)
    val decks = remember(deckLinks, champion.id, champion.decks) {
        viewModel.decksFor(champion.decks, SearchAxis.CHAMPION, champion.name)
    }
    val prevEntries = orderedEntries(champion.prev)
    val starStats = CodexQuery.scopedList(champion.starStats, selected)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = DetailPadding,
        verticalArrangement = Arrangement.spacedBy(DetailGap),
    ) {
        item(key = "header") { ChampionHeader(champion, ready, assetBase) }

        champion.ability?.let { ability ->
            item(key = "ability") { AbilitySection(ability, champion.base) }
        }

        item(key = "scopes") {
            Column {
                SectionTitle("범위별 성적", trailing = "행을 누르면 기준이 바뀝니다")
                ScopeTable(
                    scopes = scopes,
                    selected = selected,
                    minSample = file.minSample,
                    statFor = { champion.stat(it) },
                    onSelect = viewModel::setScope,
                )
            }
        }

        if (prevEntries.isNotEmpty()) {
            item(key = "prev") {
                Column {
                    SectionTitle(
                        "지난 패치 대비",
                        trailing = file.prevPatch?.patchGlobal?.takeIf { it.isNotBlank() }?.let { "$it 마지막 값" },
                    )
                    prevEntries.forEach { (key, prev) -> PrevChangeRow(key, prev, champion.stat(key)) }
                }
            }
        }

        if (champion.items.top.isNotEmpty() || champion.items.builds.isNotEmpty()) {
            item(key = "items") { RecommendedItemsSection(champion, ready, assetBase, onOpenItem) }
        }

        if (starStats != null) {
            item(key = "stars") {
                Column {
                    SectionTitle("성급별 성과", trailing = scopeLabel(starStats.first))
                    StarStatsRow(starStats.second.map { it.star to (it.avg to it.n) })
                }
            }
        }

        if (champion.cnItems.isNotEmpty()) {
            item(key = "cnItems") {
                Column {
                    SectionTitle("중국 착용 아이템", trailing = "중국 플래+ · 많이 드는 순")
                    champion.cnItems.take(5).forEach { stat ->
                        val itemRow = ready.itemsById[stat.id]
                        ItemStatLine(
                            url = codexIconUrl(assetBase, stat.icon ?: itemRow?.icon),
                            name = stat.name.ifBlank { itemRow?.name ?: stat.id },
                            avg = stat.avg,
                            n = stat.n,
                            delta = null,
                            onClick = { onOpenItem(stat.id) },
                        )
                    }
                    Text(
                        "중국 서버 아이템 평균은 원천마다 차이가 커서 인기도 참고용입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "decks") {
            DeckLinkSection("이 챔피언 덱", decks, "이 챔피언이 들어간 덱이 없습니다.", onOpenDeck)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChampionHeader(champion: ChampionRow, ready: StatsState.Ready, assetBase: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        CodexIcon(
            codexIconUrl(assetBase, champion.icon),
            champion.name,
            64.dp,
            borderColor = costColor(champion.cost),
            corner = 10.dp,
            borderWidth = 2.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    champion.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                champion.changed?.takeIf { it.isNotBlank() }?.let { changed ->
                    Spacer(Modifier.width(8.dp))
                    LabelChip(changedLabel(changed), changedColor(changed))
                }
            }
            val meta = listOfNotNull(
                champion.cost?.let { "${it}코스트" },
                champion.role?.takeIf { it.isNotBlank() },
                champion.nameEn?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            if (champion.traits.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    champion.traits.forEach { ref ->
                        val trait = ready.traitsById[ref.id]
                        // 챔피언이 어느 단계인지는 모르므로 등급색 없이 중립 바탕으로 그린다.
                        TraitNameChip(ref.name.ifBlank { trait?.name ?: ref.id }, codexIconUrl(assetBase, trait?.icon), 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun AbilitySection(ability: ChampionAbility, base: ChampionBase?) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle("스킬", trailing = manaText(ability.mana))
        if (ability.name.isNotBlank()) {
            Text(ability.name, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        }
        val desc = cleanDesc(ability.desc)
        if (desc.isNotBlank()) {
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
        }
        val baseText = base?.let(::baseStatsText).orEmpty()
        if (baseText.isNotBlank()) {
            Text(baseText, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

/** "마나 20 / 100". */
internal fun manaText(mana: List<Double>): String? = when {
    mana.isEmpty() -> null
    mana.size == 1 -> "마나 ${formatNumber(mana[0])}"
    else -> "마나 ${formatNumber(mana[0])} / ${formatNumber(mana[1])}"
}

/** "체력 700 / 1260 / 2268 · 공격력 40 / 60 / 90 · 방어력 30 …". */
internal fun baseStatsText(base: ChampionBase): String = buildList {
    if (base.health.isNotEmpty()) add("체력 " + base.health.joinToString(" / ", transform = ::formatNumber))
    if (base.attackDamage.isNotEmpty()) add("공격력 " + base.attackDamage.joinToString(" / ", transform = ::formatNumber))
    base.armor?.let { add("방어력 ${formatNumber(it)}") }
    base.magicResist?.let { add("마법 저항력 ${formatNumber(it)}") }
    base.attackSpeed?.let { add("공격 속도 ${formatNumber(it)}") }
    base.range?.let { add("사거리 ${formatNumber(it)}") }
}.joinToString(" · ")

@Composable
private fun RecommendedItemsSection(
    champion: ChampionRow,
    ready: StatsState.Ready,
    assetBase: String,
    onOpenItem: (String) -> Unit,
) {
    val recommended = champion.items
    Column {
        SectionTitle("추천 아이템", trailing = recommended.scope.takeIf { it.isNotBlank() }?.let(::scopeLabel))
        recommended.top.take(5).forEach { stat ->
            val itemRow = ready.itemsById[stat.id]
            ItemStatLine(
                url = codexIconUrl(assetBase, stat.icon ?: itemRow?.icon),
                name = stat.name.ifBlank { itemRow?.name ?: stat.id },
                avg = stat.avg,
                n = stat.n,
                delta = stat.delta,
                onClick = { onOpenItem(stat.id) },
            )
        }
        if (recommended.builds.isNotEmpty()) {
            Text(
                "3아이템 조합",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            recommended.builds.take(3).forEach { build -> BuildLine(build, ready, assetBase, onOpenItem) }
        }
    }
}

@Composable
private fun BuildLine(
    build: ChampionBuildStat,
    ready: StatsState.Ready,
    assetBase: String,
    onOpenItem: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row {
            build.items.take(3).forEach { itemId ->
                val itemRow = ready.itemsById[itemId]
                // 아이콘은 32dp지만 누르는 영역은 44dp로 넓혀 옆 아이콘과 헷갈리지 않게 한다.
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = "아이템 상세") { onOpenItem(itemId) },
                    contentAlignment = Alignment.Center,
                ) {
                    CodexIcon(codexIconUrl(assetBase, itemRow?.icon), itemRow?.name ?: itemId, 32.dp)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            build.items.joinToString(" + ") { ready.itemsById[it]?.name ?: it },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface,
            maxLines = 3,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(rankText(build.avg), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            Text("${formatCountShort(build.n)} 판", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

/** 1성·2성·3성 칸. (성급, (평균, 게임 수)). */
@Composable
private fun StarStatsRow(stars: List<Pair<Int, Pair<Double?, Int>>>) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stars.sortedBy { it.first }.forEach { (star, value) ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.surfaceVariant)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${star}성", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text(formatAvg(value.first), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                Text("${formatCountShort(value.second)} 판", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}
