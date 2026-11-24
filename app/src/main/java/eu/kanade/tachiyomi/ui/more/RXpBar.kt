package eu.kanade.tachiyomi.ui.more

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import eu.kanade.tachiyomi.util.RDragonLevel
import eu.kanade.tachiyomi.util.system.dpToPx

/**
 * R: the XP bar in the style of the owner's reference app: "LV. N" on the left, a thin rounded bar in the middle, a big "XP" on the right,
 * and "now/next" under the bar (blue number, grey total). Always laid out left-to-right (it is numbers and Latin letters).
 */
class RXpBar(context: Context, private val textColor: Int = Color.WHITE) : LinearLayout(context) {
    private val lv = TextView(context)
    private val xpWord = TextView(context)
    private val count = TextView(context)
    private val track = Track(context)

    init {
        orientation = VERTICAL
        layoutDirection = LAYOUT_DIRECTION_LTR
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = LAYOUT_DIRECTION_LTR
        }
        lv.apply { textSize = 18f; setTypeface(typeface, Typeface.BOLD); setTextColor(textColor) }
        xpWord.apply { text = "XP"; textSize = 22f; setTypeface(typeface, Typeface.BOLD); setTextColor(textColor) }
        row.addView(lv)
        row.addView(track, LayoutParams(0, 10.dpToPx, 1f).apply { marginStart = 10.dpToPx; marginEnd = 10.dpToPx })
        row.addView(xpWord)
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        count.apply { textSize = 12f; gravity = Gravity.END; setTextColor(0xFF9AA7BD.toInt()) }
        addView(count, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = 3.dpToPx })
    }

    /** shows [info]; with [animate] the bar fills and the xp number counts up. */
    fun bind(info: RDragonLevel.Info, animate: Boolean = true) {
        fun show(f: Float) {
            val now = (info.xp * f).toLong()
            lv.text = "LV. ${info.level}"
            track.fill = info.progress * f
            val next = info.nextAt
            count.text = if (next != null) "$now / $next" else "$now  MAX"
            track.invalidate()
        }
        if (!animate) {
            show(1f)
            return
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            addUpdateListener { show(it.animatedValue as Float) }
            start()
        }
    }

    private class Track(context: Context) : View(context) {
        var fill = 0f
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x553A4A6A }
        private val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E88FF.toInt() }
        private val r = RectF()

        override fun onDraw(c: Canvas) {
            val h = height.toFloat()
            r.set(0f, 0f, width.toFloat(), h)
            c.drawRoundRect(r, h / 2, h / 2, bg)
            if (fill > 0f) {
                r.set(0f, 0f, maxOf(h, width * fill), h)
                c.drawRoundRect(r, h / 2, h / 2, fg)
            }
        }
    }
}
