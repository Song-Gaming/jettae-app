package com.example.hyundai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GleoCallerTest {

    @Test
    fun gleo_accepted() {
        assertTrue(isGleoCaller("ai.umos.ambientai", hasReferrerExtra = false, allowShell = false))
    }

    @Test
    fun otherAppOrUnknownCaller_rejected() {
        assertFalse(isGleoCaller("com.example.other", hasReferrerExtra = false, allowShell = true))
        assertFalse(isGleoCaller(null, hasReferrerExtra = false, allowShell = true))
    }

    @Test
    fun referrerSetByCaller_rejectedEvenIfItNamesGleo() {
        assertFalse(isGleoCaller("ai.umos.ambientai", hasReferrerExtra = true, allowShell = true))
    }

    @Test
    fun adbShell_acceptedOnlyInDebug() {
        assertTrue(isGleoCaller("com.android.shell", hasReferrerExtra = false, allowShell = true))
        assertFalse(isGleoCaller("com.android.shell", hasReferrerExtra = false, allowShell = false))
    }
}
