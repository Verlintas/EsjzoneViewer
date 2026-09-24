package com.breakyuna.esjzone.ui

import com.breakyuna.esjzone.ui.navigation.BackToExitPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackToExitPolicyTest {

    @Test
    fun firstPressDoesNotExit() {
        assertFalse(BackToExitPolicy.shouldExit(lastPressTime = 0L, currentPressTime = 1000L))
    }

    @Test
    fun secondPressWithinIntervalExits() {
        assertTrue(BackToExitPolicy.shouldExit(lastPressTime = 1000L, currentPressTime = 2000L))
        assertTrue(BackToExitPolicy.shouldExit(lastPressTime = 1000L, currentPressTime = 3000L))
    }

    @Test
    fun secondPressExceedingIntervalDoesNotExit() {
        assertFalse(BackToExitPolicy.shouldExit(lastPressTime = 1000L, currentPressTime = 3001L))
        assertFalse(BackToExitPolicy.shouldExit(lastPressTime = 1000L, currentPressTime = 5000L))
    }

    @Test
    fun clockSkewDoesNotExit() {
        assertFalse(BackToExitPolicy.shouldExit(lastPressTime = 2000L, currentPressTime = 1000L))
    }
}
