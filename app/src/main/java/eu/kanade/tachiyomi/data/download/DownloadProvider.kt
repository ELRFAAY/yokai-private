package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.database.models.Chapter
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
    private val rMihonFolders: Boolean
        get() = uy.kohesive.injekt.Injekt.get<eu.kanade.tachiyomi.data.preference.PreferencesHelper>().rMihonFolders().get()

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
            // reuse a folder that already exists under any of its known names (e.g. one written by Mihon)
            val sourceDir = findSourceDir(source) ?: downloadsDir?.createDirectory(getSourceDirName(source))!!
            return findMangaDirIn(sourceDir, manga) ?: sourceDir.createDirectory(getMangaDirName(manga))!!
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
        return getSourceDirNames(source).firstNotNullOfOrNull { downloadsDir?.findFile(it) }
    }

    /**
     * Returns the download directory for a manga if it exists.
     *
     * @param manga the manga to query.
     * @param source the source of the manga.
     */
    fun findMangaDir(manga: Manga, source: Source): UniFile? {
        val sourceDir = findSourceDir(source)
        return sourceDir?.let { findMangaDirIn(it, manga) }
    }

    private fun findMangaDirIn(sourceDir: UniFile, manga: Manga): UniFile? {
        return getMangaDirNames(manga).firstNotNullOfOrNull { sourceDir.findFile(it) }
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
        return chapters.mapNotNull { chapter ->
            getValidChapterDirNames(chapter).map { listOf(it, "$it.cbz") }.flatten().asSequence()
                .mapNotNull { mangaDir.findFile(it) }
                .firstOrNull()
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

        return mangaDir.listFiles().orEmpty().asList().filter { file ->
            file.name?.let { fileName ->
                if (fileName.endsWith(Downloader.TMP_DIR_SUFFIX)) {
                    return@filter true
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
     * Every name the source folder may have on disk: the plain one, the sanitized one, and the one
     * Mihon writes when its "disallow non-ASCII filenames" option is on.
     */
    fun getSourceDirNames(source: Source): List<String> {
        val raw = source.toString()
        if (!rMihonFolders) return listOf(raw)
        return listOf(raw, DiskUtil.buildValidFilename(raw), asciiHexName(raw)).distinct()
    }

    /** Every name the manga folder may have on disk (plain, or Mihon's non-ASCII-as-hex form). */
    fun getMangaDirNames(manga: Manga): List<String> {
        val title = manga.originalTitle
        if (!rMihonFolders) return listOf(DiskUtil.buildValidFilename(title))
        return listOf(DiskUtil.buildValidFilename(title), asciiHexName(title)).distinct()
    }

    /**
     * Mihon's "disallow non-ASCII filenames" writes every non-ASCII character as the lowercase hex of
     * its UTF-8 bytes, e.g. the apostrophe in "Academy's" becomes "e28099". This builds that same name.
     */
    fun asciiHexName(name: String): String {
        val sb = StringBuilder(name.length)
        name.forEach { c ->
            if (c.code >= 0x80) {
                c.toString().toByteArray(Charsets.UTF_8).forEach { sb.append("%02x".format(it)) }
            } else {
                sb.append(c)
            }
        }
        return DiskUtil.buildValidFilename(sb.toString())
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
            if (rMihonFolders) {
                val hashSuffix = "_" + Hash.md5(chapter.url).take(6)
                val baseNames = toList()
                baseNames.forEach { add(it + hashSuffix) }
            }
        }.let { names ->
            if (!rMihonFolders) return@let names.distinct()
            // ...and the same names the way Mihon writes them with non-ASCII characters turned into hex
            (names + names.map { asciiHexName(it) }).distinct()
        }
    }
}
