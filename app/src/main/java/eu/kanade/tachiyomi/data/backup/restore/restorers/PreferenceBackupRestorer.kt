package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import android.util.Base64
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.backup.create.creators.R_FILE_PREFIX
import eu.kanade.tachiyomi.data.backup.create.creators.R_COVER_PREFIX
import eu.kanade.tachiyomi.data.backup.create.creators.R_HIDDEN_NAMES
import eu.kanade.tachiyomi.data.backup.create.creators.R_PINNED
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.runBlocking
import java.io.File
import yokai.domain.category.interactor.GetCategories
import eu.kanade.tachiyomi.core.preference.AndroidPreferenceStore
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.preference.getEnum
import eu.kanade.tachiyomi.data.backup.create.BackupCreatorJob
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.source.sourcePreferences
import eu.kanade.tachiyomi.ui.library.LibrarySort
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.base.BasePreferences
import yokai.domain.ui.settings.ReaderPreferences

class PreferenceBackupRestorer(
    private val context: Context,
    private val preferenceStore: PreferenceStore = Injekt.get(),
) {
    fun restoreAppPreferences(preferences: List<BackupPreference>, onComplete: () -> Unit) {
        val plain = preferences.filter {
            it.key != R_HIDDEN_NAMES && it.key != R_PINNED && it.key != "library_pinned_manga" &&
                !it.key.startsWith(R_FILE_PREFIX) && !it.key.startsWith(R_COVER_PREFIX)
        }
        restorePreferences(plain, preferenceStore)
        restoreRExtras(preferences)

        ExtensionUpdateJob.setupTask(context)
        LibraryUpdateJob.setupTask(context)
        BackupCreatorJob.setupTask(context)

        onComplete()
    }

    /** R: after the manga are restored (new ids): pinned manga and custom covers are matched by source + url. */
    fun restoreRPostManga(all: List<BackupPreference>) {
        try {
            val getManga = Injekt.get<yokai.domain.manga.interactor.GetManga>()
            fun find(key: String) = key.split('|', limit = 2).takeIf { it.size == 2 }?.let { (s, u) ->
                s.toLongOrNull()?.let { src -> runBlocking { getManga.awaitByUrlAndSource(u, src) } }
            }
            val pinned = (all.firstOrNull { it.key == R_PINNED }?.value as? StringSetPreferenceValue)?.value
            if (pinned != null) {
                val ids = pinned.mapNotNull { find(it)?.id?.toString() }.toSet()
                Injekt.get<yokai.domain.library.LibraryPreferences>().pinnedManga().set(ids)
            }
            val covers = Injekt.get<eu.kanade.tachiyomi.data.cache.CoverCache>()
            all.filter { it.key.startsWith(R_COVER_PREFIX) }.forEach { p ->
                val v = (p.value as? StringPreferenceValue)?.value ?: return@forEach
                val manga = find(p.key.removePrefix(R_COVER_PREFIX)) ?: return@forEach
                covers.setCustomCoverToCache(manga, Base64.decode(v, Base64.NO_WRAP).inputStream())
            }
        } catch (e: Exception) {
            Logger.e(e) { "R restore post-manga failed" }
        }
    }

    /** R: puts the profile pictures back (and points the profile at them) and re-finds the hidden lists by name. */
    private fun restoreRExtras(all: List<BackupPreference>) {
        try {
            val prefs: PreferencesHelper = Injekt.get()
            val files = all.filter { it.key.startsWith(R_FILE_PREFIX) }
            if (files.isNotEmpty()) {
                val dir = File(context.filesDir, "r_profile").apply { mkdirs() }
                files.forEach { p ->
                    val v = (p.value as? StringPreferenceValue)?.value ?: return@forEach
                    File(dir, p.key.removePrefix(R_FILE_PREFIX)).writeBytes(Base64.decode(v, Base64.NO_WRAP))
                }
                // the saved paths belong to the old install: keep the file names, use this install's folder
                fun fix(path: String) = File(dir, File(path).name).takeIf { it.exists() }?.absolutePath
                fix(prefs.rProfileAvatar().get())?.let { prefs.rProfileAvatar().set(it) }
                prefs.rProfileBanners().set(
                    prefs.rProfileBanners().get().split('\n').filter { it.isNotBlank() }
                        .mapNotNull { fix(it) }.joinToString("\n"),
                )
            }
            val names = (all.firstOrNull { it.key == R_HIDDEN_NAMES }?.value as? StringSetPreferenceValue)?.value
            if (names != null) {
                val ids = runBlocking { Injekt.get<GetCategories>().await() }
                    .filter { it.name in names }
                    .mapNotNull { it.id?.toString() }
                    .toSet()
                prefs.rHiddenLists().set(ids)
            }
        } catch (e: Exception) {
            Logger.e(e) { "R restore extras failed" }
        }
    }

    fun restoreSourcePreferences(preferences: List<BackupSourcePreferences>, onComplete: () -> Unit) {
        preferences.forEach {
            val sourcePrefs = AndroidPreferenceStore(context, sourcePreferences(it.sourceKey))
            restorePreferences(it.prefs, sourcePrefs)
        }
    }

    private fun restorePreferences(
        toRestore: List<BackupPreference>,
        preferenceStore: PreferenceStore,
    ) {
        val prefs = preferenceStore.getAll()
        toRestore.forEach { (key, value) ->
            // j2k fork differences
            if (key == "library_sorting_mode" && value is StringPreferenceValue &&
                prefs[key] is Int?
            ) {
                val intValue = LibrarySort.deserialize(value.value)
                preferenceStore.getInt(key).set(intValue.mainValue)
                return@forEach
            }
            // end j2k fork differences

            // << Yokai-J2K compat
            if (key == "extension_installer" && value is IntPreferenceValue) {
                val enum = BasePreferences.ExtensionInstaller.migrate(value.value)
                preferenceStore.getEnum(key, enum).set(enum)
                return@forEach
            }

            if (key == PreferenceKeys.pagerCutoutBehavior && value is IntPreferenceValue) {
                val enum = ReaderPreferences.CutoutBehaviour.migrate(value.value)
                preferenceStore.getEnum(key, enum).set(enum)
                return@forEach
            }

            if (key == "landscape_cutout_behavior" && value is IntPreferenceValue) {
                val enum = ReaderPreferences.LandscapeCutoutBehaviour.migrate(value.value)
                preferenceStore.getEnum(key, enum).set(enum)
                return@forEach
            }
            // >> Yokai-J2K compat

            when (value) {
                is IntPreferenceValue -> {
                    if (prefs[key] is Int?) {
                        preferenceStore.getInt(key).set(value.value)
                    }
                }
                is LongPreferenceValue -> {
                    if (prefs[key] is Long?) {
                        preferenceStore.getLong(key).set(value.value)
                    }
                }
                is FloatPreferenceValue -> {
                    if (prefs[key] is Float?) {
                        preferenceStore.getFloat(key).set(value.value)
                    }
                }
                is StringPreferenceValue -> {
                    if (prefs[key] is String?) {
                        preferenceStore.getString(key).set(value.value)
                    }
                }
                is BooleanPreferenceValue -> {
                    if (prefs[key] is Boolean?) {
                        preferenceStore.getBoolean(key).set(value.value)
                    }
                }
                is StringSetPreferenceValue -> {
                    if (prefs[key] is Set<*>?) {
                        preferenceStore.getStringSet(key).set(value.value)
                    }
                }
            }
        }
    }
}
