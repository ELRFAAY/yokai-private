package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.manga.interactor.GetLibraryManga

/**
 * R: the "hidden files" vault. Library lists (categories) the owner hid are kept in [PreferencesHelper.rHiddenLists];
 * every manga that sits in one of them is hidden from the library, Updates, History and search suggestions
 * until the list is un-hidden again. Nothing is deleted: library refresh still updates these manga.
 */
object RHiddenLists {

    /** Ids of the hidden lists, empty when the R switch is off. */
    fun hiddenListIds(preferences: PreferencesHelper = Injekt.get()): Set<Int> {
        if (!preferences.rHideLists().get()) return emptySet()
        return preferences.rHiddenLists().get().mapNotNull { it.toIntOrNull() }.toSet()
    }

    /** Ids of every manga that is in at least one hidden list. */
    suspend fun hiddenMangaIds(
        preferences: PreferencesHelper = Injekt.get(),
        getLibraryManga: GetLibraryManga = Injekt.get(),
    ): Set<Long> {
        val lists = hiddenListIds(preferences)
        if (lists.isEmpty()) return emptySet()
        return getLibraryManga.await()
            .filter { it.category in lists }
            .mapNotNull { it.manga.id }
            .toSet()
    }
}
