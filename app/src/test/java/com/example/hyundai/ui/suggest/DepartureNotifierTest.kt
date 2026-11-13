package com.example.hyundai.ui.suggest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 다른 앱이 보낸 [안내 시작] 흉내는 토큰이 없거나 달라서 거절되어야 한다 */
class DepartureNotifierTest {

    @Test
    fun `알림에 넣은 토큰과 같을 때만 통과`() {
        assertTrue(DepartureNotifier.isSameToken("abc", "abc"))
        assertFalse(DepartureNotifier.isSameToken("abc", "abd"))
        assertFalse(DepartureNotifier.isSameToken("abc", null)) // 토큰 없이 보낸 요청
        assertFalse(DepartureNotifier.isSameToken(null, "abc")) // 알림을 띄운 적 없거나 이미 쓴 토큰
        assertFalse(DepartureNotifier.isSameToken(null, null))
    }
}
