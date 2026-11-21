package eu.kanade.tachiyomi.ui.more.stats

import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.MANGA_HAS_UNREAD
import eu.kanade.tachiyomi.data.preference.MANGA_NON_COMPLETED
import eu.kanade.tachiyomi.data.preference.MANGA_NON_READ
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper.getReadDuration
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.data.DatabaseHandler
import yokai.domain.manga.interactor.GetLibraryManga
import yokai.domain.track.interactor.GetTrack
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Presenter of [StatsController].
 */
class StatsPresenter(
    private val prefs: PreferencesHelper = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
): BaseCoroutinePresenter<StatsController>() {
    private val handler: DatabaseHandler by injectLazy()
    private val getLibraryManga: GetLibraryManga by injectLazy()
    private val getTrack: GetTrack by injectLazy()

    private val libraryMangas = getLibrary()
    val mangaDistinct = libraryMangas.distinctBy { it.manga.id } // a manga in two lists must count once

    private fun getLibrary(): MutableList<LibraryManga> {
        return runBlocking { getLibraryManga.await() }.toMutableList()
    }

    fun getTracks(manga: Manga): MutableList<Track> {
        return runBlocking { getTrack.awaitAllByMangaId(manga.id) }.toMutableList()
    }

    fun getLoggedTrackers(): List<TrackService> {
        return trackManager.services.filter { it.isLogged }
    }

    fun getSources(): List<CatalogueSource> {
        val languages = prefs.enabledLanguages().get()
        val hiddenCatalogues = prefs.hiddenSources().get()
        return sourceManager.getCatalogueSources()
            .filter { it.lang in languages }
            .filterNot { it.id.toString() in hiddenCatalogues }
    }

    fun getGlobalUpdateManga(): Map<Long?, List<LibraryManga>> {
        val includedCategories = prefs.libraryUpdateCategories().get().map(String::toInt)
        val excludedCategories = prefs.libraryUpdateCategoriesExclude().get().map(String::toInt)
        val restrictions = prefs.libraryUpdateMangaRestriction().get()
        return libraryMangas.groupBy { it.manga.id }
            .filterNot { it.value.any { manga -> manga.category in excludedCategories } }
            .filter { includedCategories.isEmpty() || it.value.any { manga -> manga.category in includedCategories } }
            .filterNot {
                val manga = it.value.first()
                (MANGA_NON_COMPLETED in restrictions && manga.manga.status == SManga.COMPLETED) ||
                    (MANGA_HAS_UNREAD in restrictions && manga.unread != 0) ||
                    (MANGA_NON_READ in restrictions && manga.totalChapters > 0 && !manga.hasRead)
            }
    }

    fun getDownloadCount(manga: LibraryManga): Int {
        return downloadManager.getDownloadCount(manga.manga)
    }

    fun get10PointScore(track: Track): Float? {
        val service = trackManager.getService(track.sync_id)
        return service?.get10PointScore(track.score)
    }

    /** R: chapters read per day for the last 7 days (today last), counted from the history, vault manga included. */
    fun getWeeklyChapters(): List<Pair<String, Int>> {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -6)
        }
        val start = cal.timeInMillis
        val end = System.currentTimeMillis()
        val rows = runBlocking {
            handler.awaitList { historyQueries.getPerPeriod(start, end, MangaChapterHistory::mapper) }
        }
        val fmt = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
        return (0 until 7).map { i ->
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 24L * 60 * 60 * 1000
            val count = rows.count { it.history.last_read in dayStart until dayEnd }
            val label = fmt.format(java.util.Date(dayStart))
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            label to count
        }
    }

    /** R: everything the extra stats sections need, from one pass over the whole history (vault manga included). */
    fun getRData(): RStatsData {
        val now = System.currentTimeMillis()
        val rows = runBlocking {
            handler.awaitList { historyQueries.getPerPeriod(1L, now, MangaChapterHistory::mapper) }
        }
        val zone = java.time.ZoneId.systemDefault()
        fun dayOf(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = java.time.LocalDate.now()
        val todayEpoch = today.toEpochDay()
        val days = rows.map { dayOf(it.history.last_read).toEpochDay() }.toSortedSet()

        var best = 0
        var run = 0
        var prev = Long.MIN_VALUE
        for (d in days) {
            run = if (d == prev + 1) run + 1 else 1
            if (run > best) best = run
            prev = d
        }
        var cur = 0
        var d = if (todayEpoch in days) todayEpoch else todayEpoch - 1
        while (d in days) {
            cur++
            d--
        }

        val byManga = rows.groupBy { it.manga.id }
        val top = byManga.values.map { list ->
            RTopManga(
                list.first().manga,
                list.mapNotNull { it.chapter.id }.distinct().size,
                list.sumOf { it.history.time_read },
            )
        }.sortedWith(compareByDescending<RTopManga> { it.timeMs }.thenByDescending { it.chapters }).take(5)

        val hours = IntArray(24)
        rows.forEach { hours[java.time.Instant.ofEpochMilli(it.history.last_read).atZone(zone).hour]++ }

        val thisMonth = java.time.YearMonth.from(today)
        val lastMonth = thisMonth.minusMonths(1)
        val monthNow = rows.count { java.time.YearMonth.from(dayOf(it.history.last_read)) == thisMonth }
        val monthPrev = rows.count { java.time.YearMonth.from(dayOf(it.history.last_read)) == lastMonth }
        val week = rows.count { dayOf(it.history.last_read).toEpochDay() >= todayEpoch - 6 }
        val last30 = rows.count { dayOf(it.history.last_read).toEpochDay() >= todayEpoch - 29 }

        val genreCount = HashMap<String, Int>()
        val sourceCount = HashMap<String, Int>()
        byManga.values.forEach { list ->
            val weight = list.mapNotNull { it.chapter.id }.distinct().size.coerceAtLeast(1)
            val manga = list.first().manga
            manga.getGenres()?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()?.forEach {
                genreCount[it] = (genreCount[it] ?: 0) + weight
            }
            val src = runCatching { sourceManager.getOrStub(manga.source).name }.getOrDefault("?")
            sourceCount[src] = (sourceCount[src] ?: 0) + weight
        }
        fun List<Map.Entry<String, Int>>.topWithRest(n: Int): List<Pair<String, Int>> {
            val sorted = sortedByDescending { it.value }
            val head = sorted.take(n).map { it.key to it.value }
            val rest = sorted.drop(n).sumOf { it.value }
            return if (rest > 0) head + ("غيرها" to rest) else head
        }
        return RStatsData(
            streakNow = cur,
            streakBest = best,
            top = top,
            hours = hours,
            monthNow = monthNow,
            monthPrev = monthPrev,
            weekChapters = week,
            genres = genreCount.entries.toList().topWithRest(6),
            sources = sourceCount.entries.toList().topWithRest(6),
            dailyAvg = last30 / 30f,
        )
    }

    fun getReadDuration(): String {
        val chaptersTime = runBlocking {
            handler.awaitOneOrNull { historyQueries.getTotalReadDuration() }?.sum?.toLong()
        }
        return chaptersTime.getReadDuration(prefs.context.getString(MR.strings.none))
    }
}

class RTopManga(val manga: Manga, val chapters: Int, val timeMs: Long)

class RStatsData(
    val streakNow: Int,
    val streakBest: Int,
    val top: List<RTopManga>,
    val hours: IntArray,
    val monthNow: Int,
    val monthPrev: Int,
    val weekChapters: Int,
    val genres: List<Pair<String, Int>>,
    val sources: List<Pair<String, Int>>,
    val dailyAvg: Float,
)
