package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.database.BabyCareDatabase
import com.example.data.model.ActivityTypes
import com.example.data.repository.BabyCareRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Thin instrumented smoke: cold start → finish onboarding → log bottle → assert DB.
 */
@RunWith(AndroidJUnit4::class)
class BottleLogSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun useAppContext_hasExpectedApplicationId() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.aistudio.babycarelive.bclive", appContext.packageName)
    }

    @Test
    fun coldStart_completeSetup_logBottle() {
        composeRule.waitForIdle()

        // Onboarding may block the dashboard on first launch.
        val setupNext = composeRule.onAllNodesWithTag("setup_next_btn")
        if (setupNext.fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("setup_next_btn").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("setup_next_btn").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("baby_name_input").performTextInput("SmokeBaby")
            composeRule.onNodeWithTag("setup_next_btn").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_action_${ActivityTypes.BOTTLE.lowercase()}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_bottle_log").performClick()
        composeRule.waitForIdle()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bottleCount = runBlocking {
            val repo = BabyCareRepository(
                BabyCareDatabase.getDatabase(context).babyCareDao()
            )
            repo.getRecentLogs(20).count { it.activityType == ActivityTypes.BOTTLE }
        }
        assertTrue("Expected at least one bottle log after smoke flow", bottleCount >= 1)
    }
}
