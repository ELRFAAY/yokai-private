package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.more.stats.RStatsData
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.random.Random

/**
 * R: badges with XP. Every badge is computed from the READ HISTORY (chapters, hours, best streak), never from counters that start at
 * install, so a backup restored from another app gives all the badges it already earned. XP is collected by hand ([claim] / [claimAll]).
 */
object RAchievements {
    const val CHAPTERS = 0
    const val HOURS = 1
    const val STREAK = 2

    class Badge(val id: String, val kind: Int, val target: Long, val xp: Int) {
        val emoji get() = when (kind) { CHAPTERS -> "📖"; HOURS -> "⏱"; else -> "🔥" }
        val title get() = when (kind) {
            CHAPTERS -> "$target شابتر"
            HOURS -> "$target ساعة قراءة"
            else -> "سلسلة $target يوم"
        }
        val desc get() = when (kind) {
            CHAPTERS -> "اقرأ $target شابتر"
            HOURS -> "اقرأ $target ساعة"
            else -> "اقرأ $target يوم ورا بعض"
        }
    }

    /** Chapters: 10, then 15..50 step 5, 100..1000 step 50, 1500..5000 step 500, 6000..20000 step 1000, 25000..100000 step 5000. */
    private fun chapterLadder(): List<Long> {
        val l = ArrayList<Long>()
        l += 10
        for (n in 15..50 step 5) l += n.toLong()
        for (n in 100..1000 step 50) l += n.toLong()
        for (n in 1500..5000 step 500) l += n.toLong()
        for (n in 6000..20000 step 1000) l += n.toLong()
        for (n in 25000..100000 step 5000) l += n.toLong()
        return l
    }

    val all: List<Badge> by lazy {
        val out = ArrayList<Badge>()
        chapterLadder().forEach { out += Badge("c$it", CHAPTERS, it, maxOf(25L, it / 2).toInt()) }
        listOf(1L, 5, 10, 25, 50, 100, 200, 300, 500, 750, 1000).forEach { out += Badge("h$it", HOURS, it, (it * 10).toInt().coerceAtLeast(30)) }
        listOf(3L, 7, 14, 30, 60, 100, 200, 365).forEach { out += Badge("s$it", STREAK, it, (it * 8).toInt().coerceAtLeast(30)) }
        out
    }

    fun value(kind: Int, d: RStatsData): Long = when (kind) {
        CHAPTERS -> d.totalChapters.toLong()
        HOURS -> d.totalReadMs / 3_600_000L
        else -> d.streakBest.toLong()
    }

    fun unlocked(b: Badge, d: RStatsData) = value(b.kind, d) >= b.target

    fun claimed(prefs: PreferencesHelper = Injekt.get()): Set<String> = prefs.rBadgesClaimed().get()

    private fun addXp(prefs: PreferencesHelper, xp: Int) {
        prefs.rDragonXpBonus().set(prefs.rDragonXpBonus().get() + xp)
        prefs.rDragonXp().set(prefs.rDragonXp().get() + xp)
    }

    /** Collects every unlocked, not yet collected badge. Returns (how many badges, how much XP). */
    fun claimAll(d: RStatsData, prefs: PreferencesHelper = Injekt.get()): Pair<Int, Int> {
        val done = claimed(prefs).toMutableSet()
        var n = 0
        var xp = 0
        all.forEach { if (unlocked(it, d) && done.add(it.id)) { n++; xp += it.xp } }
        if (n > 0) {
            prefs.rBadgesClaimed().set(done)
            addXp(prefs, xp)
        }
        return n to xp
    }

    fun claim(b: Badge, d: RStatsData, prefs: PreferencesHelper = Injekt.get()): Int {
        if (!unlocked(b, d)) return 0
        val done = claimed(prefs).toMutableSet()
        if (!done.add(b.id)) return 0
        prefs.rBadgesClaimed().set(done)
        addXp(prefs, b.xp)
        return b.xp
    }
}

/** R: one chest per calendar week (weekly login reward) with a random XP amount. */
object RChest {
    fun week(): String {
        val d = LocalDate.now()
        return "${d.get(IsoFields.WEEK_BASED_YEAR)}-W${d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
    }

    fun available(prefs: PreferencesHelper = Injekt.get()) = prefs.rChestWeek().get() != week()

    /** Rolls the reward: common 30-80, good 80-160, rare 160-300, jackpot 500-800. Returns the XP (0 if already opened this week). */
    fun open(prefs: PreferencesHelper = Injekt.get()): Int {
        if (!available(prefs)) return 0
        val roll = Random.nextInt(100)
        val xp = when {
            roll < 55 -> Random.nextInt(30, 81)
            roll < 85 -> Random.nextInt(80, 161)
            roll < 97 -> Random.nextInt(160, 301)
            else -> Random.nextInt(500, 801)
        }
        prefs.rChestWeek().set(week())
        prefs.rDragonXpBonus().set(prefs.rDragonXpBonus().get() + xp)
        prefs.rDragonXp().set(prefs.rDragonXp().get() + xp)
        return xp
    }
}
