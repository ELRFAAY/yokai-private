package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.more.stats.RStatsData
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
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
        /** 1 bronze, 2 silver, 3 gold, 4 diamond (by how hard the target is). */
        val tier get() = when (kind) {
            CHAPTERS -> if (target < 100) 1 else if (target < 1000) 2 else if (target < 10000) 3 else 4
            HOURS -> if (target < 10) 1 else if (target < 100) 2 else if (target < 500) 3 else 4
            else -> if (target < 14) 1 else if (target < 60) 2 else if (target < 200) 3 else 4
        }
        fun iconName() = "r_badge_" + (when (kind) { CHAPTERS -> "c"; HOURS -> "h"; else -> "s" }) + tier
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

    private fun addXp(prefs: PreferencesHelper, xp: Int) = RDragonLevel.addXp(prefs, xp.toLong())

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
    private fun today() = LocalDate.now().toString()
    private fun yesterday() = LocalDate.now().minusDays(1).toString()

    /** Today's chest is still closed (it renews every day at midnight, local time). */
    fun available(prefs: PreferencesHelper = Injekt.get()) = prefs.rChestDay().get() != today()

    /** Day of the 7-day cycle (1..7) that today's chest belongs to / belonged to. A missed day starts again from day 1. */
    fun cycleDay(prefs: PreferencesHelper = Injekt.get()): Int {
        val last = prefs.rChestDay().get()
        val streak = prefs.rChestStreak().get().coerceIn(0, 7)
        return when (last) {
            today() -> streak.coerceAtLeast(1)
            yesterday() -> if (streak >= 7) 1 else streak + 1
            else -> 1
        }
    }

    /** XP range for a cycle day: grows each day, day 7 is the big one. */
    fun range(day: Int): IntRange = when (day) {
        1 -> 20..50
        2 -> 30..70
        3 -> 40..90
        4 -> 60..120
        5 -> 80..160
        6 -> 100..200
        else -> 300..600
    }

    /** Opens today's chest. Returns the XP (0 if already opened today). */
    fun open(prefs: PreferencesHelper = Injekt.get()): Int {
        if (!available(prefs)) return 0
        val day = cycleDay(prefs)
        val r = range(day)
        val xp = Random.nextInt(r.first, r.last + 1)
        prefs.rChestDay().set(today())
        prefs.rChestStreak().set(day)
        RDragonLevel.addXp(prefs, xp.toLong())
        return xp
    }
}
