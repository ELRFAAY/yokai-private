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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * R: the profile "wallet", two lines stacked: first the XP count with the SILVER animated XP icon (XP is what raises the level),
 * under it the gold COINS with the gold coin (1 coin per 1000 XP; a currency for the shop that comes later, never spent as XP).
 * (The class keeps its old name so the callers did not change.)
 */
class RGoldXpRow(context: Context, xp: Long, textColor: Int, small: Boolean = false) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val d = resources.displayMetrics.density
        val size = ((if (small) 20 else 28) * d).toInt()
        val sp = if (small) 12.5f else 15f

        // the same pill as the reader counter: dark rounded bar, coloured outline, icon + number
        fun line(icon: android.graphics.drawable.Drawable, text: String, stroke: Int): LinearLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = (6 * d).toInt()
            setPadding(pad, pad / 2, pad * 2, pad / 2)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 40 * d
                setColor(0xCC10231A.toInt())
                setStroke((1.2f * d).toInt(), stroke)
            }
            addView(ImageView(context).apply { setImageDrawable(icon) }, LayoutParams(size, size))
            addView(
                TextView(context).apply {
                    this.text = text
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = sp
                    setTypeface(typeface, Typeface.BOLD)
                    textDirection = TEXT_DIRECTION_LTR
                },
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = (4 * d).toInt() },
            )
        }

        // 1) XP with the silver animated icon
        val anim = AnimationDrawable().apply {
            for (i in 0..10) {
                val id = resources.getIdentifier("r_xps_$i", "drawable", context.packageName)
                if (id != 0) ContextCompat.getDrawable(context, id)?.let { addFrame(it, 90) }
            }
            isOneShot = false
        }
        val xpLine = line(anim, "${RDragonLevel.full(xp)} XP", 0x884ADE80.toInt())
        addView(xpLine, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        post { anim.start() }

        // 2) gold coins under it
        val prefs = Injekt.get<eu.kanade.tachiyomi.data.preference.PreferencesHelper>()
        val coins = RDragonLevel.goldBalance(prefs)
        val toNext = 1000L - xp % 1000L
        addView(
            line(RXpBadge.coin(context), "${RDragonLevel.full(coins)} عملة", 0xAAFFC53D.toInt()),
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = (5 * d).toInt() },
        )
        addView(
            TextView(context).apply {
                text = "فاضل $toNext XP للعملة الجاية"
                setTextColor(textColor)
                alpha = 0.75f
                textSize = 11f
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = (3 * d).toInt(); marginStart = (6 * d).toInt() },
        )
    }
}
