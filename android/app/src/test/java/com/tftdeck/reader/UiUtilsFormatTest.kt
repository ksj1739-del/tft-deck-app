package com.tftdeck.reader

import com.tftdeck.reader.ui.bucketLabel
import com.tftdeck.reader.ui.bucketShortLabel
import com.tftdeck.reader.ui.components.formatPick
import com.tftdeck.reader.ui.formatAvg
import com.tftdeck.reader.ui.formatAvgRank
import com.tftdeck.reader.ui.formatDelta
import com.tftdeck.reader.ui.formatGames
import com.tftdeck.reader.ui.formatPct
import com.tftdeck.reader.ui.gradeColor
import com.tftdeck.reader.ui.theme.FloaColors
import com.tftdeck.reader.ui.tierColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 숫자·구간 표기(MASTER 규칙 7·8)와 등급 색 매핑. */
class UiUtilsFormatTest {

    // -- 비율 ------------------------------------------------------------------

    @Test
    fun `비율은 소수 1자리이고 0점1퍼센트 미만은 부등호로 쓴다`() {
        assertEquals("<0.1%", formatPct(0.0004)) // 0.04%
        assertEquals("0.1%", formatPct(0.001)) // 0.1% 경계는 그대로
        assertEquals("12.3%", formatPct(0.12345)) // 12.345%
        assertEquals("58.5%", formatPct(0.585))
        assertEquals("100.0%", formatPct(1.0))
    }

    @Test
    fun `0과 값 없음은 부등호로 바꾸지 않는다`() {
        assertEquals("0.0%", formatPct(0.0))
        assertEquals("-", formatPct(null))
        assertEquals("-", formatPct(Double.NaN))
        assertEquals("-", formatPct(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `예전 호출부의 자리수 지정도 같은 규칙을 따른다`() {
        assertEquals("87%", formatPct(0.87, 0))
        assertEquals("<1%", formatPct(0.004, 0))
        assertEquals("<0.01%", formatPct(0.00004, 2))
    }

    @Test
    fun `픽률은 비율 표기와 같다`() {
        assertEquals("0.2%", formatPick(0.0021))
        assertEquals("<0.1%", formatPick(0.0003))
        assertEquals("14.1%", formatPick(0.141))
        assertEquals("-", formatPick(null))
    }

    // -- 평균 등수 ---------------------------------------------------------------

    @Test
    fun `평균 등수는 소수 2자리이고 문장 안에서는 등을 붙인다`() {
        assertEquals("4.12", formatAvg(4.123))
        assertEquals("4.00", formatAvg(4.0))
        assertEquals("4.12등", formatAvgRank(4.123))
        assertEquals("-", formatAvgRank(null))
        assertEquals("-", formatAvg(Double.NaN))
    }

    // -- 판 수 -----------------------------------------------------------------

    @Test
    fun `판 수는 만 단위 경계에서 표기가 바뀐다`() {
        assertEquals("9,847판", formatGames(9847))
        assertEquals("58.5만 판", formatGames(584959))
        assertEquals("108만 판", formatGames(1080000))
        assertEquals("1.0만 판", formatGames(10_000))
        assertEquals("-", formatGames(null as Int?))
    }

    @Test
    fun `반올림해 100만이 되면 정수 표기로 넘긴다`() {
        assertEquals("99.9만 판", formatGames(999_449L))
        assertEquals("100만 판", formatGames(999_950L))
        assertEquals("1,234만 판", formatGames(12_340_000L))
    }

    // -- 변화량 -----------------------------------------------------------------

    @Test
    fun `변화량의 빼기는 U+2212 이고 방향 기호와 짝을 이룬다`() {
        val minus = formatDelta(-0.13)
        assertEquals("▼ −0.13", minus)
        assertFalse("ASCII 하이픈을 쓰지 않는다", minus.contains('-'))
        assertEquals("▲ +0.07", formatDelta(0.07))
        assertEquals("▼ −24", formatDelta(-24.0, digits = 0))
    }

    @Test
    fun `변화가 없으면 기호 없이 0을 쓴다`() {
        assertEquals("0.00", formatDelta(0.0))
        assertEquals("0.00", formatDelta(-0.001))
        assertEquals("-", formatDelta(null))
    }

    // -- 구간 이름 ---------------------------------------------------------------

    @Test
    fun `low 구간은 실버 이하다`() {
        assertEquals("실버 이하", bucketLabel("low"))
        assertEquals("골드~에메랄드", bucketLabel("goldem"))
    }

    @Test
    fun `좁은 칸용 구간 이름`() {
        assertEquals(
            listOf("전체", "마스터+", "다이아+", "골드~에메", "실버 이하"),
            listOf("all", "master", "diamond", "goldem", "low").map(::bucketShortLabel),
        )
        assertEquals("unknown", bucketShortLabel("unknown"))
    }

    // -- 등급 색 -----------------------------------------------------------------

    @Test
    @Suppress("DEPRECATION")
    fun `편집 등급 색은 더 이상 한 칸 밀리지 않는다`() {
        assertEquals(FloaColors.TierS, gradeColor("SS"))
        assertEquals(FloaColors.TierS, tierColor("S"))
        assertEquals(gradeColor("B"), tierColor("B"))
        assertEquals(FloaColors.NoGrade, gradeColor(null))
    }

    @Test
    fun `등급색은 좋음 나쁨 색과 같은 값을 쓰지 않는다`() {
        assertNotEquals(FloaColors.Positive, FloaColors.TierC)
        assertNotEquals(FloaColors.Negative, FloaColors.TierS)
    }
}
