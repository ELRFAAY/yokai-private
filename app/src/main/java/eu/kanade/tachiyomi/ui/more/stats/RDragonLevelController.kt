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
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.scrollViewWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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

    /** R: an LTR island inside the Arabic text, so "9867 / 15000 XP" and "7135 × 1 XP" keep the order they are written in, with Latin digits. */
    private fun ltr(s: String) = "\u2066$s\u2069"

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
        val textColor = ctx.getResourceColor(android.R.attr.textColorPrimary)
        val root = binding.levelContainer
        root.removeAllViews()
        // R: the merged screen: tabs on top, then either the level view or the outfits view
        val c = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val dress = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        fun showTab(t: Int) {
            c.visibility = if (t == RHubTabs.DRESS) View.GONE else View.VISIBLE
            dress.visibility = if (t == RHubTabs.DRESS) View.VISIBLE else View.GONE
        }
        val startTab = RHubTabs.pendingTab.coerceIn(RHubTabs.LEVEL, RHubTabs.DRESS)
        root.addView(
            RHubTabs.build(ctx, startTab) { tab ->
                if (tab == RHubTabs.LEVEL || tab == RHubTabs.DRESS) showTab(tab) else RHubTabs.go(router, tab, fromDragonScreen = true)
            },
        )
        root.addView(c)
        root.addView(dress)
        showTab(startTab)
        val xp = RDragonLevel.xp(d.totalChapters, d.totalReadMs, prefs.rDragonXpBonus().get().toLong())
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
                if (next != null) {
                    "${ltr("${info.xp} / $next XP")}  ·  فاضل ${ltr("${info.left} XP")} للمستوى ${info.level + 1}"
                } else {
                    "${ltr("${info.xp} XP")}  ·  وصلت لأعلى مستوى"
                },
                15f, false, textColor,
            ),
        )
        val hours = d.totalReadMs / 3_600_000f
        c.addView(
            txt(
                "الشباتر: ${ltr("${d.totalChapters} × ${RDragonLevel.XP_PER_CHAPTER} XP")}\n" +
                    "الساعات: ${ltr("${String.format(Locale.US, "%.1f", hours)} × ${RDragonLevel.XP_PER_HOUR} XP")}",
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
                if (!open) col.addView(txt(ltr("${RDragonLevel.STARTS[i]} XP"), 11f, false, textColor, 0.5f))
                cell.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
                row.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            c.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (10 * dp).toInt() })
        }
        buildDress(ctx, dress, info.level, textColor, dp)
    }

    /** R: the outfits ("accessories"): the 21 costumes unlock with the level (costume k opens at level 2 + k/2); tap one to wear it. */
    private fun buildDress(ctx: android.content.Context, box: LinearLayout, level: Int, textColor: Int, dp: Float) {
        box.addView(txt("الأزياء: اختار اللي عايز تلبسه للتنين", 16f, true, textColor).apply { setPadding(0, (8 * dp).toInt(), 0, (10 * dp).toInt()) })
        val names = listOf(
            "alchemist", "astronaut", "bonsai", "builder", "chef", "detective", "doctor", "executive", "explorer", "firefighter", "guard",
            "knight", "magician", "opera", "painter", "pharaoh", "pilot", "pirate", "rockstar", "scribe", "steampunk",
        )
        val labels = listOf(
            "الكيميائي", "رائد الفضاء", "البونساي", "البنّاء", "الشيف", "المحقق", "الدكتور", "المدير", "المستكشف", "الإطفائي", "الحارس",
            "الفارس", "الساحر", "الأوبرا", "الرسام", "الفرعون", "الطيار", "القرصان", "نجم الروك", "الكاتب", "ستيم بانك",
        )
        // entries: (outfit name or "" = plain stage look, picture, label, unlock level)
        class E(val name: String, val pic: Int, val label: String, val need: Int)
        val entries = ArrayList<E>()
        entries += E("", RDragonLevel.PICTURES[(level - 1).coerceIn(0, 11)], "الشكل العادي", 1)
        names.forEachIndexed { k, n ->
            val id = ctx.resources.getIdentifier("r_avc_$n", "drawable", ctx.packageName)
            if (id != 0) entries += E(n, id, labels[k], 2 + k / 2)
        }
        val cols = 3
        val rowsViews = ArrayList<LinearLayout>()
        fun refresh() {
            val cur = prefs.rDragonOutfit().get()
            rowsViews.forEach { it.removeAllViews() }
            rowsViews.clear()
        }
        val selectedBorder = ctx.getResourceColor(com.google.android.material.R.attr.colorSecondary)
        for (rowStart in entries.indices step cols) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; weightSum = cols.toFloat() }
            for (i in rowStart until minOf(rowStart + cols, entries.size)) {
                val e = entries[i]
                val open = level >= e.need
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(4, 4, 4, 4) }
                val img = ImageView(ctx).apply {
                    setImageResource(e.pic)
                    adjustViewBounds = true
                    if (!open) colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f); setScale(0.25f, 0.25f, 0.25f, 1f) })
                }
                col.addView(img, LinearLayout.LayoutParams((88 * dp).toInt(), (88 * dp).toInt()))
                val cap = txt(if (open) e.label else "${e.label} 🔒 مستوى ${e.need}", 11f, open, textColor, if (open) 1f else 0.55f)
                col.addView(cap)
                fun mark() {
                    val on = open && prefs.rDragonOutfit().get() == e.name
                    col.setBackgroundColor(if (on) (selectedBorder and 0x00FFFFFF) or 0x33000000 else 0)
                }
                mark()
                col.setOnClickListener {
                    if (!open) {
                        android.widget.Toast.makeText(ctx, "يتفتح عند مستوى ${e.need}", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        prefs.rDragonOutfit().set(e.name)
                        // repaint every cell's highlight
                        for (r in 0 until box.childCount) (box.getChildAt(r) as? LinearLayout)?.let { rv ->
                            for (k in 0 until rv.childCount) (rv.getChildAt(k) as? LinearLayout)?.tag?.let { t -> (t as? Runnable)?.run() }
                        }
                    }
                }
                col.tag = Runnable { mark() }
                row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            box.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (8 * dp).toInt() })
        }
        refresh()
    }
}
