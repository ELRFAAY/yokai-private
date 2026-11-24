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
import eu.kanade.tachiyomi.util.RDragonLook
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

        val green = 0xFF2E7D32.toInt()
        val greenDark = 0xFF14341A.toInt()
        val gold = 0xFFFFD54F.toInt()
        fun bg(top: Int, bottom: Int, r: Float, stroke: Int = 0) = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom),
        ).apply { cornerRadius = r * dp; if (stroke != 0) setStroke((2 * dp).toInt(), stroke) }
        fun lp(w: Int, h: Int, top: Int = 0, bottom: Int = 0) =
            LinearLayout.LayoutParams(w, h).apply { topMargin = (top * dp).toInt(); bottomMargin = (bottom * dp).toInt() }
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

        // hero card: the big picture of the chosen stage, its name, the wear button, then level + animated xp bar
        val hero = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = bg(0xFF1F5A26.toInt(), greenDark, 24f)
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }
        val heroImg = ImageView(ctx).apply { adjustViewBounds = true }
        hero.addView(heroImg, lp((200 * dp).toInt(), (230 * dp).toInt()))
        val heroName = txt("", 20f, true, Color.WHITE)
        hero.addView(heroName)
        val heroSub = txt("", 12f, false, Color.WHITE, 0.75f)
        hero.addView(heroSub)
        val wear = android.widget.Button(ctx).apply {
            setTextColor(greenDark); isAllCaps = false
            background = bg(gold, 0xFFFFB300.toInt(), 22f)
            setPadding((22 * dp).toInt(), 0, (22 * dp).toInt(), 0)
        }
        hero.addView(wear, lp(WRAP, (42 * dp).toInt(), 8, 0).apply { gravity = Gravity.CENTER_HORIZONTAL })
        c.addView(hero, lp(MATCH, WRAP, 8, 12))

        var preview = info.level
        fun refreshHero() {
            heroImg.setImageResource(RDragonLevel.PICTURES[preview - 1])
            val locked = preview > info.level
            heroImg.colorFilter = if (locked) ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f); setScale(0.3f, 0.3f, 0.3f, 1f) }) else null
            heroName.text = "المرحلة $preview"
            val worn = prefs.rDragonOutfit().get() == RDragonLook.stageValue(preview)
            heroSub.text = if (locked) "تتفتح عند ${ltr("${RDragonLevel.STARTS[preview - 1]} XP")}" else if (worn) "لابسها دلوقتي" else if (preview == info.level) "مرحلتك الحالية" else "مرحلة فتحتها"
            wear.visibility = if (locked) View.GONE else View.VISIBLE
            wear.text = if (worn) "✓ لابسه (اضغط للشكل العادي)" else "ألبسه للتنين"
        }
        wear.setOnClickListener {
            val v = RDragonLook.stageValue(preview)
            prefs.rDragonOutfit().set(if (prefs.rDragonOutfit().get() == v) "" else v)
            refreshHero()
            android.widget.Toast.makeText(ctx, "اتغير شكل التنين", android.widget.Toast.LENGTH_SHORT).show()
        }

        // level + xp: the number counts up and the bar fills
        val lvText = txt("المستوى ${info.level} من ${RDragonLevel.STARTS.size}", 22f, true, textColor)
        c.addView(lvText)
        val xpBar = eu.kanade.tachiyomi.ui.more.RXpBar(ctx, textColor)
        c.addView(xpBar, lp(MATCH, WRAP, 10, 6))
        xpBar.bind(info)
        val next = info.nextAt
        c.addView(
            txt(
                if (next != null) "فاضل ${ltr("${info.left} XP")} للمستوى ${info.level + 1}" else "وصلت لأعلى مستوى",
                13f, false, textColor, 0.8f,
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
        c.addView(txt("كل المراحل (اضغط على واحدة تشوفها)", 16f, true, textColor).apply { setPadding(0, 0, 0, (8 * dp).toInt()) })

        // stage cards (3 per row); the picked one has a gold ring and shows in the hero
        val cards = ArrayList<Pair<Int, LinearLayout>>()
        fun refreshCards() {
            cards.forEach { (lvl, card) ->
                val open = lvl <= info.level
                card.background = bg(if (open) 0xFF245F2C.toInt() else 0xFF2A2A2A.toInt(), if (open) greenDark else 0xFF161616.toInt(), 16f, if (lvl == preview) gold else 0)
            }
        }
        val cols = 3
        for (rowStart in RDragonLevel.PICTURES.indices step cols) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; weightSum = cols.toFloat() }
            for (i in rowStart until minOf(rowStart + cols, RDragonLevel.PICTURES.size)) {
                val level = i + 1
                val open = level <= info.level
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(6, 8, 6, 8) }
                col.addView(
                    ImageView(ctx).apply {
                        setImageResource(RDragonLevel.PICTURES[i])
                        adjustViewBounds = true
                        if (!open) colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f); setScale(0.3f, 0.3f, 0.3f, 1f) })
                    },
                    lp(MATCH, (92 * dp).toInt()),
                )
                col.addView(txt(if (open) "مستوى $level" else "🔒 مستوى $level", 12f, open, Color.WHITE, if (open) 1f else 0.6f))
                if (!open) col.addView(txt(ltr("${RDragonLevel.STARTS[i]} XP"), 10f, false, Color.WHITE, 0.5f))
                col.setOnClickListener { preview = level; refreshHero(); refreshCards() }
                cards += level to col
                row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt()) })
            }
            c.addView(row, lp(MATCH, WRAP, 0, 4))
        }
        refreshHero(); refreshCards()
        buildDress(ctx, dress, info.level, textColor, dp)
    }

    /** R: the outfits: full-body pictures (no round crop), all unlocked; tap = wear it on the dragon (every place that draws it). */
    private fun buildDress(ctx: android.content.Context, box: LinearLayout, level: Int, textColor: Int, dp: Float) {
        box.addView(txt("الأزياء: اضغط على واحد يلبسه التنين", 16f, true, textColor).apply { setPadding(0, (8 * dp).toInt(), 0, (10 * dp).toInt()) })
        val names = listOf(
            "alchemist", "astronaut", "bonsai", "builder", "chef", "detective", "doctor", "executive", "explorer", "firefighter", "guard",
            "knight", "magician", "opera", "painter", "pharaoh", "pilot", "pirate", "rockstar", "scribe", "steampunk",
        )
        val labels = listOf(
            "الكيميائي", "رائد الفضاء", "البونساي", "البنّاء", "الشيف", "المحقق", "الدكتور", "المدير", "المستكشف", "الإطفائي", "الحارس",
            "الفارس", "الساحر", "الأوبرا", "الرسام", "الفرعون", "الطيار", "القرصان", "نجم الروك", "الكاتب", "ستيم بانك",
        )
        class E(val name: String, val pic: Int, val label: String)
        val entries = ArrayList<E>()
        entries += E("", RDragonLevel.PICTURES[(level - 1).coerceIn(0, 11)], "الشكل العادي")
        names.forEachIndexed { k, n ->
            val id = ctx.resources.getIdentifier("r_costume_$n", "drawable", ctx.packageName)
            if (id != 0) entries += E(n, id, labels[k])
        }
        val gold = 0xFFFFD54F.toInt()
        val cells = ArrayList<Pair<E, LinearLayout>>()
        fun repaint() {
            val cur = prefs.rDragonOutfit().get().let { if (RDragonLook.isStage(it)) "" else it }
            cells.forEach { (e, cell) ->
                cell.background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF245F2C.toInt(), 0xFF14341A.toInt()),
                ).apply {
                    cornerRadius = 16 * dp
                    if (e.name == cur) setStroke((2 * dp).toInt(), gold)
                }
            }
        }
        val cols = 3
        for (rowStart in entries.indices step cols) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; weightSum = cols.toFloat() }
            for (i in rowStart until minOf(rowStart + cols, entries.size)) {
                val e = entries[i]
                val cell = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(6, 8, 6, 8) }
                cell.addView(
                    ImageView(ctx).apply { setImageResource(e.pic); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (110 * dp).toInt()),
                )
                cell.addView(txt(e.label, 11f, true, Color.WHITE))
                cell.setOnClickListener {
                    prefs.rDragonOutfit().set(e.name)
                    repaint()
                    android.widget.Toast.makeText(ctx, "اتغير شكل التنين", android.widget.Toast.LENGTH_SHORT).show()
                }
                cells += e to cell
                row.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt()) })
            }
            box.addView(row)
        }
        repaint()
    }
}
