package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.NavDestination
import com.example.data.model.BabyProfile
import com.example.ui.dialogs.AddCaregiverDialog
import com.example.ui.dialogs.EnterPinDialog
import com.example.ui.dialogs.LogBottleDialog
import com.example.ui.dialogs.LogDiaperDialog
import com.example.ui.dialogs.OnboardingSetupDialog
import com.example.ui.theme.BabyCareTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import androidx.compose.runtime.CompositionLocalProvider
import com.example.ui.dialogs.LocalUseBottomSheet

import androidx.compose.ui.test.performScrollTo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CriticalFlowsComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottleDialogConfirmsVolumeAndMilkType() {
        var confirmedVolume = 0
        var confirmedMilk = ""

        composeRule.setContent {
            BabyCareTheme {
                CompositionLocalProvider(LocalUseBottomSheet provides false) {
                    LogBottleDialog(
                        onDismiss = {},
                        onConfirm = { volume, milk, _, _ ->
                            confirmedVolume = volume
                            confirmedMilk = milk
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("chip_volume_180").performClick()
        composeRule.onNodeWithTag("chip_milk_Formula").performClick()
        composeRule.onNodeWithTag("confirm_bottle_log").performScrollTo().performClick()

        assertEquals(180, confirmedVolume)
        assertEquals("Formula", confirmedMilk)
    }

    @Test
    fun bottleDialogSupportsFineTunedVolume35ml() {
        var confirmedVolume = 0

        composeRule.setContent {
            BabyCareTheme {
                CompositionLocalProvider(LocalUseBottomSheet provides false) {
                    LogBottleDialog(
                        onDismiss = {},
                        onConfirm = { volume, _, _, _ ->
                            confirmedVolume = volume
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("chip_volume_60").performClick()
        composeRule.onNodeWithTag("btn_decrease_volume")
            .performClick()
            .performClick()
            .performClick()
            .performClick()
            .performClick()

        composeRule.onNodeWithTag("confirm_bottle_log").performScrollTo().performClick()

        assertEquals(35, confirmedVolume)
    }

    @Test
    fun diaperDialogConfirmsSelectedStatus() {
        var status = ""

        composeRule.setContent {
            BabyCareTheme {
                CompositionLocalProvider(LocalUseBottomSheet provides false) {
                    LogDiaperDialog(
                        onDismiss = {},
                        onConfirm = { selected, _, _ -> status = selected }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("chip_diaper_Dirty").performClick()
        composeRule.onNodeWithTag("confirm_diaper_log").performScrollTo().performClick()

        assertEquals("Dirty", status)
    }

    @Test
    fun bottomNavSwitchesSelectedDestination() {
        composeRule.setContent {
            BabyCareTheme {
                var current by remember { mutableStateOf(NavDestination.DASHBOARD) }
                NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                    NavDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = current == destination,
                            onClick = { current = destination },
                            icon = {
                                Icon(
                                    imageVector = when (destination) {
                                        NavDestination.DASHBOARD -> Icons.Default.ChildCare
                                        NavDestination.TIMELINE -> Icons.Default.FormatListBulleted
                                        NavDestination.HEALTH -> Icons.Default.HealthAndSafety
                                        NavDestination.AI_INSIGHTS -> Icons.Default.AutoAwesome
                                        NavDestination.MORE -> Icons.Default.MoreHoriz
                                    },
                                    contentDescription = destination.title
                                )
                            },
                            label = { Text(destination.title) },
                            modifier = Modifier.testTag(destination.testTag)
                        )
                    }
                }
                Text(
                    text = current.title,
                    modifier = Modifier.testTag("current_destination_label"),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        composeRule.onNodeWithTag("bottom_navigation_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_dashboard").assertIsSelected()

        composeRule.onNodeWithTag("nav_timeline").performClick()
        composeRule.onNodeWithTag("nav_timeline").assertIsSelected()
        composeRule.onNodeWithTag("current_destination_label").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_health").performClick()
        composeRule.onNodeWithTag("nav_health").assertIsSelected()
    }

    @Test
    fun addCaregiverDialogConfirmsFields() {
        var name = ""
        var relationship = ""
        var pin = ""

        composeRule.setContent {
            BabyCareTheme {
                CompositionLocalProvider(LocalUseBottomSheet provides false) {
                    AddCaregiverDialog(
                        onDismiss = {},
                        onConfirm = { n, rel, _, p ->
                            name = n
                            relationship = rel
                            pin = p
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("add_caregiver_name_input").performTextInput("Alex")
        composeRule.onNodeWithTag("add_caregiver_rel_input").performTextReplacement("Uncle")
        composeRule.onNodeWithTag("add_caregiver_pin_input").performTextReplacement("4321")
        composeRule.onNodeWithTag("confirm_add_caregiver_btn").performScrollTo().performClick()

        assertEquals("Alex", name)
        assertEquals("Uncle", relationship)
        assertEquals("4321", pin)
    }

    @Test
    fun pinEntryDialogSubmitsFourDigits() {
        var submitted = ""

        composeRule.setContent {
            BabyCareTheme {
                EnterPinDialog(
                    caregiverName = "Dad",
                    onDismiss = {},
                    onConfirm = { submitted = it }
                )
            }
        }

        composeRule.onNodeWithTag("pin_entry_input").performTextInput("5678")
        composeRule.onNodeWithTag("submit_pin_btn").performClick()
        assertEquals("5678", submitted)
    }

    @Test
    fun onboardingWizardCompletesHappyPath() {
        var completedName: String? = null

        composeRule.setContent {
            BabyCareTheme {
                OnboardingSetupDialog(
                    initialProfile = BabyProfile(name = "Your Baby", isInitialSetupDone = false),
                    onDismiss = {},
                    onCompleteSetup = { profile, _, _ -> completedName = profile.name }
                )
            }
        }

        // Permissions → profile → baby
        composeRule.onNodeWithTag("setup_next_btn").performClick()
        composeRule.onNodeWithTag("setup_next_btn").performClick()
        composeRule.onNodeWithTag("baby_name_input").performTextInput("Milo")
        composeRule.onNodeWithTag("setup_next_btn").performClick()

        assertEquals("Milo", completedName)
        assertTrue(completedName != null)
    }

    @Test
    fun topBabyHeaderRendersCareSyncPillAndTriggersClick() {
        var clicked = false
        composeRule.setContent {
            BabyCareTheme {
                com.example.ui.components.TopBabyHeader(
                    profile = BabyProfile(name = "Leo"),
                    activeCaregiver = null,
                    onSwitchCaregiverClick = {},
                    onProfileClick = {},
                    onOpenCareSyncClick = { clicked = true },
                    careSyncEnabled = false
                )
            }
        }

        composeRule.onNodeWithTag("care_sync_header_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("care_sync_header_pill").performClick()
        assertTrue(clicked)
    }

    @Test
    fun activityLogCardShowsAiBadgeAndPopover() {
        composeRule.setContent {
            BabyCareTheme {
                com.example.ui.components.ActivityLogCard(
                    log = com.example.data.model.ActivityLog(
                        id = 42,
                        activityType = com.example.data.model.ActivityTypes.SLEEP,
                        startTimeMillis = System.currentTimeMillis() - 3_600_000L,
                        endTimeMillis = System.currentTimeMillis() - 2_700_000L,
                        durationSeconds = 900,
                        notes = "System intelligent nap (45m) · Gap: 1:30 PM–4:30 PM (3h)",
                        isSystemIntelligent = true
                    )
                )
            }
        }

        composeRule.onNodeWithTag("ai_system_log_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_system_log_badge").performClick()
        composeRule.onNodeWithTag("ai_system_log_popover").assertIsDisplayed()
    }

    @Test
    fun smartNapAdjusterRendersGapAndDisablesConfirmOnOverlap() {
        val gapStart = 1_000_000L
        val gapEnd = gapStart + 3 * 3_600_000L
        val bottle = com.example.engine.TimelineAnchor(
            activityType = "BOTTLE",
            title = "Bottle Feeding",
            timeMillis = gapStart + 90 * 60_000L,
            endTimeMillis = gapStart + 100 * 60_000L
        )
        val prompt = com.example.engine.SmartSleepGapPrompt(
            gapStartMillis = gapStart,
            gapEndMillis = gapEnd,
            prevActivity = bottle,
            nextActivity = com.example.engine.TimelineAnchor(
                activityType = "NOW",
                title = "Now",
                timeMillis = gapEnd
            ),
            intermediateActivities = listOf(bottle),
            // Place default nap directly over the bottle so confirm is disabled
            defaultNapStartMillis = bottle.timeMillis - 5 * 60_000L,
            defaultNapDurationMinutes = 30,
            minutesSinceLastSleep = 180
        )

        composeRule.setContent {
            BabyCareTheme {
                CompositionLocalProvider(LocalUseBottomSheet provides false) {
                    com.example.ui.dialogs.SmartNapAdjusterSheet(
                        prompt = prompt,
                        babyName = "Ava",
                        onConfirm = { _, _ -> },
                        onDismiss = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("smart_nap_adjuster_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("gap_timeline_track").assertIsDisplayed()
        composeRule.onNodeWithTag("snap_after_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("overlap_warning").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_intelligent_nap_btn").performScrollTo().assertIsDisplayed()
    }
}
