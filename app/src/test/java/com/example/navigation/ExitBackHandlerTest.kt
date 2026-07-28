package com.example.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ExitBackHandlerTest {

    @Test
    fun firstPress_showsHint() {
        val result = resolveRootBackPress(
            nowMillis = 10_000L,
            lastBackPressMillis = 0L
        )
        assertEquals(RootBackResult.ShowHint, result)
    }

    @Test
    fun secondPressWithinWindow_showsExitDialog() {
        val first = 10_000L
        val second = first + 1_500L
        val result = resolveRootBackPress(
            nowMillis = second,
            lastBackPressMillis = first
        )
        assertEquals(RootBackResult.ShowExitDialog, result)
    }

    @Test
    fun secondPressAtExactWindow_showsExitDialog() {
        val first = 10_000L
        val second = first + EXIT_BACK_WINDOW_MS
        val result = resolveRootBackPress(
            nowMillis = second,
            lastBackPressMillis = first
        )
        assertEquals(RootBackResult.ShowExitDialog, result)
    }

    @Test
    fun secondPressAfterWindow_showsHintAgain() {
        val first = 10_000L
        val second = first + EXIT_BACK_WINDOW_MS + 1L
        val result = resolveRootBackPress(
            nowMillis = second,
            lastBackPressMillis = first
        )
        assertEquals(RootBackResult.ShowHint, result)
    }

    @Test
    fun expiredWindowThenSecondPress_opensDialog() {
        val first = 10_000L
        val late = first + EXIT_BACK_WINDOW_MS + 500L
        assertEquals(
            RootBackResult.ShowHint,
            resolveRootBackPress(nowMillis = late, lastBackPressMillis = first)
        )
        // After hint, lastBack would be updated to `late`; next press within window opens dialog
        val third = late + 800L
        assertEquals(
            RootBackResult.ShowExitDialog,
            resolveRootBackPress(nowMillis = third, lastBackPressMillis = late)
        )
    }
}
