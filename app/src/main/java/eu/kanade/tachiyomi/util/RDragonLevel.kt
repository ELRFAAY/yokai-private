package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.R

/**
 * R: the little dragon grows with reading. Every chapter read is [XP_PER_CHAPTER] xp and every hour read is [XP_PER_HOUR] xp.
 * 12 stages (pictures r_lv_1 ... r_lv_12). The numbers are the first draft (not approved yet): change them here only.
 */
object RDragonLevel {
    // x10 of the first draft (owner approved): same pace for the old history, but numbers feel bigger
    const val XP_PER_CHAPTER = 10
    const val XP_PER_HOUR = 40

    /** xp needed to START each level (index 0 = level 1). */
    val STARTS = longArrayOf(0, 250, 1000, 3000, 8000, 20000, 45000, 90000, 150000, 250000, 400000, 700000)

    val PICTURES = intArrayOf(
        R.drawable.r_lv_1, R.drawable.r_lv_2, R.drawable.r_lv_3, R.drawable.r_lv_4, R.drawable.r_lv_5, R.drawable.r_lv_6,
        R.drawable.r_lv_7, R.drawable.r_lv_8, R.drawable.r_lv_9, R.drawable.r_lv_10, R.drawable.r_lv_11, R.drawable.r_lv_12,
    )

    /** History base + the combo bonus collected since the multiplier exists (`r_dragon_xp_bonus`). */
    fun xp(chapters: Int, readMs: Long, bonus: Long = 0L): Long =
        chapters.toLong() * XP_PER_CHAPTER + readMs * XP_PER_HOUR / 3_600_000L + bonus

    class Info(val level: Int, val xp: Long, val levelStart: Long, val nextAt: Long?) {
        val left get() = nextAt?.let { it - xp }
        val progress get() = nextAt?.let { ((xp - levelStart).toFloat() / (it - levelStart)).coerceIn(0f, 1f) } ?: 1f
    }

    fun info(xp: Long): Info {
        var level = 1
        for (i in STARTS.indices) if (xp >= STARTS[i]) level = i + 1
        return Info(level, xp, STARTS[level - 1], STARTS.getOrNull(level))
    }

    // ---- unlimited XP (Long) + gold XP: every 1000 XP is one gold XP; the XP itself is never spent, gold is just 1000-step count ----
    private fun migrate(prefs: eu.kanade.tachiyomi.data.preference.PreferencesHelper) {
        if (prefs.rDragonXpL().get() == 0L) prefs.rDragonXp().get().takeIf { it > 0 }?.let { prefs.rDragonXpL().set(it.toLong()) }
        if (prefs.rDragonXpBonusL().get() == 0L) prefs.rDragonXpBonus().get().takeIf { it > 0 }?.let { prefs.rDragonXpBonusL().set(it.toLong()) }
    }

    fun totalXp(prefs: eu.kanade.tachiyomi.data.preference.PreferencesHelper): Long { migrate(prefs); return prefs.rDragonXpL().get() }
    fun bonusXp(prefs: eu.kanade.tachiyomi.data.preference.PreferencesHelper): Long { migrate(prefs); return prefs.rDragonXpBonusL().get() }
    fun setTotalXp(prefs: eu.kanade.tachiyomi.data.preference.PreferencesHelper, v: Long) { prefs.rDragonXpL().set(v) }

    /** Adds [gain] to the total and [extra] (the part beyond the plain chapter XP) to the bonus. */
    fun addXp(prefs: eu.kanade.tachiyomi.data.preference.PreferencesHelper, gain: Long, extra: Long = gain) {
        migrate(prefs)
        prefs.rDragonXpBonusL().set(prefs.rDragonXpBonusL().get() + extra)
        prefs.rDragonXpL().set(prefs.rDragonXpL().get() + gain)
    }

    fun gold(xp: Long): Long = xp / 1000L

    /** 999 -> "999", 12345 -> "12.3K", then M, B, T, Qa, Qi, Sx, Sp, Oc (so a huge combo still fits a small label). */
    fun compact(v: Long): String {
        if (v < 10_000L) return String.format(java.util.Locale.US, "%,d", v)
        val units = arrayOf("K", "M", "B", "T", "Qa", "Qi")
        var d = v.toDouble()
        var i = -1
        while (d >= 1000.0 && i < units.lastIndex) { d /= 1000.0; i++ }
        return String.format(java.util.Locale.US, if (d >= 100) "%.0f%s" else if (d >= 10) "%.1f%s" else "%.2f%s", d, units[i])
    }

    /** How much bigger the floating dragon is at a level: 0.80 at level 1, 1.15 at level 12 (smooth). */
    fun sizeFactor(level: Int): Float = 0.80f + (level - 1) * (0.35f / 11f)
}
