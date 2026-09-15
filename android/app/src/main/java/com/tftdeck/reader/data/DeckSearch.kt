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

    // -- 목록 필터 ----------------------------------------------------------

    /**
     * 덱 목록 필터. [bucket] 을 주면 티어 필터는 그 구간의 등급(없으면 편집 등급)으로 본다.
     * 숨긴 덱은 [showHidden] 일 때만 함께 나온다 — 길게 눌러 복구할 수 있어야 하기 때문이다.
     * [bucket] 을 주면 그 구간에 기록이 없는 통계 덱은 뺀다. 고정한 덱처럼 늘 남길 덱은 [alwaysShow] 로 넘긴다.
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
        (bucket == null || deck.appearsIn(bucket) || deck.id in alwaysShow) &&
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
         */
        fun sort(decks: List<Deck>, mode: DeckSortMode, bucket: String): List<Deck> {
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
            return decks.sortedWith(comparator)
        }

        private fun gradeComparator(bucket: String): Comparator<Deck> =
            compareBy<Deck> { gradeRank(it.statsFor(bucket)?.grade) }
                .thenBy(nullsLast<Double>()) { it.statsFor(bucket)?.adjAvg }
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

/** 덱 목록 정렬. key 는 기기 설정에 저장하는 값이다. */
enum class DeckSortMode(val key: String, val label: String) {
    GRADE("grade", "등급"),
    PICK("pick", "픽률"),
    RISING("rising", "상승"),
    SAMPLE("sample", "표본");

    companion object {
        fun fromKey(key: String?): DeckSortMode = entries.firstOrNull { it.key == key } ?: GRADE
    }
}
