package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.injectLazy
import yokai.domain.library.LibraryPreferences

/** Small helper around the "pinned manga" preference (pinned manga are listed first in the library). */
object PinnedManga {
    private val libraryPreferences: LibraryPreferences by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    /** R switch r_pin_manga: when it is off, pinning does nothing and the library behaves as the original. */
    val enabled: Boolean get() = preferences.rPinManga().get()

    fun isPinned(mangaId: Long?): Boolean =
        enabled && mangaId != null && libraryPreferences.pinnedManga().get().contains(mangaId.toString())

    /** A pin emoji to put in front of a pinned manga's title (empty when it is not pinned). */
    fun titlePrefix(mangaId: Long?): String = ""
}
