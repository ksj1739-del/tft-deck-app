package com.tftdeck.reader

import com.tftdeck.reader.overlay.OverlayPosition
import com.tftdeck.reader.overlay.clampOverlayPosition
import com.tftdeck.reader.overlay.hideHeaderWhileSearching
import com.tftdeck.reader.overlay.liftAboveIme
import com.tftdeck.reader.overlay.overlayPositionKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 오버레이 창 자리 보정. 창에 FLAG_LAYOUT_NO_LIMITS 가 있어 시스템이 화면 안으로 밀어 주지 않으므로
 * 끌기·커짐·회전·저장값 복원 모두 이 규칙을 거쳐야 창을 잃어버리지 않는다.
 * 영역 수치는 에뮬레이터(1080x2400, 420dpi)의 dumpsys window parent 틀이다.
 */
class OverlayPlacementTest {

    // 세로: parent=[0,136][1080,2337] → 1080 x 2201. 가로: parent=[136,74][2400,1017] → 2264 x 943.
    private val portraitW = 1080
    private val portraitH = 2201
    private val landW = 2264
    private val landH = 943

    @Test
    fun `화면 안의 자리는 그대로 둔다`() {
        val pos = OverlayPosition(0, 120)
        assertEquals(pos, clampOverlayPosition(pos, width = 166, height = 111, areaWidth = portraitW, areaHeight = portraitH))
    }

    @Test
    fun `오른쪽·아래로 끌려 나간 창은 화면 끝에 붙인다`() {
        // 접힌 칩(166x111px)을 화면 밖 오른쪽 아래로 끌었을 때.
        val placed = clampOverlayPosition(OverlayPosition(1500, 3000), 166, 111, portraitW, portraitH)
        assertEquals(OverlayPosition(portraitW - 166, portraitH - 111), placed)
    }

    @Test
    fun `왼쪽·위로 넘어간 자리는 0 으로`() {
        assertEquals(OverlayPosition(0, 0), clampOverlayPosition(OverlayPosition(-40, -9), 166, 111, portraitW, portraitH))
    }

    @Test
    fun `펼쳐서 커진 창은 넘치는 만큼만 안으로 민다`() {
        // 오른쪽 아래 모서리에 둔 칩을 펼침: 패널 840x978px.
        val corner = OverlayPosition(portraitW - 166, portraitH - 111)
        val placed = clampOverlayPosition(corner, 840, 978, portraitW, portraitH)
        assertEquals(OverlayPosition(portraitW - 840, portraitH - 978), placed)
    }

    @Test
    fun `가로 화면에서 기본 자리의 펼친 목록은 위로 올려 바닥이 잘리지 않게 한다`() {
        // 에뮬레이터에서 본 결함: y=120 에 높이 943px 창 → 틀 바닥이 화면(1080)을 넘었다.
        val placed = clampOverlayPosition(OverlayPosition(0, 120), 788, 943, landW, landH)
        assertEquals(OverlayPosition(0, 0), placed)
    }

    @Test
    fun `창이 영역보다 크면 머리줄이 보이게 위·왼쪽에 붙인다`() {
        val placed = clampOverlayPosition(OverlayPosition(300, 500), 3000, 1200, landW, landH)
        assertEquals(OverlayPosition(0, 0), placed)
    }

    @Test
    fun `세로에서 저장한 아래쪽 자리를 가로에서 되찾으면 화면 안으로 들어온다`() {
        val savedInPortrait = OverlayPosition(900, 2000)
        val placed = clampOverlayPosition(savedInPortrait, 166, 111, landW, landH)
        assertEquals(OverlayPosition(900, landH - 111), placed)
    }

    @Test
    fun `키보드가 가린 만큼 창을 올린다`() {
        assertEquals(200, liftAboveIme(y = 500, imeOverlap = 300))
    }

    @Test
    fun `키보드 위 공간이 창보다 작으면 화면 위에서 멈춘다`() {
        assertEquals(0, liftAboveIme(y = 120, imeOverlap = 700))
        assertEquals(0, liftAboveIme(y = 0, imeOverlap = 150))
    }

    @Test
    fun `가리지 않으면 움직이지 않고 내리지도 않는다`() {
        assertEquals(500, liftAboveIme(y = 500, imeOverlap = 0))
        assertEquals(500, liftAboveIme(y = 500, imeOverlap = -20))
    }

    @Test
    fun `자리는 방향별로 저장하고 세로는 예전 키를 그대로 쓴다`() {
        assertEquals("x" to "y", overlayPositionKeys(landscape = false))
        val land = overlayPositionKeys(landscape = true)
        assertNotEquals("x", land.first)
        assertNotEquals("y", land.second)
        assertNotEquals(land.first, land.second)
    }

    @Test
    fun `가로 폰 높이에서만 검색 중 머리줄을 접는다`() {
        assertTrue(hideHeaderWhileSearching(359f)) // 시스템 바가 보이는 가로
        assertTrue(hideHeaderWhileSearching(411f)) // 몰입 모드 가로
        assertFalse(hideHeaderWhileSearching(838f)) // 세로
    }
}
