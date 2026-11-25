package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.more.stats.RStatsData

/**
 * R: achievement badges. Three kinds (chapters read, hours read, best streak in days), each on a fixed ladder. A badge is UNLOCKED from the
 * whole reading history (so a backup restored from the old app opens every badge it already earned) and gives XP that the user COLLECTS by hand
 * (one by one or "collect all"). Collected ids live in pref r_badges_claimed; the xp goes to the saved bonus (r_dragon_xp_bonus).
 * Reward: chapters N -> N xp (10% of the 10 xp a chapter gave), hours H -> 4H xp (10% of 40/hour), streak D days -> 2D xp; at least 10 xp.
 */
object RBadges {
    enum class Kind(val key: String, val label: String) { CHAPTERS("c", "شباتر"), HOURS("h", "ساعات"), STREAK("s", "أيام متتالية") }

    class Badge(val kind: Kind, val goal: Int) {
        val id get() = "${kind.key}$goal"
        val reward: Int
            get() = when (kind) {
                Kind.CHAPTERS -> goal
                Kind.HOURS -> goal * 4
                Kind.STREAK -> goal * 2
            }.coerceAtLeast(10)
        val title get() = "$goal ${kind.label}"
        val condition
            get() = when (kind) {
                Kind.CHAPTERS -> "اقرأ $goal شابتر"
                Kind.HOURS -> "اقرأ $goal ساعة"
                Kind.STREAK -> "اقرأ $goal يوم ورا بعض"
            }
    }

    /** 5..50 step 5, 100..1000 step 50, 1500..10000 step 500, 15000..100000 step 5000. */
    val CHAPTER_GOALS: List<Int> =
        (5..50 step 5).toList() + (100..1000 step 50) + (1500..10000 step 500) + (15000..100000 step 5000)
    val HOUR_GOALS = listOf(1, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 150, 200, 300, 400, 500, 750, 1000, 1500, 2000, 3000, 5000)
    val STREAK_GOALS = listOf(3, 7, 14, 30, 60, 100, 200, 365)

    val ALL: List<Badge> =
        CHAPTER_GOALS.map { Badge(Kind.CHAPTERS, it) } + HOUR_GOALS.map { Badge(Kind.HOURS, it) } + STREAK_GOALS.map { Badge(Kind.STREAK, it) }

    fun current(d: RStatsData, kind: Kind): Long = when (kind) {
        Kind.CHAPTERS -> d.totalChapters.toLong()
        Kind.HOURS -> d.totalReadMs / 3_600_000L
        Kind.STREAK -> d.streakBest.toLong()
    }

    fun unlocked(d: RStatsData, b: Badge) = current(d, b.kind) >= b.goal

    private fun claimedSet(prefs: PreferencesHelper): Set<String> =
        prefs.rBadgesClaimed().get().split(',').filter { it.isNotBlank() }.toSet()

    fun isClaimed(prefs: PreferencesHelper, b: Badge) = b.id in claimedSet(prefs)

    /** unlocked and not collected yet */
    fun ready(prefs: PreferencesHelper, d: RStatsData): List<Badge> {
        val done = claimedSet(prefs)
        return ALL.filter { unlocked(d, it) && it.id !in done }
    }

    class Claim(val count: Int, val xp: Int)

    /** Collects [list] (what is already collected is skipped) and adds the xp to the bonus. */
    fun claim(prefs: PreferencesHelper, list: List<Badge>): Claim {
        val done = claimedSet(prefs).toMutableSet()
        var xp = 0
        var n = 0
        for (b in list) {
            if (done.add(b.id)) {
                xp += b.reward
                n++
            }
        }
        if (n > 0) {
            prefs.rBadgesClaimed().set(done.joinToString(","))
            prefs.rDragonXpBonus().set(prefs.rDragonXpBonus().get() + xp)
            prefs.rDragonXp().set((prefs.rDragonXp().get().toLong() + xp).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        return Claim(n, xp)
    }
}
