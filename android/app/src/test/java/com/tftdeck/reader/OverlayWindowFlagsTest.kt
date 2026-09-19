package com.tftdeck.reader

import android.view.WindowManager.LayoutParams as LP
import com.tftdeck.reader.overlay.overlayWindowFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 오버레이 창 플래그 합성. 검색 중이 아닐 때는 언제나 FLAG_NOT_FOCUSABLE 이어야 한다 —
 * 포커스를 쥔 채 남으면 게임의 뒤로 가기·키 입력이 오버레이로 온다.
 */
class OverlayWindowFlagsTest {

    private fun Int.has(flag: Int) = this and flag != 0

    @Test
    fun `검색하지 않을 때는 예전과 같은 플래그다`() {
        assertEquals(LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_NO_LIMITS, overlayWindowFlags(visible = true, searching = false))
        assertEquals(
            LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_NO_LIMITS or LP.FLAG_NOT_TOUCHABLE,
            overlayWindowFlags(visible = false, searching = false),
        )
    }

    @Test
    fun `검색 중에만 포커스를 받고 창 밖 터치는 게임으로 보낸다`() {
        val flags = overlayWindowFlags(visible = true, searching = true)
        assertFalse(flags.has(LP.FLAG_NOT_FOCUSABLE))
        assertTrue(flags.has(LP.FLAG_NOT_TOUCH_MODAL))
        assertTrue(flags.has(LP.FLAG_WATCH_OUTSIDE_TOUCH))
        assertTrue(flags.has(LP.FLAG_LAYOUT_NO_LIMITS))
        assertFalse(flags.has(LP.FLAG_NOT_TOUCHABLE))
    }

    @Test
    fun `숨은 창은 검색 중이어도 포커스와 터치를 받지 않는다`() {
        val flags = overlayWindowFlags(visible = false, searching = true)
        assertTrue(flags.has(LP.FLAG_NOT_FOCUSABLE))
        assertTrue(flags.has(LP.FLAG_NOT_TOUCHABLE))
        assertFalse(flags.has(LP.FLAG_WATCH_OUTSIDE_TOUCH))
    }
}
