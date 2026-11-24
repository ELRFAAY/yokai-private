package eu.kanade.tachiyomi.ui.more

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.RDragonLevel
import eu.kanade.tachiyomi.util.system.dpToPx
import uy.kohesive.injekt.injectLazy

/**
 * R: compact green rank card (top of the overflow menu). Round avatar = the dragon's stage portrait (r_av_N), or the chosen costume
 * portrait (r_avc_<name>, pref r_dragon_outfit) - plus level, a progress bar and the xp left.
 */
class RRankCardView(context: Context) : LinearLayout(context) {
    private val prefs: PreferencesHelper by injectLazy()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(12.dpToPx, 10.dpToPx, 14.dpToPx, 10.dpToPx)
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF1F4D2E.toInt(), 0xFF2E7D45.toInt())).apply {
            cornerRadius = 14.dpToPx.toFloat()
        }
        val xp = prefs.rDragonXp().get().toLong()
        val info = RDragonLevel.info(xp)

        val avatar = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0x33FFFFFF)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) = o.setOval(0, 0, v.width, v.height)
            }
            clipToOutline = true
            val pic = prefs.rProfileAvatar().get()
            val bmp = if (pic.isNotBlank() && java.io.File(pic).exists()) runCatching { android.graphics.BitmapFactory.decodeFile(pic) }.getOrNull() else null
            if (bmp != null) {
                setImageBitmap(bmp)
            } else {
                val res = eu.kanade.tachiyomi.util.RDragonLook.portraitId(context, prefs)
                setImageResource(if (res != 0) res else avatarOf(info.level))
            }
        }
        addView(avatar, LayoutParams(52.dpToPx, 52.dpToPx))

        val col = LinearLayout(context).apply { orientation = VERTICAL }
        col.addView(TextView(context).apply {
            text = "مستوى ${info.level}"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        col.addView(
            RXpBar(context, Color.WHITE).apply { bind(info, animate = false) },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = 6.dpToPx },
        )
        addView(col, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 12.dpToPx })
    }

    private fun avatarOf(level: Int): Int {
        val id = context.resources.getIdentifier("r_av_${level.coerceIn(1, 12)}", "drawable", context.packageName)
        return if (id != 0) id else RDragonLevel.PICTURES[level.coerceIn(1, 12) - 1]
    }
}
