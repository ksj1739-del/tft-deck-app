package com.tftdeck.reader.data

/**
 * 덱 검색·필터·정렬. 전부 메모리에서 끝나므로 오프라인에서도 즉시 동작한다.
 *
 * 한국어 사용자가 실제로 치는 방식을 받아준다:
 *   "무한"    부분 일치
 *   "ㅁㅎㅇ"   초성
 *   "무대"    각 어절 첫 글자 줄임말
 *   "infinity" 영문명
 */
class DeckSearch(private val feed: DeckFeed) {

    private val decksById: Map<String, Deck> = feed.decks.associateBy { it.id }
    private val catalog = CatalogIndex(feed.catalog)

    /** 자동완성 후보. 이름 하나당 한 줄. */
    private val entries: List<Entry> = buildEntries()

    private data class Entry(
        val axis: SearchAxis,
        val name: String,
        val id: String?,
        val icon: String?,
        val cost: Int?,
        val deckCount: Int,
        val haystack: List<String>,
        /** 증강 설명의 단어. 이름으로 맞은 후보보다 뒤에 오도록 따로 둔다. */
        val descWords: List<String> = emptyList(),
    )

    private fun buildEntries(): List<Entry> {
        val out = mutableListOf<Entry>()

        fun add(axis: SearchAxis, name: String, entry: CatalogEntry?, cost: Int?, count: Int, descWords: List<String> = emptyList()) {
            if (count <= 0) return
            out += Entry(
                axis = axis,
                name = name,
                id = entry?.id,
                icon = entry?.icon,
                cost = cost,
                deckCount = count,
                haystack = keysFor(name, entry?.nameEn),
                descWords = descWords,
            )
        }

        val byName = { list: List<CatalogEntry> -> list.associateBy { it.name } }
        val champCat = byName(feed.catalog.champions)
        val itemCat = byName(feed.catalog.items)
        val traitCat = byName(feed.catalog.traits)
        val augCat = byName(feed.catalog.augments)

        feed.index.champion.forEach { (name, decks) ->
            val c = champCat[name]
            add(SearchAxis.CHAMPION, name, c, c?.cost, decks.size)
        }
        feed.index.item.forEach { (name, uses) ->
            add(SearchAxis.ITEM, name, itemCat[name], null, uses.map { it.deck }.distinct().size)
        }
        feed.index.component.forEach { (name, decks) ->
            add(SearchAxis.COMPONENT, name, itemCat[name], null, decks.size)
        }
        feed.index.trait.forEach { (name, decks) ->
            add(SearchAxis.TRAIT, name, traitCat[name], null, decks.size)
        }
        feed.index.augment.forEach { (name, decks) ->
            val c = augCat[name]
            // 증강은 이름보다 효과로 기억하는 경우가 많다. 설명이 들어오면 단어 단위로 검색에 넣는다.
            add(SearchAxis.AUGMENT, name, c, null, decks.size, descWords(c?.desc))
        }
        return out
    }

    // -- 자동완성 -----------------------------------------------------------

    fun suggest(query: String, limit: Int = 30): List<Suggestion> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val needle = q.lowercase()

        return entries
            .mapNotNull { entry ->
                val byName = entry.haystack.minOfOrNull { key -> rank(key, needle) } ?: NO_MATCH
                val byDesc = entry.descWords.minOfOrNull { word -> rank(word, needle) }
                    ?.takeIf { it != NO_MATCH }?.plus(DESC_PENALTY) ?: NO_MATCH
                val score = minOf(byName, byDesc)
                if (score == NO_MATCH) null else entry to score
            }
            // 앞에서 맞은 것 우선, 그다음 덱이 많은 것, 그다음 이름순
            .sortedWith(compareBy({ it.second }, { -it.first.deckCount }, { it.first.name }))
            .take(limit)
            .map { (entry, _) ->
                Suggestion(entry.axis, entry.name, entry.icon, entry.cost, entry.deckCount, entry.id)
            }
    }

    private fun rank(key: String, needle: String): Int = when {
        key == needle -> 0
        key.startsWith(needle) -> 1
        key.contains(needle) -> 2
        else -> NO_MATCH
    }

    // -- 축별 조회 ----------------------------------------------------------

    /**
     * 아이템 하나가 들어가는 모든 덱. 이 앱의 핵심 기능.
     * 핵심(main)으로 쓰는 덱을 먼저 보여준다.
     */
    fun decksWithItem(itemName: String): List<ItemHit> =
        feed.index.item[itemName].orEmpty()
            .mapNotNull { use ->
                decksById[use.deck]?.let { ItemHit(it, use.unit, use.isCore) }
            }
            .sortedWith(compareBy({ !it.isCore }, { it.deck.tierOrder }, { it.deck.name }))

    fun decksFor(axis: SearchAxis, name: String): List<Deck> = when (axis) {
        SearchAxis.ITEM -> decksWithItem(name).map { it.deck }.distinctBy { it.id }
        SearchAxis.COMPONENT -> resolve(feed.index.component[name])
        SearchAxis.CHAMPION -> resolve(feed.index.champion[name])
        SearchAxis.TRAIT -> resolve(feed.index.trait[name])
        SearchAxis.AUGMENT -> resolve(feed.index.augment[name])
    }

    /**
     * DA id 로 덱을 찾는다(도감 → 덱). byId 인덱스가 없는 옛 피드나 조합 재료 축은
     * catalog 에서 이름을 찾아 이름 인덱스로 떨어진다.
     */
    fun decksForId(axis: SearchAxis, id: String): List<Deck> {
        val ids = when (axis) {
            SearchAxis.CHAMPION -> feed.index.byId.champion[id]
            SearchAxis.ITEM -> feed.index.byId.item[id]
            SearchAxis.TRAIT -> feed.index.byId.trait[id]
            SearchAxis.AUGMENT -> feed.index.byId.augment[id]
            SearchAxis.COMPONENT -> null
        }
        if (ids != null) return resolve(ids)
        val name = nameForId(axis, id) ?: return emptyList()
        return decksFor(axis, name)
    }

    /** catalog id 의 한글 이름. */
    fun nameForId(axis: SearchAxis, id: String): String? = when (axis) {
        SearchAxis.CHAMPION -> catalog.unit(id)?.name
        SearchAxis.ITEM, SearchAxis.COMPONENT -> catalog.items[id]?.name
        SearchAxis.TRAIT -> catalog.traits[id]?.name
        SearchAxis.AUGMENT -> catalog.augments[id]?.name
    }

    private fun resolve(ids: List<String>?): List<Deck> =
        ids.orEmpty().mapNotNull { decksById[it] }.sortedWith(compareBy({ it.tierOrder }, { it.name }))

    // -- 검색 줄(다중 선택 조건) ----------------------------------------------

    /**
     * 조건을 모두 만족하는(AND) 덱만 남긴다. 순서는 [decks] 그대로다. 조건이 없으면 그대로 돌려준다.
     * 목록 화면과 오버레이가 기존 필터·정렬 사이에 끼워 쓴다.
     */
    fun filterByTokens(decks: List<Deck>, tokens: List<DeckToken>): List<Deck> {
        if (tokens.isEmpty()) return decks
        val matches = tokens.map { matchingIds(it) }
        return decks.filter { deck -> matches.all { deck.id in it } }
    }

    /**
     * 조건 하나에 맞는 덱 id.
     * 유닛·시너지·아이템·조합 재료·증강은 [decksFor] 와 같은 이름 인덱스로 찾는다 — 후보에 보인 덱 수와 결과가
     * 어긋나지 않게. 이름으로 못 찾으면 [decksForId] 로 한 번 더 찾는다(피드가 바뀌어 이름이 달라져도 DA id 는 같다).
     * 사용자 지정은 [matchesText] 로 덱 별칭·이름·설명을 본다.
     */
    fun matchingIds(token: DeckToken): Set<String> {
        val axis = token.axis
            ?: return feed.decks.filter { matchesText(it, token.name) }.mapTo(HashSet()) { it.id }
        val byName = if (token.name.isBlank()) emptyList() else decksFor(axis, token.name)
        val found = if (byName.isEmpty() && token.id != null) decksForId(axis, token.id) else byName
        return found.mapTo(HashSet()) { it.id }
    }

    /**
     * 검색 줄 후보. [suggest](초성·줄임말·영문)의 결과에서 이미 고른 조건을 빼고, 맨 끝에 친 글자 그대로의
     * '사용자 지정' 후보를 붙인다. 후보마다 [within] 목록(없으면 전체 덱)에 그 조건을 더했을 때 남는 덱 수를 센다.
     */
    fun suggestTokens(
        query: String,
        selected: List<DeckToken> = emptyList(),
        within: List<Deck>? = null,
        limit: Int = 8,
    ): List<TokenCandidate> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val picked = selected.mapTo(HashSet()) { it.key }
        val base = within ?: feed.decks
        fun count(token: DeckToken): Int = matchingIds(token).let { ids -> base.count { it.id in ids } }

        // 고른 조건을 빼도 [limit] 개가 남도록 그만큼 더 받아 둔다.
        val fromIndex = suggest(q, limit + picked.size)
            .map { DeckToken.of(it) to it }
            .filter { (token, _) -> token.key !in picked }
            .take(limit)
            .map { (token, s) -> TokenCandidate(token, s.icon, s.cost, count(token)) }
        val custom = DeckToken.custom(q)
        return if (custom.key in picked) fromIndex else fromIndex + TokenCandidate(custom, deckCount = count(custom))
    }

    // -- 목록 필터 ----------------------------------------------------------

    /**
     * 덱 목록 필터. [bucket] 을 주면 티어 필터는 그 구간의 등급(없으면 편집 등급)으로 본다.
     * 숨긴 덱은 [showHidden] 일 때만 함께 나온다 — 길게 눌러 복구할 수 있어야 하기 때문이다.
     * [bucket] 을 주면 그 구간 등급이 없는 통계 덱은 뺀다. 고정한 덱처럼 늘 남길 덱은 [alwaysShow] 로 넘긴다.
     */
    fun filter(
        tiers: Set<String> = emptySet(),
        levels: Set<Int> = emptySet(),
        onlyChina: Boolean = false,
        editorialOnly: Boolean = false,
        mainTrait: String? = null,
        hidden: Set<String> = emptySet(),
        showHidden: Boolean = false,
        bucket: String? = null,
        alwaysShow: Set<String> = emptySet(),
    ): List<Deck> = feed.decks.filter { deck ->
        (bucket == null || deck.listedIn(bucket) || deck.id in alwaysShow) &&
            (tiers.isEmpty() || (if (bucket != null) deck.gradeFor(bucket) else deck.tier) in tiers) &&
            (levels.isEmpty() || deck.finalLevel in levels) &&
            (!onlyChina || deck.isOnlyInChina) &&
            (!editorialOnly || deck.isEditorialDeck) &&
            (mainTrait == null || deck.mainTraits.any { it.id == mainTrait }) &&
            (showHidden || deck.id !in hidden)
    }

    companion object {
        private const val NO_MATCH = Int.MAX_VALUE

        /** 설명으로 맞은 후보는 이름으로 맞은 어떤 후보(0~2)보다도 뒤에 온다. */
        private const val DESC_PENALTY = 3

        private const val TREND_UP = "up"

        /** 통계 등급 순서. 편집 등급 SS 가 섞여 들어와도 맨 앞에 두도록 포함한다. */
        private val GRADE_ORDER = listOf("SS", "S", "A", "B", "C", "D")

        /**
         * 정렬. 어느 모드든 마지막 기준은 등급순이라 같은 값끼리 순서가 흔들리지 않는다.
         *  - GRADE: 등급 S→D→없음, 같은 등급은 보정 평균 오름차순
         *  - PICK: 픽률 내림차순
         *  - RISING: 추세 up 먼저, 그다음 평균 등수 변화가 작은(좋아진) 순
         *  - SAMPLE: 표본 내림차순
         *
         * [reversed] 면 그 기준 값이 있는 덱끼리만 순서를 뒤집는다(등급 D→S, 픽률 낮은순, 하락, 표본 적은순).
         * 기준 값이 없는 덱(등급 없는 편집 독립 덱·고정 덱 등)은 어느 방향이든 뒤에 두고,
         * metatft 전용 덱은 lol.qq 덱과 섞지 않고 그 뒤에서 자기들끼리 뒤집는다.
         */
        fun sort(decks: List<Deck>, mode: DeckSortMode, bucket: String, reversed: Boolean = false): List<Deck> {
            val byGrade = gradeComparator(bucket)
            val comparator: Comparator<Deck> = when (mode) {
                DeckSortMode.GRADE -> byGrade
                DeckSortMode.PICK ->
                    compareBy<Deck, Double?>(nullsLast(reverseOrder())) { it.statsFor(bucket)?.pick }
                        .then(byGrade)
                DeckSortMode.RISING ->
                    compareBy<Deck> { it.statsFor(bucket)?.trend != TREND_UP }
                        .thenBy(nullsLast<Double>()) { it.statsFor(bucket)?.avgDiff }
                        .then(byGrade)
                DeckSortMode.SAMPLE ->
                    compareByDescending<Deck> { it.statsFor(bucket)?.n ?: 0 }
                        .then(byGrade)
            }
            val sorted = decks.sortedWith(comparator)
            if (!reversed) return sorted
            val (lolqq, globalOnly) = sorted.partition { !it.isGlobalOnly }
            return flipRanked(lolqq, mode, bucket) + flipRanked(globalOnly, mode, bucket)
        }

        /** 기준 값이 있는 덱만 거꾸로 세우고, 값이 없는 덱은 원래 순서 그대로 뒤에 붙인다. */
        private fun flipRanked(decks: List<Deck>, mode: DeckSortMode, bucket: String): List<Deck> {
            val (ranked, unranked) = decks.partition { hasSortValue(it, mode, bucket) }
            return ranked.asReversed() + unranked
        }

        private fun hasSortValue(deck: Deck, mode: DeckSortMode, bucket: String): Boolean {
            val stats = deck.statsFor(bucket)
            return when (mode) {
                DeckSortMode.GRADE -> (if (deck.isGlobalOnly) deck.globalGrade else stats?.grade) != null
                DeckSortMode.PICK -> stats?.pick != null
                DeckSortMode.RISING -> stats?.avgDiff != null
                DeckSortMode.SAMPLE -> stats != null
            }
        }

        // lol.qq 덱(구간 등급·편집 등급)을 먼저, metatft 전용 덱은 그 뒤에 글로벌 등급·평균 등수 순으로.
        private fun gradeComparator(bucket: String): Comparator<Deck> =
            compareBy<Deck> { it.isGlobalOnly }
                .thenBy { gradeRank(if (it.isGlobalOnly) it.globalGrade else it.statsFor(bucket)?.grade) }
                .thenBy(nullsLast<Double>()) { if (it.isGlobalOnly) it.displayStats(bucket)?.avg else it.statsFor(bucket)?.adjAvg }
                .thenBy { it.tierOrder }
                .thenBy { it.name }

        private fun gradeRank(grade: String?): Int {
            if (grade == null) return GRADE_ORDER.size + 1
            val index = GRADE_ORDER.indexOf(grade.uppercase())
            return if (index >= 0) index else GRADE_ORDER.size
        }

        /** 고정한 덱을 맨 위로. 고정 덱끼리·나머지끼리는 원래 순서를 지킨다. */
        fun pinFirst(decks: List<Deck>, pinned: Set<String>): List<Deck> =
            if (pinned.isEmpty()) decks else decks.filter { it.id in pinned } + decks.filterNot { it.id in pinned }

        /**
         * 오버레이 헤더를 누를 때 넘어갈 다음 구간. [available] 은 피드에 있는 구간 키다.
         * 지금 구간이 목록에 없으면 첫 구간으로 간다.
         */
        fun nextBucket(current: String, available: Collection<String>): String {
            val order = DeckKeys.BUCKET_ORDER.filter { it in available }
            if (order.isEmpty()) return current
            val index = order.indexOf(current)
            return if (index < 0) order.first() else order[(index + 1) % order.size]
        }

        /**
         * 사용자 지정 글자가 덱의 별칭·이름·설명 어딘가에 들어 있는지.
         * 대소문자와 띄어쓰기를 가리지 않고('장로드래곤'도 '장로 드래곤'에 맞는다), 초성·어절 첫 글자 줄임말도 받는다.
         * 별칭·설명은 화면에 보이는 값([Deck.displayAlias]·[Deck.displaySummary])으로 찾는다 — 수집기가 아직 싣지
         * 않은 덱도 보이는 글자로 찾을 수 있어야 한다.
         */
        fun matchesText(deck: Deck, text: String): Boolean {
            val needle = text.trim().lowercase()
            if (needle.isEmpty()) return true
            val compactNeedle = needle.filterNot { it.isWhitespace() }
            return listOf(deck.displayAlias, deck.name, deck.displaySummary).any { field ->
                field.isNotBlank() &&
                    textKeys(field).any { key -> key.contains(needle) || key.contains(compactNeedle) }
            }
        }

        /** 자유 글자(별칭·이름·설명)의 매칭 대상: [keysFor] 에 띄어쓰기를 뺀 글자를 더한다. */
        private fun textKeys(text: String): List<String> =
            (keysFor(text, null) + text.lowercase().filterNot { it.isWhitespace() }).distinct()

        /** 한 이름에 대해 매칭 대상이 되는 문자열들. */
        internal fun keysFor(name: String, nameEn: String?): List<String> = buildList {
            add(name.lowercase())
            nameEn?.lowercase()?.let(::add)
            initials(name)?.let(::add)
            abbreviation(name)?.let(::add)
            // 영문 두문자: "Infinity Edge" -> "ie"
            nameEn?.let { en ->
                val letters = en.split(' ', '.', '\'').filter { it.isNotBlank() }
                if (letters.size > 1) add(letters.joinToString("") { it.first().lowercase() })
            }
        }.distinct()

        /** 설명문을 글자·숫자 덩어리로 자른다. 한 글자짜리 조각은 거의 모든 설명에 걸려서 뺀다. */
        internal fun descWords(desc: String?): List<String> {
            if (desc.isNullOrBlank()) return emptyList()
            val words = mutableListOf<String>()
            val current = StringBuilder()
            for (ch in desc) {
                if (ch.isLetterOrDigit()) {
                    current.append(ch.lowercaseChar())
                } else if (current.isNotEmpty()) {
                    words += current.toString()
                    current.setLength(0)
                }
            }
            if (current.isNotEmpty()) words += current.toString()
            return words.filter { it.length >= 2 }.distinct()
        }

        private const val HANGUL_BASE = 0xAC00
        private const val HANGUL_END = 0xD7A3
        private val CHOSEONG = charArrayOf(
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
        )

        /** "무한의 대검" -> "ㅁㅎㅇㄷㄱ" (공백 제거). 한글이 없으면 null. */
        internal fun initials(name: String): String? {
            val sb = StringBuilder()
            var sawHangul = false
            for (ch in name) {
                when {
                    ch.code in HANGUL_BASE..HANGUL_END -> {
                        sawHangul = true
                        sb.append(CHOSEONG[(ch.code - HANGUL_BASE) / 588])
                    }
                    ch.isWhitespace() -> Unit
                    else -> sb.append(ch.lowercaseChar())
                }
            }
            return if (sawHangul) sb.toString() else null
        }

        /** "무한의 대검" -> "무대". 어절이 하나뿐이면 null. */
        internal fun abbreviation(name: String): String? {
            val words = name.split(' ', '·').filter { it.isNotBlank() }
            if (words.size < 2) return null
            return words.joinToString("") { it.first().toString() }.lowercase()
        }
    }
}

/**
 * 덱 목록 정렬. key 는 기기 설정에 저장하는 값이다.
 * [forwardLabel]·[reversedLabel] 은 고른 칩에 방향까지 적어 보여 줄 이름이다.
 */
enum class DeckSortMode(val key: String, val label: String, val forwardLabel: String, val reversedLabel: String) {
    GRADE("grade", "등급", "등급 S→D", "등급 D→S"),
    PICK("pick", "픽률", "픽률 높은순", "픽률 낮은순"),
    RISING("rising", "상승", "상승", "하락"),
    SAMPLE("sample", "표본", "표본 많은순", "표본 적은순");

    companion object {
        fun fromKey(key: String?): DeckSortMode = entries.firstOrNull { it.key == key } ?: GRADE
    }
}

/** 목록 정렬 설정. 고른 칩을 다시 누르면 방향만 뒤집고, 다른 칩을 누르면 그 정렬의 기본 방향으로 시작한다. */
data class DeckSort(val mode: DeckSortMode = DeckSortMode.GRADE, val reversed: Boolean = false) {
    val label: String get() = if (reversed) mode.reversedLabel else mode.forwardLabel

    fun tapped(tappedMode: DeckSortMode): DeckSort =
        if (tappedMode == mode) copy(reversed = !reversed) else DeckSort(tappedMode)
}
