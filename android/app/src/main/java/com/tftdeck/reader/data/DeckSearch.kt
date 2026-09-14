package com.tftdeck.reader.data

/**
 * 덱 검색. 전부 메모리에서 끝나므로 오프라인에서도 즉시 동작한다.
 *
 * 한국어 사용자가 실제로 치는 방식을 받아준다:
 *   "무한"    부분 일치
 *   "ㅁㅎㅇ"   초성
 *   "무대"    각 어절 첫 글자 줄임말
 *   "infinity" 영문명
 */
class DeckSearch(private val feed: DeckFeed) {

    private val decksById: Map<String, Deck> = feed.decks.associateBy { it.id }

    /** 자동완성 후보. 이름 하나당 한 줄. */
    private val entries: List<Entry> = buildEntries()

    private data class Entry(
        val axis: SearchAxis,
        val name: String,
        val icon: String?,
        val cost: Int?,
        val deckCount: Int,
        val haystack: List<String>,
    )

    private fun buildEntries(): List<Entry> {
        val out = mutableListOf<Entry>()

        fun add(axis: SearchAxis, name: String, icon: String?, cost: Int?, count: Int, nameEn: String?) {
            if (count <= 0) return
            out += Entry(
                axis = axis,
                name = name,
                icon = icon,
                cost = cost,
                deckCount = count,
                haystack = keysFor(name, nameEn),
            )
        }

        val byName = { list: List<CatalogEntry> -> list.associateBy { it.name } }
        val champCat = byName(feed.catalog.champions)
        val itemCat = byName(feed.catalog.items)
        val traitCat = byName(feed.catalog.traits)
        val augCat = byName(feed.catalog.augments)

        feed.index.champion.forEach { (name, decks) ->
            val c = champCat[name]
            add(SearchAxis.CHAMPION, name, c?.icon, c?.cost, decks.size, c?.nameEn)
        }
        feed.index.item.forEach { (name, uses) ->
            val c = itemCat[name]
            add(SearchAxis.ITEM, name, c?.icon, null, uses.map { it.deck }.distinct().size, c?.nameEn)
        }
        feed.index.component.forEach { (name, decks) ->
            val c = itemCat[name]
            add(SearchAxis.COMPONENT, name, c?.icon, null, decks.size, c?.nameEn)
        }
        feed.index.trait.forEach { (name, decks) ->
            val c = traitCat[name]
            add(SearchAxis.TRAIT, name, c?.icon, null, decks.size, c?.nameEn)
        }
        feed.index.augment.forEach { (name, decks) ->
            val c = augCat[name]
            add(SearchAxis.AUGMENT, name, c?.icon, null, decks.size, c?.nameEn)
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
                val score = entry.haystack.minOfOrNull { key -> rank(key, needle) } ?: return@mapNotNull null
                if (score == NO_MATCH) null else entry to score
            }
            // 앞에서 맞은 것 우선, 그다음 덱이 많은 것, 그다음 이름순
            .sortedWith(compareBy({ it.second }, { -it.first.deckCount }, { it.first.name }))
            .take(limit)
            .map { (entry, _) ->
                Suggestion(entry.axis, entry.name, entry.icon, entry.cost, entry.deckCount)
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

    private fun resolve(ids: List<String>?): List<Deck> =
        ids.orEmpty().mapNotNull { decksById[it] }.sortedWith(compareBy({ it.tierOrder }, { it.name }))

    // -- 목록 필터 ----------------------------------------------------------

    fun filter(
        tiers: Set<String> = emptySet(),
        levels: Set<Int> = emptySet(),
        onlyInChinaOnly: Boolean = false,
    ): List<Deck> = feed.decks.filter { deck ->
        (tiers.isEmpty() || deck.tier in tiers) &&
            (levels.isEmpty() || deck.finalLevel in levels) &&
            (!onlyInChinaOnly || deck.metatft.onlyInChina)
    }

    companion object {
        private const val NO_MATCH = Int.MAX_VALUE

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
