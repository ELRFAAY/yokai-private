package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** R: manga key that survives a backup restore (manga ids do not). */
fun rMangaKey(source: Long, url: String) = "$source|$url"

private fun parse(s: String) = try { JSONObject(s) } catch (e: Exception) { JSONObject() }

/** R: a note per chapter, e.g. "ص١٢: هنا بدأ الأرك". */
object RChapterNotes {
    private fun key(source: Long, mangaUrl: String, chapterUrl: String) = "$source|$mangaUrl|$chapterUrl"

    fun get(source: Long, mangaUrl: String, chapterUrl: String, prefs: PreferencesHelper = Injekt.get()): String =
        parse(prefs.rChapterNotes().get()).optString(key(source, mangaUrl, chapterUrl), "")

    fun set(source: Long, mangaUrl: String, chapterUrl: String, text: String, prefs: PreferencesHelper = Injekt.get()) {
        val json = parse(prefs.rChapterNotes().get())
        if (text.isBlank()) json.remove(key(source, mangaUrl, chapterUrl)) else json.put(key(source, mangaUrl, chapterUrl), text)
        prefs.rChapterNotes().set(json.toString())
    }
}

/** R: personal tags per manga (several each), searchable from the library search box. */
object RTags {
    fun get(source: Long, url: String, prefs: PreferencesHelper = Injekt.get()): List<String> {
        val arr = parse(prefs.rMangaTags().get()).optJSONArray(rMangaKey(source, url)) ?: return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun set(source: Long, url: String, tags: List<String>, prefs: PreferencesHelper = Injekt.get()) {
        val json = parse(prefs.rMangaTags().get())
        val clean = tags.map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) json.remove(rMangaKey(source, url)) else json.put(rMangaKey(source, url), JSONArray(clean))
        prefs.rMangaTags().set(json.toString())
    }

    /** Every tag used anywhere, most used first. */
    fun all(prefs: PreferencesHelper = Injekt.get()): List<String> {
        val json = parse(prefs.rMangaTags().get())
        val count = HashMap<String, Int>()
        json.keys().forEach { k ->
            val arr = json.optJSONArray(k) ?: return@forEach
            for (i in 0 until arr.length()) count.merge(arr.getString(i), 1, Int::plus)
        }
        return count.entries.sortedByDescending { it.value }.map { it.key }
    }

    // cached parse for the library search (called for every manga while typing)
    @Volatile private var cacheRaw: String? = null
    @Volatile private var cache: JSONObject = JSONObject()

    fun matches(source: Long, url: String, query: String, prefs: PreferencesHelper = Injekt.get()): Boolean {
        val q = query.trim().removePrefix("#")
        if (q.isEmpty()) return false
        val raw = prefs.rMangaTags().get()
        if (raw != cacheRaw) {
            cache = parse(raw)
            cacheRaw = raw
        }
        val arr = cache.optJSONArray(rMangaKey(source, url)) ?: return false
        return (0 until arr.length()).any { arr.getString(it).contains(q, true) }
    }
}

/** R: how long the person really read: per day and per manga (the history only keeps a total per chapter). */
object RReadLog {
    private val dayFmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun add(source: Long, url: String, title: String, ms: Long, prefs: PreferencesHelper = Injekt.get()) {
        if (ms < 1000L) return
        val json = parse(prefs.rReadLog().get())
        val days = json.optJSONObject("days") ?: JSONObject().also { json.put("days", it) }
        val mangas = json.optJSONObject("manga") ?: JSONObject().also { json.put("manga", it) }
        val today = dayFmt.format(Date())
        days.put(today, days.optLong(today, 0L) + ms)
        val key = rMangaKey(source, url)
        val m = mangas.optJSONObject(key) ?: JSONObject().put("t", title).put("ms", 0L)
        m.put("t", title)
        m.put("ms", m.optLong("ms", 0L) + ms)
        mangas.put(key, m)
        prefs.rReadLog().set(json.toString())
    }

    class Summary(
        val todayMs: Long,
        val avgActiveDayMs: Long,
        val bestDay: String?,
        val bestDayMs: Long,
        val lastDayAgo: Int?,
        val top: List<Pair<String, Long>>,
    )

    fun summary(prefs: PreferencesHelper = Injekt.get()): Summary {
        val json = parse(prefs.rReadLog().get())
        val days = json.optJSONObject("days") ?: JSONObject()
        val map = days.keys().asSequence().associateWith { days.optLong(it, 0L) }.filterValues { it > 0 }
        val today = dayFmt.format(Date())
        val recent = map.entries.sortedByDescending { it.key }.take(30)
        val avg = if (recent.isEmpty()) 0L else recent.sumOf { it.value } / recent.size
        val best = map.maxByOrNull { it.value }
        val last = map.keys.maxOrNull()?.let { daysAgo(it) }
        val mangas = json.optJSONObject("manga") ?: JSONObject()
        val top = mangas.keys().asSequence()
            .map { k -> mangas.getJSONObject(k).let { it.optString("t", "?") to it.optLong("ms", 0L) } }
            .sortedByDescending { it.second }.take(3).toList()
        return Summary(map[today] ?: 0L, avg, best?.key, best?.value ?: 0L, last, top)
    }

    /** Whole days since the given yyyy-MM-dd day (0 = today). */
    fun daysAgo(day: String): Int? = try {
        val then = Calendar.getInstance().apply { time = dayFmt.parse(day)!! }
        val now = Calendar.getInstance()
        listOf(then, now).forEach {
            it.set(Calendar.HOUR_OF_DAY, 12)
            it.set(Calendar.MINUTE, 0)
            it.set(Calendar.SECOND, 0)
            it.set(Calendar.MILLISECOND, 0)
        }
        ((now.timeInMillis - then.timeInMillis) / 86_400_000L).toInt()
    } catch (e: Exception) {
        null
    }

    /** "٢ س ١٥ د" style, in Arabic words. */
    fun format(ms: Long): String {
        val min = ms / 60_000L
        val h = min / 60
        val m = min % 60
        return when {
            ms < 60_000L -> "${ms / 1000} ثانية"
            h == 0L -> "$m دقيقة"
            m == 0L -> "$h ساعة"
            else -> "$h ساعة و$m دقيقة"
        }
    }
}
