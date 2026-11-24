package eu.kanade.tachiyomi.util

import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper

/**
 * R: the look the owner put on the little dragon (level screen > الأزياء / كل المراحل). Pref `r_dragon_outfit`:
 * "" = none (normal dragon), "lv:N" = the picture of stage N, anything else = the costume name (`r_costume_<name>`).
 * Every place that draws the dragon asks here, so the look shows in all of them.
 */
object RDragonLook {
    private val ids = HashMap<String, Int>()

    fun stageValue(level: Int) = "lv:$level"

    fun isStage(value: String) = value.startsWith("lv:")

    /** Drawable of the worn look, 0 when nothing is worn. */
    fun resId(context: Context, prefs: PreferencesHelper): Int {
        val v = prefs.rDragonOutfit().get()
        if (v.isBlank()) return 0
        return ids.getOrPut(v) {
            val name = if (isStage(v)) "r_lv_${v.removePrefix("lv:").toIntOrNull() ?: 0}" else "r_costume_$v"
            context.resources.getIdentifier(name, "drawable", context.packageName)
        }
    }

    /** Rank-card / portrait drawable of the worn look, 0 = none. */
    fun portraitId(context: Context, prefs: PreferencesHelper): Int {
        val v = prefs.rDragonOutfit().get()
        if (v.isBlank()) return 0
        val name = if (isStage(v)) "r_lv_${v.removePrefix("lv:").toIntOrNull() ?: 0}" else "r_avc_$v"
        return context.resources.getIdentifier(name, "drawable", context.packageName)
    }
}
