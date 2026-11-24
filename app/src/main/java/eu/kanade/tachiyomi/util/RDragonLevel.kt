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

    /** How much bigger the floating dragon is at a level: 0.80 at level 1, 1.15 at level 12 (smooth). */
    fun sizeFactor(level: Int): Float = 0.80f + (level - 1) * (0.35f / 11f)
}
