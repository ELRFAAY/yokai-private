package eu.kanade.tachiyomi.ui.setting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.util.system.getResourceColor

/**
 * R: draws each group of settings (the items under one category title) as a rounded card tinted with
 * the app theme color, like the reader settings the owner liked. Category titles stay outside the cards.
 */
class RSettingsCardDecoration(context: Context) : RecyclerView.ItemDecoration() {
    private val density = context.resources.displayMetrics.density
    private val side = (12 * density).toInt()
    private val corner = 22 * density
    // neutral grey tones from the text color (the theme green was too much); only the icon badge keeps a faint tint
    private val secondary = context.getResourceColor(com.google.android.material.R.attr.colorOnSurface)
    private val accent = context.getResourceColor(com.google.android.material.R.attr.colorSecondary)

    // a calm, dark card with only a hint of the theme color (the old one was a flat saturated green block)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(secondary, 16)
    }
    private val iconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(accent, 30)
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(secondary, 22)
        strokeWidth = 1f * density
    }
    private val iconRadius = 19 * density
    private val dividerInset = 72 * density
    private val path = Path()
    private val rect = RectF()

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.left = side
        outRect.right = side
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        val pos = parent.getChildAdapterPosition(view)
        if (pos > 0 && adapter.getItem(pos) is PreferenceCategory) outRect.top = (10 * density).toInt()
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            if (pos < 0) continue
            if (adapter.getItem(pos) is PreferenceCategory) continue
            val first = pos == 0 || adapter.getItem(pos - 1) is PreferenceCategory
            val last = pos == adapter.itemCount - 1 || adapter.getItem(pos + 1) is PreferenceCategory
            val top = if (first) corner else 0f
            val bottom = if (last) corner else 0f
            rect.set(
                child.left.toFloat(),
                child.top + child.translationY,
                child.right.toFloat(),
                child.bottom + child.translationY,
            )
            path.reset()
            path.addRoundRect(rect, floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom), Path.Direction.CW)
            c.drawPath(path, paint)

            // a soft round badge behind each row's icon, like a settings app icon
            val icon = child.findViewById<View>(android.R.id.icon)
            if (icon != null && icon.isShown && icon.width > 0) {
                // the icon sits inside a nested frame: convert its box to the row's coordinates
                val box = Rect(0, 0, icon.width, icon.height)
                (child as? android.view.ViewGroup)?.offsetDescendantRectToMyCoords(icon, box)
                val cx = child.left + box.exactCenterX()
                val cy = child.top + child.translationY + box.exactCenterY()
                c.drawCircle(cx, cy, iconRadius, iconBgPaint)
            }

            // a thin divider between rows (not under the last one), starting after the icon
            if (!last) {
                val y = rect.bottom - dividerPaint.strokeWidth / 2f
                val rtl = parent.layoutDirection == View.LAYOUT_DIRECTION_RTL
                if (rtl) {
                    c.drawLine(rect.left + 16 * density, y, rect.right - dividerInset, y, dividerPaint)
                } else {
                    c.drawLine(rect.left + dividerInset, y, rect.right - 16 * density, y, dividerPaint)
                }
            }
        }
    }
}
