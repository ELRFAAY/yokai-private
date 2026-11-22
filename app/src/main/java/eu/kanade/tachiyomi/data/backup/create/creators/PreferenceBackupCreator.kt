package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.preferenceKey
import eu.kanade.tachiyomi.source.sourcePreferences
import eu.kanade.tachiyomi.ui.library.LibrarySort
import android.app.Application
import android.util.Base64
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.runBlocking
import java.io.File
import yokai.domain.category.interactor.GetCategories
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PreferenceBackupCreator(
    private val sourceManager: SourceManager = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) {
    fun createApp(includePrivatePreferences: Boolean): List<BackupPreference> {
        return (preferenceStore.getAll().toBackupPreferences() + rExtras())
            .withPrivatePreferences(includePrivatePreferences)
    }

    /**
     * R: things the plain preference dump cannot carry over to another install:
     * - the hidden lists, by list NAME (category ids change after a restore);
     * - the profile pictures (the preferences only hold file paths): the image files themselves.
     */
    private fun rExtras(): List<BackupPreference> {
        val out = mutableListOf<BackupPreference>()
        try {
            val prefs: PreferencesHelper = Injekt.get()
            val ids = prefs.rHiddenLists().get().mapNotNull { it.toIntOrNull() }.toSet()
            if (ids.isNotEmpty()) {
                val names = runBlocking { Injekt.get<GetCategories>().await() }
                    .filter { it.id in ids }
                    .map { it.name }
                    .toSet()
                if (names.isNotEmpty()) out += BackupPreference(R_HIDDEN_NAMES, StringSetPreferenceValue(names))
            }
            val ctx: Application = Injekt.get()
            val dir = File(ctx.filesDir, "r_profile")
            var total = 0L
            dir.listFiles()?.filter { it.isFile && it.length() in 1..8_000_000L }?.forEach { f ->
                if (total + f.length() > 40_000_000L) return@forEach
                total += f.length()
                out += BackupPreference(
                    R_FILE_PREFIX + f.name,
                    StringPreferenceValue(Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)),
                )
            }
            // pinned manga + custom covers are keyed by manga id, which changes after a restore: save them by source + url
            val all = runBlocking { Injekt.get<yokai.domain.manga.MangaRepository>().getMangaList() }
            val pinnedIds = Injekt.get<yokai.domain.library.LibraryPreferences>().pinnedManga().get()
            val pinned = all.filter { it.id?.toString() in pinnedIds }.map { "${it.source}|${it.url}" }.toSet()
            if (pinned.isNotEmpty()) out += BackupPreference(R_PINNED, StringSetPreferenceValue(pinned))
            val covers = Injekt.get<eu.kanade.tachiyomi.data.cache.CoverCache>()
            var coverTotal = 0L
            all.forEach { m ->
                val f = covers.getCustomCoverFile(m)
                if (f.exists() && f.length() > 0 && coverTotal + f.length() <= 60_000_000L) {
                    coverTotal += f.length()
                    out += BackupPreference(
                        R_COVER_PREFIX + "${m.source}|${m.url}",
                        StringPreferenceValue(Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)),
                    )
                }
            }
        } catch (e: Exception) {
            co.touchlab.kermit.Logger.e(e) { "R backup extras failed" }
        }
        return out
    }

    fun createSource(includePrivatePreferences: Boolean): List<BackupSourcePreferences> {
        return sourceManager.getOnlineSources()
            .filterIsInstance<ConfigurableSource>()
            .map {
                BackupSourcePreferences(
                    it.preferenceKey(),
                    it.sourcePreferences().all.toBackupPreferences()
                        .withPrivatePreferences(includePrivatePreferences),
                )
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, *>.toBackupPreferences(): List<BackupPreference> {
        return this.filterKeys { !Preference.isAppState(it) }
            .mapNotNull { (key, value) ->
                // j2k fork differences
                if (key == "library_sorting_mode" && value is Int) {
                    val stringValue = (LibrarySort.valueOf(value) ?: LibrarySort.Title).serialize()
                    return@mapNotNull BackupPreference(key, StringPreferenceValue(stringValue))
                }
                // end j2k fork differences
                when (value) {
                    is Int -> BackupPreference(key, IntPreferenceValue(value))
                    is Long -> BackupPreference(key, LongPreferenceValue(value))
                    is Float -> BackupPreference(key, FloatPreferenceValue(value))
                    is String -> BackupPreference(key, StringPreferenceValue(value))
                    is Boolean -> BackupPreference(key, BooleanPreferenceValue(value))
                    is Set<*> -> (value as? Set<String>)?.let {
                        BackupPreference(key, StringSetPreferenceValue(it))
                    }
                    else -> null
                }
            }
    }

    private fun List<BackupPreference>.withPrivatePreferences(include: Boolean) =
        if (include) {
            this
        } else {
            this.filter { !Preference.isPrivate(it.key) }
        }
}

/** Backup keys written by [PreferenceBackupCreator.rExtras] and read back by the restorer. */
const val R_HIDDEN_NAMES = "r_backup_hidden_list_names"
const val R_FILE_PREFIX = "r_backup_file:"
const val R_PINNED = "r_backup_pinned_manga" // set of "sourceId|url"
const val R_COVER_PREFIX = "r_backup_cover:" // + "sourceId|url" = base64 of the manga's custom cover image
