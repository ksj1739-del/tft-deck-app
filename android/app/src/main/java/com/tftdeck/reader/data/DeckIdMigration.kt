package com.tftdeck.reader.data

/**
 * 덱 id 가 바뀐 데이터에서 옛 id → 새 id 대응을 만든다. 고정·숨김·오버레이 덱을 잃지 않게 한다.
 *
 * - lol.qq 그룹이 metatft 조합 덱에 합쳐지면 옛 그룹 id(g-…)는 그 덱의 [Deck.mergedGroups] 에 남는다.
 * - 옛 metatft 전용 덱 id 는 m-{클러스터 번호}였다. 새 조합 덱은 [Deck.metaCluster] 에 그 번호를 들고 있다.
 * 지금 데이터에 그대로 있는 id 는 옮기지 않는다(대응표에 넣지 않는다).
 */
object DeckIdMigration {
    fun mapping(decks: List<Deck>): Map<String, String> {
        val current = decks.mapTo(HashSet()) { it.id }
        val out = LinkedHashMap<String, String>()
        for (deck in decks) {
            for (old in deck.mergedGroups) {
                if (old !in current && old !in out) out[old] = deck.id
            }
            (deck.metaCluster?.toLong() ?: deck.global?.cluster?.takeIf { deck.isMeta })?.let { cluster ->
                val old = "m-$cluster"
                if (old != deck.id && old !in current && old !in out) out[old] = deck.id
            }
        }
        return out
    }
}
