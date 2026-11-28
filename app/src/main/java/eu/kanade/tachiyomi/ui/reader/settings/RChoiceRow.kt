package eu.kanade.tachiyomi.ui.reader.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import eu.kanade.tachiyomi.util.system.getResourceColor

/**
 * R: the options of the reader settings as picture cards: a small drawn illustration (a phone showing what the option does) with the
 * FULL label under it (wraps, never cut with "…"). Replaces the old text-only segmented buttons.
 */
object RChoiceRow {
    const val SIDE = 0 // which bottom corner(s) of the screen scroll down: params = 0 right, 1 left, 2 both
    const val JUMP = 1 // how far one tap scrolls: params = percent of the screen
    const val SPEED = 2 // hold-scroll speed: params = 1..4 chevrons
    const val CLOCK = 3 // auto-hide delay: params = seconds, 0 = never
    const val TOOLS = 4 // shape of the reader tools box: params = 0 card, 1 thin bar, 2 capsule, 3 side strip, 4 the original (no tools box)

    /** Hides [group] (it keeps the ids/state code) and puts a row of picture cards right after it. */
    fun bind(
        context: Context,
        group: View,
        kind: Int,
        params: List<Int>,
        labels: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit,
    ) {
        val parent = group.parent as? ViewGroup ?: return
        val tag = "r_choice_row_" + group.id
        (0 until parent.childCount).map { parent.getChildAt(it) }.filter { it.tag == tag }.forEach { parent.removeView(it) }
        group.visibility = View.GONE

        val dp = context.resources.displayMetrics.density
        val accent = context.getResourceColor(com.google.android.material.R.attr.colorSecondary)
        val text = context.getResourceColor(com.google.android.material.R.attr.colorOnBackground)
        val row = LinearLayout(context).apply {
            this.tag = tag
            orientation = LinearLayout.HORIZONTAL
            weightSum = labels.size.toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (6 * dp).toInt()
            }
        }
        val cells = ArrayList<LinearLayout>()
        fun paint(sel: Int) {
            cells.forEachIndexed { i, c ->
                c.background = GradientDrawable().apply {
                    cornerRadius = 12 * dp
                    if (i == sel) {
                        setColor((accent and 0x00FFFFFF) or 0x33000000)
                        setStroke((2 * dp).toInt(), accent)
                    } else {
                        setColor(0x10808080)
                        setStroke((1 * dp).toInt(), 0x33808080)
                    }
                }
            }
        }
        labels.forEachIndexed { i, label ->
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding((4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt())
                isClickable = true
                isFocusable = true
            }
            cell.addView(
                ImageView(context).apply { setImageDrawable(Icon(kind, params[i], accent, text, dp)) },
                LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt()),
            )
            cell.addView(
                TextView(context).apply {
                    this.text = label
                    textSize = 12.5f
                    setTextColor(text)
                    gravity = Gravity.CENTER
                    maxLines = 3
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (4 * dp).toInt() },
            )
            cell.setOnClickListener {
                paint(i)
                onSelect(i)
            }
            row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins((3 * dp).toInt(), 0, (3 * dp).toInt(), 0) })
            cells += cell
        }
        paint(selected.coerceIn(0, labels.lastIndex))
        parent.addView(row, parent.indexOfChild(group) + 1)
    }

    /** A phone outline with what the option does drawn on it. */
    private class Icon(val kind: Int, val v: Int, val accent: Int, val ink: Int, val dp: Float) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(c: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            c.save()
            c.translate(bounds.left.toFloat(), bounds.top.toFloat())
            if (kind == CLOCK) clock(c, w, h) else phone(c, w, h)
            c.restore()
        }

        private fun phone(c: Canvas, w: Float, h: Float) {
            val r = RectF(w * 0.27f, h * 0.04f, w * 0.73f, h * 0.96f)
            val sw = 2f * dp
            // inner "screen" the options work on
            val scr = RectF(r.left + sw * 1.5f, r.top + sw * 3f, r.right - sw * 1.5f, r.bottom - sw * 3f)
            when (kind) {
                SIDE -> {
                    val zoneTop = scr.top + scr.height() * 0.62f
                    p.style = Paint.Style.FILL
                    p.color = (accent and 0x00FFFFFF) or 0x88000000.toInt()
                    val mid = (scr.left + scr.right) / 2f
                    if (v == 0 || v == 2) c.drawRect(mid, zoneTop, scr.right, scr.bottom, p)
                    if (v == 1 || v == 2) c.drawRect(scr.left, zoneTop, mid, scr.bottom, p)
                    p.color = accent
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 1.8f * dp
                    p.strokeCap = Paint.Cap.ROUND
                    if (v == 0 || v == 2) arrowDown(c, (mid + scr.right) / 2f, zoneTop + (scr.bottom - zoneTop) * 0.2f, scr.bottom - 3 * dp)
                    if (v == 1 || v == 2) arrowDown(c, (scr.left + mid) / 2f, zoneTop + (scr.bottom - zoneTop) * 0.2f, scr.bottom - 3 * dp)
                }
                JUMP -> {
                    val f = v / 100f
                    p.style = Paint.Style.FILL
                    p.color = (accent and 0x00FFFFFF) or 0x55000000
                    c.drawRect(scr.left, scr.top, scr.right, scr.top + scr.height() * f, p)
                    p.color = accent
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 1.8f * dp
                    p.strokeCap = Paint.Cap.ROUND
                    val x = (scr.left + scr.right) / 2f
                    arrowDown(c, x, scr.top + 3 * dp, scr.top + scr.height() * f - 1.5f * dp)
                }
                TOOLS -> {
                    p.style = Paint.Style.FILL
                    p.color = (accent and 0x00FFFFFF) or 0x88000000.toInt()
                    when (v) {
                        0 -> c.drawRoundRect(RectF(scr.left + 1.5f * dp, scr.bottom - scr.height() * 0.36f, scr.right - 1.5f * dp, scr.bottom - 2f * dp), 3 * dp, 3 * dp, p)
                        1 -> c.drawRoundRect(RectF(scr.left + 1.5f * dp, scr.bottom - scr.height() * 0.16f, scr.right - 1.5f * dp, scr.bottom - 2f * dp), 3 * dp, 3 * dp, p)
                        2 -> c.drawRoundRect(RectF(scr.left + scr.width() * 0.2f, scr.bottom - scr.height() * 0.17f, scr.right - scr.width() * 0.2f, scr.bottom - 3f * dp), 6 * dp, 6 * dp, p)
                        3 -> {
                            c.drawRoundRect(RectF(scr.right - 7f * dp, scr.top + scr.height() * 0.25f, scr.right - 1.5f * dp, scr.bottom - scr.height() * 0.25f), 3 * dp, 3 * dp, p)
                            c.drawRoundRect(RectF(scr.left + scr.width() * 0.25f, scr.bottom - scr.height() * 0.15f, scr.right - 10f * dp, scr.bottom - 3f * dp), 5 * dp, 5 * dp, p)
                        }
                        else -> { // the original: only the top toolbar, nothing floating
                            p.color = ink
                            p.alpha = 90
                            c.drawRect(scr.left, scr.top, scr.right, scr.top + scr.height() * 0.12f, p)
                            p.alpha = 255
                        }
                    }
                }
                else -> { // SPEED: v chevrons
                    p.color = accent
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2f * dp
                    p.strokeCap = Paint.Cap.ROUND
                    p.strokeJoin = Paint.Join.ROUND
                    val cx = (scr.left + scr.right) / 2f
                    val step = 6.5f * dp
                    val total = step * (v - 1)
                    val top0 = (scr.top + scr.bottom) / 2f - total / 2f - 3 * dp
                    for (i in 0 until v) {
                        val y = top0 + i * step
                        val path = Path().apply { moveTo(cx - 6 * dp, y); lineTo(cx, y + 5 * dp); lineTo(cx + 6 * dp, y) }
                        c.drawPath(path, p)
                    }
                }
            }
            p.color = ink
            p.alpha = 210
            p.style = Paint.Style.STROKE
            p.strokeWidth = sw
            c.drawRoundRect(r, 6 * dp, 6 * dp, p)
            p.alpha = 255
        }

        private fun arrowDown(c: Canvas, x: Float, y0: Float, y1: Float) {
            c.drawLine(x, y0, x, y1, p)
            c.drawLine(x - 4 * dp, y1 - 4 * dp, x, y1, p)
            c.drawLine(x + 4 * dp, y1 - 4 * dp, x, y1, p)
        }

        private fun clock(c: Canvas, w: Float, h: Float) {
            val cx = w / 2f
            val cy = h / 2f
            val rad = w * 0.36f
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f * dp
            p.color = ink
            p.alpha = 210
            c.drawCircle(cx, cy, rad, p)
            p.alpha = 255
            if (v == 0) {
                p.style = Paint.Style.FILL
                p.color = accent
                p.textAlign = Paint.Align.CENTER
                p.textSize = 26f * dp / 1.6f
                c.drawText("∞", cx, cy + 9 * dp, p)
            } else {
                p.style = Paint.Style.FILL
                p.color = (accent and 0x00FFFFFF) or 0x99000000.toInt()
                c.drawArc(RectF(cx - rad, cy - rad, cx + rad, cy + rad), -90f, 360f * v / 10f, true, p)
                p.style = Paint.Style.FILL
                p.color = ink
                p.textAlign = Paint.Align.CENTER
                p.textSize = 13f * dp
                c.drawText("$v", cx, cy + 4.5f * dp, p)
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth() = (56 * dp).toInt()
        override fun getIntrinsicHeight() = (56 * dp).toInt()
    }
}
