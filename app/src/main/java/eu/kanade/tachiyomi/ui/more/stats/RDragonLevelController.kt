package eu.kanade.tachiyomi.ui.more.stats

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import eu.kanade.tachiyomi.databinding.RDragonLevelControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.BaseLegacyController
import eu.kanade.tachiyomi.util.RDragonLevel
import eu.kanade.tachiyomi.util.view.scrollViewWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * R: the little dragon's level screen (3-dot menu > "مستوى التنين"): the current level, the xp to the next one, how the xp is
 * counted, and all 12 stages (the ones not unlocked yet are dark with the xp that opens them).
 */
class RDragonLevelController : BaseLegacyController<RDragonLevelControllerBinding>() {

    private val prefs: eu.kanade.tachiyomi.data.preference.PreferencesHelper by uy.kohesive.injekt.injectLazy()

    override fun getTitle() = "مستوى التنين"

    override fun createBinding(inflater: LayoutInflater) = RDragonLevelControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        scrollViewWith(binding.levelScrollView, true)
        viewScope.launch {
            val d = withContext(Dispatchers.IO) { runCatching { StatsPresenter().getRData() }.getOrNull() }
            if (d != null) show(d)
        }
    }

    private fun txt(text: String, sp: Float, bold: Boolean = false, color: Int = Color.WHITE, alpha: Float = 1f) =
        TextView(binding.root.context).apply {
            this.text = text
            textSize = sp
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setTextColor(color)
            this.alpha = alpha
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

    private fun show(d: RStatsData) {
        val ctx = binding.root.context
        val dp = ctx.resources.displayMetrics.density
        val textColor = eu.kanade.tachiyomi.util.system.getResourceColor(android.R.attr.textColorPrimary)
        val c = binding.levelContainer
        c.removeAllViews()
        val xp = RDragonLevel.xp(d.totalChapters, d.totalReadMs)
        val info = RDragonLevel.info(xp)
        prefs.rDragonXp().set(xp.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

        c.addView(
            ImageView(ctx).apply {
                setImageResource(RDragonLevel.PICTURES[info.level - 1])
                adjustViewBounds = true
            },
            LinearLayout.LayoutParams((220 * dp).toInt(), (250 * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (8 * dp).toInt()
            },
        )
        c.addView(txt("المستوى ${info.level} من ${RDragonLevel.STARTS.size}", 24f, true, textColor))
        c.addView(
            ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progress = (info.progress * 1000).toInt()
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (14 * dp).toInt()).apply {
                topMargin = (12 * dp).toInt()
                bottomMargin = (6 * dp).toInt()
            },
        )
        val next = info.nextAt
        c.addView(
            txt(
                if (next != null) "${info.xp} / $next XP  ·  فاضل ${info.left} XP للمستوى ${info.level + 1}" else "${info.xp} XP  ·  وصلت لأعلى مستوى",
                15f, false, textColor,
            ),
        )
        val hours = d.totalReadMs / 3_600_000f
        c.addView(
            txt(
                "الشباتر: ${d.totalChapters} × ${RDragonLevel.XP_PER_CHAPTER} XP\nالساعات: ${"%.1f".format(hours)} × ${RDragonLevel.XP_PER_HOUR} XP",
                13f, false, textColor, 0.7f,
            ).apply { setPadding(0, (10 * dp).toInt(), 0, (16 * dp).toInt()) },
        )
        c.addView(txt("كل المراحل", 18f, true, textColor).apply { setPadding(0, 0, 0, (8 * dp).toInt()) })

        val cols = 3
        for (rowStart in RDragonLevel.PICTURES.indices step cols) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; weightSum = cols.toFloat() }
            for (i in rowStart until minOf(rowStart + cols, RDragonLevel.PICTURES.size)) {
                val level = i + 1
                val open = level <= info.level
                val cell = FrameLayout(ctx)
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
                col.addView(
                    ImageView(ctx).apply {
                        setImageResource(RDragonLevel.PICTURES[i])
                        adjustViewBounds = true
                        if (!open) {
                            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f); setScale(0.25f, 0.25f, 0.25f, 1f) })
                        }
                    },
                    LinearLayout.LayoutParams((96 * dp).toInt(), (110 * dp).toInt()),
                )
                col.addView(txt(if (open) "مستوى $level" else "مستوى $level 🔒", 12f, open, textColor, if (open) 1f else 0.6f))
                if (!open) col.addView(txt("${RDragonLevel.STARTS[i]} XP", 11f, false, textColor, 0.5f))
                cell.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
                row.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            c.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (10 * dp).toInt() })
        }
    }
}
