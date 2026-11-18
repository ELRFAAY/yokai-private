package eu.kanade.tachiyomi.ui.library

import uy.kohesive.injekt.injectLazy
import yokai.domain.library.LibraryPreferences

/** Small helper around the "pinned manga" preference (pinned manga are listed first in the library). */
object PinnedManga {
    private val libraryPreferences: LibraryPreferences by injectLazy()

    fun isPinned(mangaId: Long?): Boolean =
        mangaId != null && libraryPreferences.pinnedManga().get().contains(mangaId.toString())

    /** A pin emoji to put in front of a pinned manga's title (empty when it is not pinned). */
    fun titlePrefix(mangaId: Long?): String = if (isPinned(mangaId)) "\uD83D\uDCCC " else ""
}
