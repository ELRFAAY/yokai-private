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

    private var cacheLists: Set<Int> = emptySet()
    private var cacheIds: Set<Long> = emptySet()
    private var cacheAt = 0L

    /** Forget the cached ids (call when a list is hidden / un-hidden). */
    fun invalidate() {
        cacheAt = 0L
    }

    /**
     * Ids of every manga that is in at least one hidden list. Cached for 60 s: Updates / History call this for every
     * page they load, and reading the whole library each time made scrolling down hang.
     */
    suspend fun hiddenMangaIds(
        preferences: PreferencesHelper = Injekt.get(),
        getLibraryManga: GetLibraryManga = Injekt.get(),
    ): Set<Long> {
        val lists = hiddenListIds(preferences)
        if (lists.isEmpty()) return emptySet()
        val now = System.currentTimeMillis()
        if (lists == cacheLists && now - cacheAt < 60_000L) return cacheIds
        val ids = getLibraryManga.await()
            .filter { it.category in lists }
            .mapNotNull { it.manga.id }
            .toSet()
        cacheLists = lists
        cacheIds = ids
        cacheAt = now
        return ids
    }
}
