package eu.kanade.tachiyomi.ui.setting.controllers

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.bindTo
import eu.kanade.tachiyomi.ui.setting.intListPreference
import eu.kanade.tachiyomi.ui.setting.switchPreference

/**
 * "R" settings: one switch per modification made in this fork.
 *
 * Rules (see YOKAI_HANDOFF.md):
 * - every modification gets a switch here (key prefix "r_" in PreferencesHelper);
 * - switch ON = the modified behavior (default), switch OFF = the original Yokai behavior;
 * - anything new we add must also have its settings in this screen.
 */
class SettingsRController : SettingsLegacyController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        title = "R"

        switchPreference {
            bindTo(preferences.rFixSplitTallImageError())
            title = "Download: fix page names and tall-image split"
            summary = "On: page files always get English digits (fixes \"page not found during splitting\" on Arabic phones) " +
                "and a missing file during the split is only logged. " +
                "Off: original behavior (device-language digits, the page can fail with that error)."
        }

        switchPreference {
            bindTo(preferences.rAutoScroll())
            title = "Reader: auto-scroll button (continuous mode)"
            summary = "On: a play button in the reader menu moves the page by itself. Off: original reader (no button)."
        }

        switchPreference {
            bindTo(preferences.rSingleAppIcon())
            title = "Appearance: single app icon"
            summary = "On: the icon picker is hidden, the app only has the R logo (reopen Appearance to see it). " +
                "Off: original icon picker."
        }

        switchPreference {
            bindTo(preferences.rMascot())
            title = "Home: snake mascot"
            summary = "On: a small snake floats on the library screen; grab it and throw it. Off: no mascot (original)."
        }

        intListPreference(activity) {
            bindTo(preferences.rAutoScrollSpeed())
            title = "Reader: auto-scroll speed"
            entries = listOf("Very slow", "Slow", "Normal", "Fast", "Very fast")
            entryValues = listOf(30, 60, 100, 150, 220)
        }
    }
}
