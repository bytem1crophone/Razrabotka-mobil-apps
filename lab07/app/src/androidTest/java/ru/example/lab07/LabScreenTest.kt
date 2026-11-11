package ru.example.lab07

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LabScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun initialScreenShowsDefaultAddressAndNoSelectedFile() {
        composeRule.onNodeWithTag("addressInput")
            .performScrollTo()
            .assertTextContains("https://developer.android.com")
        composeRule.onNodeWithTag("filePreview")
            .performScrollTo()
            .assertTextContains("Файл не выбран")
    }

    @Test
    fun invalidAddressShowsErrorWithoutLeavingTheScreen() {
        composeRule.onNodeWithTag("addressInput")
            .performScrollTo()
            .performTextReplacement("http://example.org")
        closeSoftKeyboard()
        composeRule.onNodeWithTag("openWebsiteButton")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("statusMessage")
            .performScrollTo()
            .assertTextContains("Введите корректный HTTPS-адрес")
            .assertIsDisplayed()
    }

    @Test
    fun noteAndAddressSurviveActivityRecreation() {
        val note = "Заметка для лабораторной № 7\nВторая строка сохраняется."
        val address = "https://developer.android.com/guide/components/activities"
        composeRule.onNodeWithTag("noteInput")
            .performScrollTo()
            .performTextReplacement(note)
        closeSoftKeyboard()
        composeRule.onNodeWithTag("addressInput")
            .performScrollTo()
            .performTextReplacement(address)
        closeSoftKeyboard()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag("noteInput")
            .performScrollTo()
            .assertTextContains(note)
        composeRule.onNodeWithTag("addressInput")
            .performScrollTo()
            .assertTextContains(address)
    }
}
