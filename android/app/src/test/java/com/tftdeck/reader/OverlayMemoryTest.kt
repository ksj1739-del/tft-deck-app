package com.tftdeck.reader

import android.content.SharedPreferences
import com.tftdeck.reader.overlay.MAX_REMEMBERED_LEVELS
import com.tftdeck.reader.overlay.OverlayListAnchor
import com.tftdeck.reader.overlay.OverlayListStart
import com.tftdeck.reader.overlay.OverlayMemory
import com.tftdeck.reader.overlay.decodeDeckLevels
import com.tftdeck.reader.overlay.encodeDeckLevels
import com.tftdeck.reader.overlay.overlayListStart
import com.tftdeck.reader.overlay.rememberDeckLevel
import com.tftdeck.reader.overlay.resolveOverlayLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 오버레이 '보던 자리'의 복원 규칙. 펼침 화면은 접으면 컴포지션에서 빠지고 서비스는 시스템이 되살리기도 해서,
 * 고른 덱·덱별 레벨·목록 자리는 서비스가 저장값으로 들고 있다가 다시 흘려 보낸다.
 */
class OverlayMemoryTest {

    @Test
    fun `기억한 자리가 없으면 목록 맨 위부터`() {
        assertEquals(OverlayListStart(0, 0), overlayListStart(listOf("a", "b", "c"), null))
    }

    @Test
    fun `맨 위에 보이던 덱을 순서가 바뀐 목록에서도 찾아 그 자리로`() {
        // 구간을 바꿔 순서가 달라져도 칸 번호가 아니라 덱 id 로 찾는다.
        val start = overlayListStart(listOf("x", "c", "a", "b"), OverlayListAnchor("a", 37))
        assertEquals(OverlayListStart(2, 37), start)
    }

    @Test
    fun `그 덱이 목록에서 빠졌으면 맨 위부터`() {
        assertEquals(OverlayListStart(0, 0), overlayListStart(listOf("a", "b"), OverlayListAnchor("gone", 50)))
    }

    @Test
    fun `밀림 값이 음수로 저장돼 있어도 0 으로`() {
        assertEquals(OverlayListStart(1, 0), overlayListStart(listOf("a", "b"), OverlayListAnchor("b", -8)))
    }

    @Test
    fun `고른 레벨이 칩에 있으면 그 레벨`() {
        assertEquals(7, resolveOverlayLevel(saved = 7, levels = listOf(4, 5, 6, 7, 8, 9), fallback = 9))
    }

    @Test
    fun `고른 적 없거나 칩에서 사라진 레벨이면 기본 레벨`() {
        assertEquals(9, resolveOverlayLevel(saved = null, levels = listOf(7, 8, 9), fallback = 9))
        assertEquals(9, resolveOverlayLevel(saved = 4, levels = listOf(7, 8, 9), fallback = 9))
        assertNull(resolveOverlayLevel(saved = 4, levels = emptyList(), fallback = null))
    }

    @Test
    fun `덱별 레벨은 최근에 고른 덱부터 상한까지만 남긴다`() {
        var levels = emptyMap<String, Int>()
        for (i in 0 until MAX_REMEMBERED_LEVELS + 3) levels = rememberDeckLevel(levels, "deck$i", 8)
        assertEquals(MAX_REMEMBERED_LEVELS, levels.size)
        // 가장 먼저 고른 세 덱이 빠진다.
        assertTrue("deck0" !in levels && "deck2" !in levels)
        assertTrue("deck3" in levels && "deck${MAX_REMEMBERED_LEVELS + 2}" in levels)
    }

    @Test
    fun `다시 고른 덱은 최근으로 옮겨 먼저 버려지지 않는다`() {
        var levels = rememberDeckLevel(rememberDeckLevel(emptyMap(), "a", 7), "b", 8)
        levels = rememberDeckLevel(levels, "a", 6)
        assertEquals(listOf("b", "a"), levels.keys.toList())
        assertEquals(6, levels["a"])
        levels = rememberDeckLevel(levels, "c", 9, cap = 2)
        assertEquals(listOf("a", "c"), levels.keys.toList())
    }

    @Test
    fun `같은 레벨을 다시 고르면 저장을 건너뛸 수 있게 같은 값을 돌려준다`() {
        val levels = rememberDeckLevel(emptyMap(), "a", 7)
        assertSame(levels, rememberDeckLevel(levels, "a", 7))
    }

    @Test
    fun `레벨 저장값은 순서까지 되살아나고 깨졌으면 빈 값`() {
        val levels = linkedMapOf("s16_draven" to 9, "g:k=v;x" to 7, "메타 덱" to 6)
        val decoded = decodeDeckLevels(encodeDeckLevels(levels))
        assertEquals(levels, decoded)
        assertEquals(levels.keys.toList(), decoded.keys.toList())
        assertEquals(emptyMap<String, Int>(), decodeDeckLevels(null))
        assertEquals(emptyMap<String, Int>(), decodeDeckLevels("{not json"))
    }

    @Test
    fun `서비스가 다시 만들어져도 보던 덱·레벨·목록 자리가 이어진다`() {
        val prefs = FakePrefs()
        OverlayMemory(prefs).apply {
            selectDeck("draven")
            setLevel("draven", 7)
            setLevel("ashe", 8)
            setListAnchor(OverlayListAnchor("kayn", 42))
        }
        // 시스템이 서비스를 되살린 경우: 같은 저장소로 새로 만든다.
        val restored = OverlayMemory(prefs)
        assertEquals("draven", restored.selectedDeckId.value)
        assertEquals(mapOf("draven" to 7, "ashe" to 8), restored.deckLevels.value)
        assertEquals(OverlayListAnchor("kayn", 42), restored.listAnchor.value)
    }

    @Test
    fun `목록으로 돌아가거나 조건을 바꾸면 그 상태로 저장된다`() {
        val prefs = FakePrefs()
        OverlayMemory(prefs).apply {
            selectDeck("draven")
            setListAnchor(OverlayListAnchor("kayn", 42))
            selectDeck(null) // 뒤로 → 목록
            setListAnchor(null) // 조건이 바뀌어 맨 위부터
        }
        val restored = OverlayMemory(prefs)
        assertNull(restored.selectedDeckId.value)
        assertNull(restored.listAnchor.value)
    }

    /** SharedPreferences 를 메모리에 흉내 낸다. apply·commit 모두 곧바로 반영한다. */
    private class FakePrefs : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = key in values
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
                apply { pending[key] = values }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value }
            override fun remove(key: String): SharedPreferences.Editor = apply { removed += key }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                if (clearAll) values.clear()
                removed.forEach { values.remove(it) }
                pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
                return true
            }
            override fun apply() {
                commit()
            }
        }
    }
}
