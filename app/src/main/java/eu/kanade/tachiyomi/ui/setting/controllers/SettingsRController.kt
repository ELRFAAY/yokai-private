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
            title = "Download: tolerate missing page when splitting tall images"
            summary = "On: a missing file during the tall-image split is only logged and the download continues. " +
                "Off: original behavior (the page fails with a \"page not found during splitting\" error)."
        }

        switchPreference {
            bindTo(preferences.rAutoScroll())
            title = "Reader: auto-scroll button (continuous mode)"
            summary = "On: a play button in the reader menu moves the page by itself. Off: original reader (no button)."
        }

        intListPreference(activity) {
            bindTo(preferences.rAutoScrollSpeed())
            title = "Reader: auto-scroll speed"
            entries = listOf("Very slow", "Slow", "Normal", "Fast", "Very fast")
            entryValues = listOf(30, 60, 100, 150, 220)
            defaultValue = 60
        }
    }
}
