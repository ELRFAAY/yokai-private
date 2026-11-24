package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.R

/**
 * R: the little dragon grows with reading. Every chapter read is [XP_PER_CHAPTER] xp and every hour read is [XP_PER_HOUR] xp.
 * 12 stages (pictures r_lv_1 ... r_lv_12). The numbers are the first draft (not approved yet): change them here only.
 */
object RDragonLevel {
    const val XP_PER_CHAPTER = 1
    const val XP_PER_HOUR = 4

    /** xp needed to START each level (index 0 = level 1). */
    val STARTS = longArrayOf(0, 25, 100, 300, 800, 2000, 4500, 9000, 15000, 25000, 40000, 70000)

    val PICTURES = intArrayOf(
        R.drawable.r_lv_1, R.drawable.r_lv_2, R.drawable.r_lv_3, R.drawable.r_lv_4, R.drawable.r_lv_5, R.drawable.r_lv_6,
        R.drawable.r_lv_7, R.drawable.r_lv_8, R.drawable.r_lv_9, R.drawable.r_lv_10, R.drawable.r_lv_11, R.drawable.r_lv_12,
    )

    fun xp(chapters: Int, readMs: Long): Long = chapters.toLong() * XP_PER_CHAPTER + readMs * XP_PER_HOUR / 3_600_000L

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
