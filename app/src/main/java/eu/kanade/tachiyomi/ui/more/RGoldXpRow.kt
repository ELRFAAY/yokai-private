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
 * R: GOLD COINS: a currency (NOT xp). Every 1000 XP of the whole history gives one coin; XP itself is never spent. Balance = earned -
 * spent (a shop comes later), shown with the code-drawn coin and the XP still missing for the next coin.
 */
class RGoldXpRow(context: Context, xp: Long, textColor: Int, small: Boolean = false) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val d = resources.displayMetrics.density
        val size = ((if (small) 18 else 26) * d).toInt()
        addView(ImageView(context).apply { setImageDrawable(RXpBadge.coin()) }, LayoutParams(size, size))
        val prefs = uy.kohesive.injekt.Injekt.get<eu.kanade.tachiyomi.data.preference.PreferencesHelper>()
        val gold = RDragonLevel.goldBalance(prefs)
        val toNext = 1000L - xp % 1000L
        addView(
            TextView(context).apply {
                text = "${RDragonLevel.compact(gold)} عملة ذهبية  •  فاضل $toNext XP للعملة الجاية"
                setTextColor(textColor)
                textSize = if (small) 11.5f else 13.5f
                setTypeface(typeface, Typeface.BOLD)
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = (6 * d).toInt() },
        )
    }
}
