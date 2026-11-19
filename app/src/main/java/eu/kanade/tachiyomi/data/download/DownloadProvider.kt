package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.injectLazy
import yokai.domain.download.DownloadPreferences
import yokai.domain.storage.StorageManager
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * This class is used to provide the directories where the downloads should be saved.
 * It uses the following path scheme: /<root downloads dir>/<source name>/<manga>/<chapter>
 *
 * @param context the application context.
 */
class DownloadProvider(private val context: Context) {

    /**
     * Preferences helper.
     */
    private val downloadPreferences: DownloadPreferences by injectLazy()
    private val storageManager: StorageManager by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The root directory for downloads.
     */
    private var downloadsDir = storageManager.getDownloadsDirectory()

    init {
        storageManager.changes.onEach {
            downloadsDir = storageManager.getDownloadsDirectory()
        }.launchIn(scope)
    }

    /**
     * Returns the download directory for a manga. For internal use only.
     *
     * @param manga the manga to query.
     * @param source the source of the manga.
     */
    internal fun getMangaDir(manga: Manga, source: Source): UniFile {
        try {
            return downloadsDir?.createDirectory(getSourceDirName(source))!!
                .createDirectory(getMangaDirName(manga))!!
        } catch (e: NullPointerException) {
            throw Exception(context.getString(MR.strings.invalid_download_location))
        }
    }

    /**
     * Returns the download directory for a source if it exists.
     *
     * @param source the source to query.
     */
    fun findSourceDir(source: Source): UniFile? {
        return downloadsDir?.findFile(getSourceDirName(source))
    }

    /**
     * Returns the download directory for a manga if it exists.
     *
     * @param manga the manga to query.
     * @param source the source of the manga.
     */
    fun findMangaDir(manga: Manga, source: Source): UniFile? {
        val sourceDir = findSourceDir(source)
        return sourceDir?.findFile(getMangaDirName(manga))
    }

    /**
     * Returns the download directory for a chapter if it exists.
     *
     * @param chapter the chapter to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    fun findChapterDir(chapter: Chapter, manga: Manga, source: Source): UniFile? {
        val mangaDir = findMangaDir(manga, source)
        return getValidChapterDirNames(chapter).asSequence()
            .mapNotNull { mangaDir?.findFile(it) ?: mangaDir?.findFile("$it.cbz") }
            .firstOrNull()
            ?: mangaDir?.let { findMihonChapterFile(chapter, it) }
    }

    // ---- R: read the chapters downloaded by Mihon ----
    // Mihon ends every chapter folder / .cbz name with "_" + the first 6 characters of md5(chapter url).
    // The rest of its name can differ (padding, scanlator, truncation), so the hash alone identifies the chapter.

    private fun mihonEnabled() = preferences.rMihonFiles().get()

    private fun mihonHashSuffix(chapter: Chapter): String = "_" + Hash.md5(chapter.url).take(6)

    /** True when [name] (a folder name, or a .cbz name) ends with a Mihon hash suffix. */
    private fun endsWithMihonHash(name: String, suffixes: Set<String>): Boolean {
        val base = name.removeSuffix(".cbz")
        return base.length > 7 && base.takeLast(7) in suffixes
    }

    private fun findMihonChapterFile(chapter: Chapter, mangaDir: UniFile): UniFile? {
        if (!mihonEnabled()) return null
        val suffixes = setOf(mihonHashSuffix(chapter))
        return mangaDir.listFiles().orEmpty().firstOrNull { file ->
            val name = file.name ?: return@firstOrNull false
            !name.endsWith(Downloader.TMP_DIR_SUFFIX) && endsWithMihonHash(name, suffixes)
        }
    }

    /** All Mihon-named files of a manga folder, keyed by their hash suffix. Listed once per call. */
    private fun mihonFilesByHash(mangaDir: UniFile): Map<String, UniFile> {
        val map = HashMap<String, UniFile>()
        mangaDir.listFiles().orEmpty().forEach { file ->
            val name = file.name ?: return@forEach
            if (name.endsWith(Downloader.TMP_DIR_SUFFIX)) return@forEach
            val base = name.removeSuffix(".cbz")
            if (base.length > 7 && base[base.length - 7] == '_') map[base.takeLast(7)] = file
        }
        return map
    }

    /** True if one of the cached chapter folder names (without ".cbz") is Mihon's name for [chapter]. */
    fun cacheHasMihonChapter(chapter: Chapter, chapterDirs: Collection<String>): Boolean {
        if (!mihonEnabled()) return false
        val suffix = mihonHashSuffix(chapter)
        return try {
            chapterDirs.any { it.endsWith(suffix) }
        } catch (e: ConcurrentModificationException) {
            false // the cache was being refreshed; the next call will see it
        }
    }

    /** Removes Mihon's name for [chapter] from the cached folder names. */
    fun removeMihonChapter(chapter: Chapter, chapterDirs: MutableSet<String>) {
        if (!mihonEnabled()) return
        val suffix = mihonHashSuffix(chapter)
        chapterDirs.removeAll { it.endsWith(suffix) }
    }

    /**
     * Returns a list of downloaded directories for the chapters that exist.
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    fun findChapterDirs(chapters: List<Chapter>, manga: Manga, source: Source): List<UniFile> {
        val mangaDir = findMangaDir(manga, source) ?: return emptyList()
        val mihonFiles by lazy { mihonFilesByHash(mangaDir) } // only listed if a chapter is not found by its own name
        return chapters.mapNotNull { chapter ->
            getValidChapterDirNames(chapter).map { listOf(it, "$it.cbz") }.flatten().asSequence()
                .mapNotNull { mangaDir.findFile(it) }
                .firstOrNull()
                ?: (if (mihonEnabled()) mihonFiles[mihonHashSuffix(chapter)] else null)
        }
    }

    /**
     * Renames the chapter folders with id's and removes it + null scanlators
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    /*
    fun renameChapters() {
        val sourceManager by injectLazy<SourceManager>()
        val mangas = db.getFavoriteMangas().executeAsBlocking()
        mangas.forEach sfor@{ manga ->
            val sourceId = manga.source
            val source = sourceManager.get(sourceId) ?: return@sfor
            val mangaDir = findMangaDir(manga, source) ?: return@sfor
            mangaDir.listFiles()?.forEach {
                val nameSplit = it.name?.split("_")?.toMutableList() ?: return@sfor
                if (nameSplit.size > 2 && nameSplit.first().first().isDigit()) {
                    nameSplit.removeAt(0)
                    val newName = nameSplit.joinToString("_").removePrefix("null_")
                    it.renameTo(newName)
                }
            }
        }
    }
    */

    fun renameMangaFolder(from: String, to: String, sourceId: Long) {
        val sourceManager by injectLazy<SourceManager>()
        val source = sourceManager.get(sourceId) ?: return
        val sourceDir = findSourceDir(source)
        val mangaDir = sourceDir?.findFile(DiskUtil.buildValidFilename(from))
        mangaDir?.renameTo(to)
    }

    /**
     * Returns a list of all files in manga directory
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    fun findUnmatchedChapterDirs(
        chapters: List<Chapter>,
        manga: Manga,
        source: Source,
    ): List<UniFile> {
        val mangaDir = findMangaDir(manga, source) ?: return emptyList()
        val chapterNameHashSet = chapters.map { it.name }.toHashSet()
        val scanlatorNameHashSet = chapters.map {
            getChapterDirName(it)
            getChapterDirName(it, includeId = downloadPreferences.downloadWithId().get())
        }.toHashSet()
        val scanlatorCbzNameHashSet = chapters.map {
            "${getChapterDirName(it)}.cbz"
            "${getChapterDirName(it, includeId = downloadPreferences.downloadWithId().get())}.cbz"
        }.toHashSet()

        val mihonSuffixes: Set<String> =
            if (mihonEnabled()) chapters.map { mihonHashSuffix(it) }.toHashSet() else emptySet()

        return mangaDir.listFiles().orEmpty().asList().filter { file ->
            file.name?.let { fileName ->
                if (fileName.endsWith(Downloader.TMP_DIR_SUFFIX)) {
                    return@filter true
                }
                // R: a download made by Mihon belongs to a chapter, it is not an orphan
                if (mihonSuffixes.isNotEmpty() && endsWithMihonHash(fileName, mihonSuffixes)) {
                    return@filter false
                }
                // check this first because this is the normal name format
                if (scanlatorNameHashSet.contains(fileName)) {
                    return@filter false
                }
                if (scanlatorCbzNameHashSet.contains(fileName)) {
                    return@filter false
                }

                val afterScanlatorCheck = fileName.substringAfter("_")
                // check both these don't exist because who knows how a chapter name is and it might not trim scanlator correctly
                return@filter !chapterNameHashSet.contains(fileName) && !chapterNameHashSet.contains(afterScanlatorCheck)
            }
            // everything else is considered true
            return@filter true
        }
    }

    /**
     * Returns a list of downloaded directories for the chapters that exist.
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    fun findTempChapterDirs(chapters: List<Chapter>, manga: Manga, source: Source): List<UniFile> {
        val mangaDir = findMangaDir(manga, source) ?: return emptyList()
        return chapters.mapNotNull {
            mangaDir.findFile("${getChapterDirName(it, includeId = downloadPreferences.downloadWithId().get())}_tmp")
        }
    }

    /**
     * Returns the download directory name for a source.
     *
     * @param source the source to query.
     */
    fun getSourceDirName(source: Source): String {
        return source.toString()
    }

    /**
     * Returns the download directory name for a manga.
     *
     * @param manga the manga to query.
     */
    fun getMangaDirName(manga: Manga): String {
        return DiskUtil.buildValidFilename(manga.originalTitle)
    }

    /**
     * Returns the chapter directory name for a chapter.
     *
     * @param chapter the chapter to query.
     */
    fun getChapterDirName(chapter: Chapter, includeBlank: Boolean = false, includeId: Boolean = false): String {
        return DiskUtil.buildValidFilename(
            if (!chapter.scanlator.isNullOrBlank()) {
                "${chapter.scanlator}_${chapter.name}"
            } else {
                (if (includeBlank) "_" else "") + chapter.name
            } + (if (includeId) "_${chapter.id}" else ""),
        )
    }

    /**
     * Returns valid downloaded chapter directory names.
     *
     * @param chapter the chapter to query.
     */
    fun getValidChapterDirNames(chapter: Chapter): List<String> {
        return buildList {
            add(getChapterDirName(chapter))
            add(getChapterDirName(chapter, includeBlank = true))

            add(getChapterDirName(chapter, includeBlank = false, includeId = true))
            add(getChapterDirName(chapter, includeBlank = true, includeId = true))

            // Legacy chapter directory name used in v0.8.4 and before
            add(DiskUtil.buildValidFilename(chapter.name))

            // Mihon names folders "<scanlator>_<name>_<first 6 chars of md5(chapter url)>" when its
            // "chapter name hash" option is on, so look for those too (works for .cbz as well).
            val hashSuffix = "_" + Hash.md5(chapter.url).take(6)
            val baseNames = toList()
            baseNames.forEach { add(it + hashSuffix) }
        }.distinct()
    }
}
