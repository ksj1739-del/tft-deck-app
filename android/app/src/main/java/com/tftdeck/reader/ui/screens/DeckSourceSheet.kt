package com.tftdeck.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.DeckFeed
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.ScopeStat
import com.tftdeck.reader.ui.bucketLabel
import com.tftdeck.reader.ui.components.GradeBadge
import com.tftdeck.reader.ui.components.GradeBadgeStyle
import com.tftdeck.reader.ui.components.SectionTitle
import com.tftdeck.reader.ui.components.TextBadge
import com.tftdeck.reader.ui.formatAvgRank
import com.tftdeck.reader.ui.formatGames
import com.tftdeck.reader.ui.formatPct
import com.tftdeck.reader.ui.formatShortDate
import com.tftdeck.reader.ui.placeColor
import com.tftdeck.reader.ui.scopeLabel

/**
 * 덱 상세의 '데이터 출처' 시트(2026-09-19 UX 검토 D1·D2·D8·D15·D18, MASTER 규칙 2).
 * 상세 본문에서 걷어낸 것을 한곳에 모은다: 등급·네 수치의 출처·구간·판 수·기준일, 지역별 성적과 중국 참고치(척도 주의),
 * 섹션마다 어디서 온 자료인지, 배지 뜻, 중국어 원문. 본문 섹션에는 출처 캡션을 달지 않는다.
 * 머리말의 ⓘ 와 상세 맨 아래 '데이터 출처' 줄에서 연다. [bucket] 은 상세에서 고른(저장하지 않는) 구간이다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeckSourceSheet(deck: Deck, bucket: String, feed: DeckFeed?, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Text("데이터 출처", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            StatsBlock(deck, bucket, feed)
            RegionBlock(deck, bucket, feed)
            SectionSourcesBlock(deck, feed)
            BadgeLegend()
            OriginalBlock(deck)
        }
    }
}

/** 등급·네 수치: 어디서, 어느 구간, 몇 판, 언제. 등급을 매기는 방식 한 문장. */
@Composable
private fun StatsBlock(deck: Deck, bucket: String, feed: DeckFeed?) {
    SectionTitle("등급·수치")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SheetText(DeckSourceText.statsLine(deck, bucket, feed), strong = true)
        DeckSourceText.gradeRule(deck, bucket)?.let { SheetText(it) }
        if (!deck.isMeta && !deck.isGlobalOnly && deck.stats.isNotEmpty()) SheetText(DeckSourceText.CHINA_SCALE_NOTE)
        if (deck.isOnlyInChina && deck.metatft.compared) SheetText("metatft 에서는 같은 구성을 찾지 못한 덱입니다")
    }
}

/** 지역별 성적: metatft 스코프 막대(1~8등 칸 폭이 비율) + 중국 참고치. 축 이름은 막대 아래 한 번만(V21). */
@Composable
private fun RegionBlock(deck: Deck, bucket: String, feed: DeckFeed?) {
    val stats = deck.global?.stats.orEmpty()
    val scopes = DeckKeys.SCOPE_ORDER.filter { it in stats }
    val china = DeckSourceText.chinaLines(deck, bucket)
    if (scopes.isEmpty() && china.isEmpty()) return
    val scheme = MaterialTheme.colorScheme

    SectionTitle("지역별 성적")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (scopes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                scopes.forEach { key -> ScopeBar(scopeLabel(key, short = true), stats.getValue(key)) }
                Row(Modifier.fillMaxWidth()) {
                    Text("1등", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("8등", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
                DeckSourceText.metatftPeriod(feed)?.let { SheetText(it) }
            }
        }
        if (china.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("중국 참고", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                china.forEach { SheetText(it) }
                // 중국 한정 덱은 등급·수치 묶음에서 이미 말했다.
                if (deck.isMeta) SheetText(DeckSourceText.CHINA_SCALE_NOTE)
            }
        }
    }
}

@Composable
private fun ScopeBar(label: String, stat: ScopeStat) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, modifier = Modifier.weight(1f))
            Text(
                DeckSourceText.statText(stat.avg, stat.top4, stat.n),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        val places = stat.places.take(8)
        if (places.sum() > 0) {
            // 칸 사이 1dp 틈으로 등수를 가른다(예전의 칸마다 다른 투명도 대신).
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp)),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                places.forEachIndexed { index, count ->
                    if (count > 0) {
                        Box(
                            Modifier
                                .weight(count.toFloat())
                                .fillMaxHeight()
                                .background(placeColor(index + 1)),
                        )
                    }
                }
            }
        }
    }
}

/** 섹션마다 자료가 온 곳. 예전 본문 캡션 아홉 개(D8)를 이 표 하나로 모았다. */
@Composable
private fun SectionSourcesBlock(deck: Deck, feed: DeckFeed?) {
    val rows = remember(deck, feed) { DeckSourceText.sectionSources(deck, feed) }
    if (rows.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    SectionTitle("섹션별 출처")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (section, source) ->
            Row {
                Text(
                    section,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface,
                    modifier = Modifier
                        .width(SECTION_COLUMN)
                        .alignByBaseline(),
                )
                Text(
                    source,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline(),
                )
            }
        }
    }
}

/** 배지 뜻: 실제 배지 모양 그대로 옆에 뜻을 적는다(MASTER 규칙 5). */
@Composable
private fun BadgeLegend() {
    SectionTitle("배지 뜻")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LegendRow(meaning = "metatft 통계 등급") { GradeBadge("S") }
        LegendRow(meaning = "중국 한정 덱 등급(중국 승률 조합 집계)") {
            GradeBadge("S", GradeBadgeStyle.Outlined)
            TextBadge("중국")
        }
        LegendRow(meaning = "편집 덱 작가가 매긴 등급") { GradeBadge("S", GradeBadgeStyle.Editorial) }
        LegendRow(meaning = "이 구간 표본이 300판 미만이거나 등급을 매길 만큼 없음") { TextBadge("표본 적음") }
        LegendRow(meaning = "지난 패치에 쓴 편집 덱") { TextBadge("이전 패치") }
        LegendRow(meaning = "편집 덱 작가가 고른 증강·구성") { TextBadge("작가") }
    }
}

@Composable
private fun LegendRow(meaning: String, badges: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.width(LEGEND_COLUMN),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            badges()
        }
        Text(
            meaning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 작성자 원문(번역 없는 중국어)을 한곳에(D18): 덱 이름·아이템/증강 메모·초반/레벨업 운영 메모·작성자. */
@Composable
private fun OriginalBlock(deck: Deck) {
    val lines = DeckSourceText.originalLines(deck)
    if (lines.isEmpty()) return
    SectionTitle("원문(중국어)")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { SheetText(it) }
    }
}

@Composable
private fun SheetText(text: String, strong: Boolean = false) {
    Text(
        text,
        style = if (strong) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
        color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val SECTION_COLUMN = 112.dp
private val LEGEND_COLUMN = 96.dp

/** 시트 글을 만드는 순수 규칙. 단위 테스트 DeckDetailLogicTest 가 함께 검사한다. */
internal object DeckSourceText {

    /** 중국 승률 조합 집계의 척도 주의(D2). 수집기 주석: 이 목록에는 평균 4등 안쪽 조합만 올라온다. */
    const val CHINA_SCALE_NOTE = "중국 승률 조합 집계는 평균 4등 안쪽 조합만 모은 목록이라 다른 지역보다 좋게 나옵니다"

    private const val AUTHOR = "편집 덱 작가"

    /** '4.14등 · TOP4 59.4% · 45.7만 판'. 표본은 판 수로만 적는다(MASTER 규칙 7). */
    fun statText(avg: Double?, top4: Double?, games: Int): String = listOfNotNull(
        formatAvgRank(avg),
        top4?.let { "TOP4 ${formatPct(it)}" },
        formatGames(games),
    ).joinToString(" · ")

    /**
     * 네 수치의 출처 한 줄. 조합 덱은 'metatft 전 지역 · 최근 3일 · 골드~에메랄드 · 59.2만 판 · 9/19 기준',
     * 중국 한정 덱은 '중국 승률 조합 집계 · 골드~에메랄드 · 3,234판 · 9/19 기준'.
     */
    fun statsLine(deck: Deck, bucket: String, feed: DeckFeed?): String {
        if (deck.isGlobalOnly) {
            val scope = deck.globalDisplayScope ?: return "metatft"
            val stat = deck.global?.stats?.get(scope)
            return listOfNotNull(
                "metatft ${scopeLabel(scope, short = true)}",
                metatftDays(feed),
                stat?.let { formatGames(it.n) },
            ).joinToString(" · ")
        }
        if (deck.stats.isEmpty()) return "편집 덱이라 통계가 없습니다"
        val games = deck.statsFor(bucket)?.let { formatGames(it.n) } ?: "이 구간 자료 없음"
        return if (deck.isMeta) {
            listOfNotNull(
                "metatft 전 지역",
                metatftDays(feed),
                bucketLabel(bucket),
                games,
                metatftDate(feed)?.let { "$it 기준" },
            ).joinToString(" · ")
        } else {
            listOfNotNull(
                "중국 승률 조합 집계",
                bucketLabel(bucket),
                games,
                formatShortDate(feed?.buckets?.get(bucket)?.listDate).takeIf { it.isNotBlank() }?.let { "$it 기준" },
            ).joinToString(" · ")
        }
    }

    /** 등급을 매기는 방식 한 문장. 통계도 편집 등급도 없으면 null. */
    fun gradeRule(deck: Deck, bucket: String): String? = when {
        deck.showsEditorialGrade(bucket) -> "이 구간은 통계 등급이 없어 편집 덱 작가가 매긴 등급을 보여 줍니다"
        deck.isMeta || deck.isGlobalOnly -> "등급은 metatft 와 같은 평균 등수 기준으로 매깁니다"
        deck.stats.isNotEmpty() -> "중국 한정 덱 등급은 같은 구간 중국 한정 덱을 보정 평균 순으로 절반씩 S와 A로 나눕니다"
        else -> null
    }

    /** metatft 스코프 막대의 기간·기준일 'metatft 최근 3일 · 9/19 기준'. */
    fun metatftPeriod(feed: DeckFeed?): String? {
        val parts = listOfNotNull(metatftDays(feed), metatftDate(feed)?.let { "$it 기준" })
        return if (parts.isEmpty()) null else (listOf("metatft") + parts).joinToString(" · ")
    }

    /**
     * 중국 참고치. 조합 덱은 합쳐진 중국 덱의 승률 조합 수치(중국 한정 덱은 이 값이 네 수치라 되풀이하지 않는다),
     * 그리고 둘 다 중국 전체 판 집계(있을 때).
     */
    fun chinaLines(deck: Deck, bucket: String): List<String> {
        val cn = deck.chinaStatsFor(bucket)
        return listOfNotNull(
            cn?.takeIf { deck.isMeta }?.let { "${bucketLabel(bucket)} 승률 조합 · ${statText(it.avg, it.top4, it.n)}" },
            cn?.precise?.takeIf { it.n > 0 && it.avg != null }?.let { precise ->
                "${scopeLabel(precise.scope, short = true)} 전체 판 · ${statText(precise.avg, precise.top4, precise.n)}"
            },
        )
    }

    /** 증강 성적·실측 배치를 받은 구간과 기준일 '중국 골드~에메랄드 9/18'. 구간 정보가 없는 옛 피드는 '중국'. */
    fun detailLabel(deck: Deck, feed: DeckFeed?): String {
        val buckets = feed?.buckets?.takeIf { it.isNotEmpty() } ?: return "중국"
        val key = deck.detailBucket?.takeIf { it in buckets } ?: feed.defaultBucket
        val date = formatShortDate(buckets[key]?.detailDate).takeIf { it.isNotBlank() }
        return listOfNotNull("중국 ${bucketLabel(key)}", date).joinToString(" ")
    }

    /** 섹션 이름 → 자료가 온 곳. 그 섹션이 화면에 없으면 줄도 없다. */
    fun sectionSources(deck: Deck, feed: DeckFeed?): List<Pair<String, String>> = buildList {
        val detail = detailLabel(deck, feed)

        add(
            "캐리·아이템" to when {
                deck.editorial != null -> AUTHOR
                deck.isGlobalOnly || (deck.isMeta && deck.mergedGroups.isEmpty()) -> "metatft 대표 구성"
                else -> "중국 승률 조합 대표 구성"
            }
        )

        val global = deck.buildup?.global?.takeIf { source -> source.levels.any { it.options.isNotEmpty() } }
        val cn = deck.buildup?.cn?.takeIf { source -> source.levels.any { it.options.isNotEmpty() } }
        val levels = listOfNotNull(
            global?.let { source ->
                val days = source.scope?.let { feed?.scopes?.get(it)?.days }?.takeIf { it > 0 }
                listOfNotNull(source.scope?.let { scopeLabel(it, short = true) } ?: "metatft", days?.let { "최근 ${it}일" })
                    .joinToString(" ")
            },
            cn?.let { source -> "중국 " + bucketLabel(source.bucket ?: feed?.defaultBucket ?: DeckKeys.DEFAULT_BUCKET) },
            AUTHOR.takeIf { deck.editorial?.stages.orEmpty().any { it.units.isNotEmpty() && it.level != null } },
        )
        if (levels.isNotEmpty()) add("레벨별 구성" to levels.joinToString(" · "))

        if (deck.global?.finalLevels.orEmpty().isNotEmpty()) {
            add("마무리 레벨" to "metatft · 기간·티어를 밝히지 않은 집계")
        }

        val authored = DeckDetailLogic.authoredAugments(deck) != null
        val fallback = !authored && (deck.augments.recommended.isNotEmpty() || deck.augments.alternatives.isNotEmpty())
        val augments = listOfNotNull(
            AUTHOR.takeIf { authored },
            "$detail · 덱마다 상위 5개".takeIf { deck.augmentStats.isNotEmpty() },
            "중국 승률 조합 추천".takeIf { fallback && deck.augmentStats.isEmpty() },
        )
        if (augments.isNotEmpty()) add("추천 증강" to augments.joinToString(" · "))

        val board = listOfNotNull(
            AUTHOR.takeIf { deck.units.any { it.row != null && it.col != null } },
            "많이 놓는 자리 $detail".takeIf { deck.positions.isNotEmpty() },
        )
        if (board.isNotEmpty()) add("배치" to board.joinToString(" · "))

        if (deck.otherVariants.isNotEmpty()) add("비슷한 구성" to "중국 승률 조합에서 같은 덱으로 묶인 구성")
        if (deck.global?.counters.orEmpty().isNotEmpty()) add("상대하기 어려운 덱" to "metatft")
    }

    /** 작성자 원문 줄. 본문이 하나도 없으면 빈 목록(작성자 이름만으로는 섹션을 만들지 않는다). */
    fun originalLines(deck: Deck): List<String> {
        val notes = deck.buildupNotes
        val body = listOfNotNull(
            deck.nameCn.takeIf { it.isNotBlank() },
            deck.notesCn.items.takeIf { it.isNotBlank() },
            deck.notesCn.augments.takeIf { it.isNotBlank() },
            notes.early?.takeIf { it.isNotBlank() }?.let { "초반 · $it" },
            notes.levelUp?.takeIf { it.isNotBlank() }?.let { "레벨업 · $it" },
        )
        if (body.isEmpty()) return emptyList()
        val author = deck.editorial?.author?.takeIf { it.isNotBlank() } ?: deck.author.takeIf { it.isNotBlank() }
        return body + listOfNotNull(author?.let { "작성 $it" })
    }

    private fun metatftDays(feed: DeckFeed?): String? =
        feed?.scopes?.get(DeckKeys.SCOPE_GLOBAL_PLAT)?.days?.takeIf { it > 0 }?.let { "최근 ${it}일" }

    private fun metatftDate(feed: DeckFeed?): String? {
        val raw = feed?.scopes?.get(DeckKeys.SCOPE_GLOBAL_PLAT)?.updatedAt?.takeIf { it.isNotBlank() }
            ?: feed?.version?.generatedAt
        return formatShortDate(raw).takeIf { it.isNotBlank() }
    }
}
