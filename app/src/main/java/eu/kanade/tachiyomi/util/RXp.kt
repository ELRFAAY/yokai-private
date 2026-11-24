package eu.kanade.tachiyomi.util

import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * R: XP per finished chapter with a combo. A reading session = chapters finished with gaps under 30 minutes.
 * Chapters 1-4 of a session x1, 5-9 x1.5, 10-19 x2, 20+ x3 (approved by the owner). The base is
 * [RDragonLevel.XP_PER_CHAPTER]; the extra part is saved in `r_dragon_xp_bonus` so the history based total stays valid.
 */
object RXp {
    private const val SESSION_GAP_MS = 30 * 60 * 1000L

    /** What the last finished chapter gave (shown on the end card). */
    @Volatile var lastGain: Int = 0
    @Volatile var lastMultiplier: Float = 1f
    @Volatile var lastCombo: Int = 0

    fun multiplier(count: Int): Float = when {
        count >= 20 -> 3f
        count >= 10 -> 2f
        count >= 5 -> 1.5f
        else -> 1f
    }

    /** Call once when a chapter becomes read (it was not read before). Returns the xp gained. */
    fun onChapterRead(context: Context, prefs: PreferencesHelper = Injekt.get()): Int {
        val sp = context.getSharedPreferences("r_xp", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = sp.getLong("last", 0L)
        val count = (if (now - last <= SESSION_GAP_MS) sp.getInt("count", 0) else 0) + 1
        val mult = multiplier(count)
        val gain = (RDragonLevel.XP_PER_CHAPTER * mult).toInt()
        val extra = gain - RDragonLevel.XP_PER_CHAPTER
        sp.edit().putLong("last", now).putInt("count", count).apply()
        prefs.rDragonXpBonus().set(prefs.rDragonXpBonus().get() + extra)
        prefs.rDragonXp().set(prefs.rDragonXp().get() + gain)
        lastGain = gain
        lastMultiplier = mult
        lastCombo = count
        return gain
    }
}
