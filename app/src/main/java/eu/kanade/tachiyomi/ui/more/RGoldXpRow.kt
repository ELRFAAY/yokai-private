package eu.kanade.tachiyomi.ui.more

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.AnimationDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.util.RDragonLevel

/**
 * R: "gold XP": every 1000 XP of the whole history (level 1 until now) is one gold XP. XP is never spent; the gold count is just
 * total / 1000, shown with the gold version of the animated XP icon (r_xpg_0..10) and the XP still missing for the next one.
 */
class RGoldXpRow(context: Context, xp: Long, textColor: Int, small: Boolean = false) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val d = resources.displayMetrics.density
        val size = ((if (small) 18 else 26) * d).toInt()
        val anim = AnimationDrawable().apply {
            for (i in 0..10) {
                val id = resources.getIdentifier("r_xpg_$i", "drawable", context.packageName)
                if (id != 0) ContextCompat.getDrawable(context, id)?.let { addFrame(it, 90) }
            }
            isOneShot = false
        }
        addView(ImageView(context).apply { setImageDrawable(anim); post { anim.start() } }, LayoutParams(size, size))
        val gold = RDragonLevel.gold(xp)
        val toNext = 1000L - xp % 1000L
        addView(
            TextView(context).apply {
                text = "${RDragonLevel.compact(gold)} ذهبية  •  فاضل $toNext XP للذهبية الجاية"
                setTextColor(textColor)
                textSize = if (small) 11.5f else 13.5f
                setTypeface(typeface, Typeface.BOLD)
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = (6 * d).toInt() },
        )
    }
}
