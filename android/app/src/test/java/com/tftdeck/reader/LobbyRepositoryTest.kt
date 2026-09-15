package com.tftdeck.reader

import com.tftdeck.reader.data.LP_PAIR_WINDOW_MS
import com.tftdeck.reader.data.RatingChange
import com.tftdeck.reader.data.RatingSnapshot
import com.tftdeck.reader.data.firstObservations
import com.tftdeck.reader.data.formatLpDelta
import com.tftdeck.reader.data.lpDeltaFor
import com.tftdeck.reader.data.metatftJson
import com.tftdeck.reader.data.pairLpChanges
import com.tftdeck.reader.data.parseMetatftTime
import com.tftdeck.reader.data.parseRatingChanges
import com.tftdeck.reader.ingame.LastLobby
import com.tftdeck.reader.ingame.detectGameEnd
import com.tftdeck.reader.ingame.lobbyTierText
import com.tftdeck.reader.ingame.parseFlags
import com.tftdeck.reader.ingame.parseLatestMatchRef
import com.tftdeck.reader.ingame.parseLobbyMatch
import com.tftdeck.reader.ingame.parseSpectate
import com.tftdeck.reader.ingame.resultText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 게임 연동의 파서와 판정 규칙.
 *
 * 샘플은 실제 metatft 응답(2026-09-15)에서 이름·태그를 가짜로 바꾸고 puuid·MMR을 지운 것이다.
 * 기대값은 같은 원본으로 규칙을 먼저 계산해 둔 값이다(최근 8판 LP: -1, -35, +36, 0, 0, -15, -10, +10).
 */
class LobbyRepositoryTest {

    private fun resource(path: String): String {
        javaClass.classLoader?.getResourceAsStream(path)?.let { stream ->
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        // 단위 테스트의 작업 디렉터리는 모듈 폴더(android/app)다.
        val file = File("src/test/resources/$path")
        assertTrue("테스트 리소스가 없다: ${file.absolutePath}", file.exists())
        return file.readText(Charsets.UTF_8)
    }

    private val matchBody by lazy { resource("ingame/match_sample.json") }
    private val ratingBody by lazy { resource("ingame/rating_changes_sample.json") }

    // -- 지난 게임 로비 --------------------------------------------------------

    @Test
    fun `경기 JSON에서 8명의 로비를 등수순으로 만든다`() {
        val lobby = parseLobbyMatch(matchBody, "KR_fallback", "Tester#KR1")
        assertNotNull(lobby)
        lobby!!
        assertEquals("KR_1000000001", lobby.matchId)
        assertEquals(1789471371596L, lobby.endedAt)
        assertEquals(8, lobby.players.size)
        assertEquals((1..8).toList(), lobby.players.map { it.placement })
        assertEquals(listOf(9, 9, 9, 7, 8, 9, 8, 7), lobby.players.map { it.level })
    }

    @Test
    fun `내 행은 라이엇 ID를 대소문자와 앞뒤 공백 없이 찾는다`() {
        val lobby = parseLobbyMatch(matchBody, "", "  tester # kr1 ")!!
        val mine = lobby.players.filter { it.isMe }
        assertEquals(1, mine.size)
        assertEquals(6, mine.single().placement)
        assertEquals(mine.single(), lobby.me)
        // 이름 가운데 공백은 그대로 둔다.
        assertTrue(lobby.players.any { it.riotId == "Lazy Panda#KR1" && !it.isMe })
    }

    @Test
    fun `티어는 한글로 바꾸고 LP를 붙인다`() {
        val lobby = parseLobbyMatch(matchBody, "", "Tester#KR1")!!
        assertEquals("다이아몬드 IV 42 LP", lobby.players[0].tierText)
        assertEquals("에메랄드 I 10 LP", lobby.players[1].tierText)
        assertEquals("다이아몬드 IV 1 LP", lobby.players[5].tierText)
        assertEquals("다이아몬드 III 10 LP", lobby.players[7].tierText)
        assertEquals("", lobbyTierText(""))
        assertEquals("마스터 I 83 LP", lobbyTierText("MASTER I 83 LP"))
        assertEquals("UNKNOWN 3", lobbyTierText("UNKNOWN 3"))
    }

    @Test
    fun `티어 목록 순서가 달라도 라이엇 ID로 짝을 맞추고 없는 사람은 비워 둔다`() {
        val body = """
            {"metadata":{"match_id":"KR_9"},
             "info":{"game_datetime":5,"participants":[
               {"riotIdGameName":"B","riotIdTagline":"KR1","placement":2,"level":8},
               {"riotIdGameName":"A","riotIdTagline":"KR1","placement":1,"level":9}]},
             "_metatft":{"participant_info":[
               {"riot_id":"C#KR1","ranked":null},
               {"riot_id":"a#kr1","ranked":{"rating_text":"GOLD I 5 LP"}}]}}
        """.trimIndent()
        val lobby = parseLobbyMatch(body, "", "B#KR1")!!
        assertEquals(listOf("A#KR1", "B#KR1"), lobby.players.map { it.riotId })
        assertEquals("골드 I 5 LP", lobby.players[0].tierText)
        assertEquals("", lobby.players[1].tierText)
        assertTrue(lobby.players[1].isMe)
    }

    @Test
    fun `캐시로 저장하는 로비에는 puuid가 남지 않는다`() {
        val lobby = parseLobbyMatch(matchBody, "", "Tester#KR1")!!
        val encoded = metatftJson.encodeToString(LastLobby.serializer(), lobby)
        assertFalse("puuid가 캐시에 들어갔다", encoded.contains("puuid"))
        assertFalse(encoded.contains("MASKED"))
        assertEquals(lobby, metatftJson.decodeFromString(LastLobby.serializer(), encoded))
    }

    @Test
    fun `lookup 응답에서 가장 최근 경기 참조를 읽는다`() {
        val body = """
            {"summoner":{"riot_id":"Tester#KR1"},"matches":[
              {"riot_match_id":"KR_1","match_data_url":"https://matches3.metatft.com/KR_1.json","placement":1,"match_timestamp":1000},
              {"riot_match_id":"KR_2","match_data_url":"https://matches3.metatft.com/KR_2.json","placement":6,"match_timestamp":2000}]}
        """.trimIndent()
        val ref = parseLatestMatchRef(body)!!
        assertEquals("KR_2", ref.matchId)
        assertEquals("https://matches3.metatft.com/KR_2.json", ref.matchDataUrl)
        assertEquals(6, ref.placement)
        assertEquals(2000L, ref.matchTimestamp)
        assertNull(parseLatestMatchRef("""{"matches":[]}"""))
    }

    // -- rating_changes 짝짓기 ---------------------------------------------------

    @Test
    fun `rating_changes를 파싱하고 시각은 UTC로 읽는다`() {
        val snapshot = parseRatingChanges(ratingBody, fetchedAt = 1L)!!
        assertEquals(112, snapshot.numGames)
        assertEquals(2400, snapshot.ratingNumeric)
        assertEquals("DIAMOND IV 0 LP", snapshot.ratingText)
        assertEquals(15, snapshot.changes.size)
        assertEquals(1L, snapshot.fetchedAt)
        assertEquals(1789471339493L, parseMetatftTime("2026-09-15T11:22:19.493"))
        assertEquals(1789471339493L, parseMetatftTime("2026-09-15T11:22:19.493500"))
        assertEquals(1789471339493L, parseMetatftTime("2026-09-15T11:22:19.493Z"))
        assertEquals(0L, parseMetatftTime("어제"))
    }

    @Test
    fun `같은 판 수가 두 번 기록되면 처음 기록 하나만 쓴다`() {
        val snapshot = parseRatingChanges(ratingBody)!!
        val firsts = firstObservations(snapshot.changes)
        assertEquals(firsts.map { it.numGames }.distinct(), firsts.map { it.numGames })
        assertEquals(14, firsts.size)
        assertEquals(1787835669036L, firsts.single { it.numGames == 5 }.createdAt)
        // 6번째 판: 6판 첫 기록(1424) − 5판 마지막 기록(1412)
        assertEquals(12, lpDeltaFor(snapshot.changes, 6))
    }

    @Test
    fun `최근 경기마다 15분 안의 LP 기록을 짝짓는다`() {
        val snapshot = parseRatingChanges(ratingBody)!!
        val lp = pairLpChanges(RECENT_MATCH_TIMES, snapshot.changes)
        assertEquals(listOf<Int?>(-1, -35, 36, 0, 0, -15, -10, 10), lp)
    }

    @Test
    fun `15분을 벗어나면 짝을 짓지 않는다`() {
        val changes = listOf(RatingChange(10, 1500, T0), RatingChange(11, 1530, T0 + 40 * MIN))
        // 11판 기록보다 16분 앞선 경기: 짝 없음
        assertEquals(listOf<Int?>(null), pairLpChanges(listOf(T0 + 24 * MIN), changes))
        // 14분 뒤: 짝 있음
        assertEquals(listOf<Int?>(30), pairLpChanges(listOf(T0 + 54 * MIN), changes))
        // 정확히 15분은 안쪽으로 본다.
        assertEquals(listOf<Int?>(30), pairLpChanges(listOf(T0 + 40 * MIN + LP_PAIR_WINDOW_MS), changes))
    }

    @Test
    fun `한 기록은 한 경기에만 붙고 판 수가 건너뛰면 LP를 비운다`() {
        val single = listOf(RatingChange(30, 2100, T0), RatingChange(31, 2140, T0 + 45 * MIN))
        // 두 경기 모두 31판 기록 근처 → 더 가까운 첫 경기에만 붙는다.
        assertEquals(listOf<Int?>(40, null), pairLpChanges(listOf(T0 + 46 * MIN, T0 + 40 * MIN), single))

        val skipped = listOf(RatingChange(20, 2000, T0), RatingChange(22, 2050, T0 + 60 * MIN))
        assertNull(lpDeltaFor(skipped, 22))
        assertEquals(listOf<Int?>(null), pairLpChanges(listOf(T0 + 62 * MIN), skipped))
    }

    // -- 판 종료 판정 --------------------------------------------------------------

    @Test
    fun `판 수가 늘면 판 종료로 보고 LP 변화를 계산한다`() {
        val end = detectGameEnd(BEFORE, AFTER)
        assertNotNull(end)
        end!!
        assertEquals(112, end.numGames)
        assertEquals(1, end.gamesPlayed)
        assertEquals(-1, end.lpDelta)
        assertEquals(T0 + 120 * MIN, end.observedAt)
        assertEquals("6등 −1 LP", resultText(6, end.lpDelta))
    }

    @Test
    fun `판 수가 같으면 LP가 바뀌어도 판 종료가 아니다`() {
        assertNull(detectGameEnd(BEFORE, BEFORE.copy(ratingNumeric = 2390, fetchedAt = T0 + 90 * MIN)))
        assertNull(detectGameEnd(AFTER, BEFORE))
    }

    @Test
    fun `직전 판 기록이 없으면 한 판일 때만 점수 차이를 쓴다`() {
        val base = RatingSnapshot(50, 1800, "", emptyList())
        val one = RatingSnapshot(51, 1835, "", listOf(RatingChange(51, 1835, T0)))
        assertEquals(35, detectGameEnd(base, one)!!.lpDelta)

        val two = RatingSnapshot(52, 1850, "", listOf(RatingChange(52, 1850, T0)))
        val end = detectGameEnd(base, two)!!
        assertEquals(2, end.gamesPlayed)
        assertNull(end.lpDelta)

        assertEquals("게임 종료", resultText(null, null))
        assertEquals("3등", resultText(3, null))
        assertEquals("+36 LP", resultText(null, 36))
        assertEquals("±0", formatLpDelta(0))
    }

    // -- 진행 중 게임(기본 비활성) ------------------------------------------------------

    @Test
    fun `관전 응답이 비었거나 모양이 다르면 무시한다`() {
        assertNull(parseSpectate("{}", "me"))
        assertNull(parseSpectate("""{"gameMode":"CLASSIC","participants":[{"puuid":"me"}]}""", "me"))
        assertNull(parseSpectate("""{"participants":[{"puuid":"me"}]}""", "me"))
        val lobby = parseSpectate("""{"gameMode":"TFT","participants":[{"puuid":"a"},{"puuid":"me"}]}""", "me")!!
        assertEquals(2, lobby.participantCount)
        assertTrue(lobby.includesMe)
        assertFalse(parseFlags("""{"liveSpectateAvailable": false, "checkedAt": "2026-09-15T20:00:00Z"}""").liveSpectateAvailable)
    }

    private companion object {
        const val MIN = 60_000L
        const val T0 = 1_789_000_000_000L

        /** metatft lookup 샘플(2026-09-15)의 최근 8판 종료 시각. 등수는 6, 7, 2, 7, 7, 7, 5, 4. */
        val RECENT_MATCH_TIMES = listOf(
            1789471371596L, 1789464051118L, 1789398102072L, 1789396223427L,
            1789394178616L, 1789386454612L, 1789378890831L, 1789303738743L,
        )

        val BEFORE = RatingSnapshot(
            numGames = 111,
            ratingNumeric = 2401,
            ratingText = "DIAMOND IV 1 LP",
            changes = listOf(RatingChange(110, 2436, T0), RatingChange(111, 2401, T0 + 60 * MIN)),
            fetchedAt = T0 + 61 * MIN,
        )

        val AFTER = BEFORE.copy(
            numGames = 112,
            ratingNumeric = 2400,
            ratingText = "DIAMOND IV 0 LP",
            changes = BEFORE.changes + RatingChange(112, 2400, T0 + 120 * MIN),
            fetchedAt = T0 + 125 * MIN,
        )
    }
}
