package com.tftdeck.reader

import com.tftdeck.reader.ingame.GameDetector
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TFT 감지의 사용 기록 조회 구간. 폴링이 늦어져도(기기 잠듦) 그 사이의 일시정지·정지 이벤트를
 * 놓치지 않아야 오버레이가 홈 화면에 굳지 않고 판 종료 감시가 시작된다.
 */
class GameDetectorTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `첫 조회는 두 시간을 넓게 본다`() {
        assertEquals(now - GameDetector.LOOKBACK_MS, GameDetector.queryStart(now, lastQueryEnd = 0L))
    }

    @Test
    fun `평소에는 지난 조회 끝보다 조금 앞에서 이어 읽는다`() {
        val last = now - 3_000L
        assertEquals(last - GameDetector.WINDOW_MS, GameDetector.queryStart(now, last))
    }

    @Test
    fun `폴링이 몇 분 늦어져도 그 사이 구간을 모두 읽는다`() {
        val slept = now - 7 * 60 * 1000L
        assertEquals(slept - GameDetector.WINDOW_MS, GameDetector.queryStart(now, slept))
    }

    @Test
    fun `아주 오래 멈췄으면 두 시간까지만, 시계가 뒤로 가도 최근 창은 읽는다`() {
        assertEquals(now - GameDetector.LOOKBACK_MS, GameDetector.queryStart(now, now - 5 * 60 * 60 * 1000L))
        assertEquals(now - GameDetector.WINDOW_MS, GameDetector.queryStart(now, now + 60_000L))
    }
}
