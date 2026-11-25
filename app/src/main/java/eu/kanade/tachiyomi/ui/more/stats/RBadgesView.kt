package eu.kanade.tachiyomi.ui.more.stats

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.RBadges
import eu.kanade.tachiyomi.util.system.dpToPx

/**
 * R: the badges card of the profile. Header "الأوسمة n/total" + a "جمع الكل" button (collects every ready badge at once and says how many
 * badges / how much XP). Below, the badges in a 3-column grid: locked = grey, ready = glowing gold (tap collects it), collected = green tick.
 * Only the next few of each kind are listed until "عرض الكل" is pressed, so the card stays short.
 */
class RBadgesView(
    context: Context,
    private val prefs: PreferencesHelper,
    private val data: RStatsData,
    private val onXpChanged: () -> Unit,
) : LinearLayout(context) {
    private var expanded = false

    init {
        orientation = VERTICAL
        layoutDirection = LAYOUT_DIRECTION_RTL
        background = GradientDrawable().apply { setColor(0x1F808080); cornerRadius = 14.dpToPx.toFloat() }
        setPadding(14.dpToPx, 12.dpToPx, 14.dpToPx, 12.dpToPx)
        render()
    }

    private fun txt(t: String, sp: Float, bold: Boolean = false, color: Int = 0xFFFFFFFF.toInt()) = TextView(context).apply {
        text = t
        textSize = sp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun render() {
        removeAllViews()
        val ready = RBadges.ready(prefs, data)
        val total = RBadges.ALL.size
        val got = RBadges.ALL.count { RBadges.isClaimed(prefs, it) }

        val head = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(txt("الأوسمة  ⁦$got/$total⁩", 17f, true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val all = com.google.android.material.button.MaterialButton(context).apply {
            val xp = ready.sumOf { it.reward }
            text = if (ready.isEmpty()) "جمع الكل" else "جمع الكل (⁦+$xp XP⁩)"
            isEnabled = ready.isNotEmpty()
            setOnClickListener {
                val c = RBadges.claim(prefs, ready)
                Toast.makeText(context, "جمعت ${c.count} وسام: ⁦+${c.xp} XP⁩", Toast.LENGTH_LONG).show()
                onXpChanged()
                render()
            }
        }
        head.addView(all)
        addView(head)

        // the badges to list: every ready one, plus (collapsed) the next locked one of each kind, or everything when expanded
        val list = if (expanded) {
            RBadges.ALL
        } else {
            val nextLocked = RBadges.Kind.values().mapNotNull { k -> RBadges.ALL.firstOrNull { it.kind == k && !RBadges.unlocked(data, it) } }
            RBadges.ALL.filter { it in ready || it in nextLocked }
        }
        val grid = GridLayout(context).apply { columnCount = 3 }
        list.forEach { b -> grid.addView(cell(b), GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
            width = 0
            setMargins(3.dpToPx, 3.dpToPx, 3.dpToPx, 3.dpToPx)
        }) }
        addView(grid, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8.dpToPx })

        addView(
            txt(if (expanded) "إخفاء" else "عرض كل الأوسمة", 13f, false, 0xFF64B5F6.toInt()).apply {
                gravity = Gravity.CENTER
                setPadding(0, 8.dpToPx, 0, 4.dpToPx)
                setOnClickListener { expanded = !expanded; render() }
            },
        )
    }

    private fun cell(b: RBadges.Badge): LinearLayout {
        val unlocked = RBadges.unlocked(data, b)
        val claimed = RBadges.isClaimed(prefs, b)
        val box = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(6.dpToPx, 10.dpToPx, 6.dpToPx, 10.dpToPx)
            background = GradientDrawable().apply {
                cornerRadius = 12.dpToPx.toFloat()
                when {
                    claimed -> { setColor(0x332E7D45); setStroke(1.dpToPx, 0xFF2E7D45.toInt()) }
                    unlocked -> { setColor(0x44FFC107); setStroke(2.dpToPx, 0xFFFFC107.toInt()) }
                    else -> setColor(0x22808080)
                }
            }
            alpha = if (unlocked) 1f else 0.55f
        }
        val icon = when (b.kind) {
            RBadges.Kind.CHAPTERS -> "📖"
            RBadges.Kind.HOURS -> "⏳"
            RBadges.Kind.STREAK -> "🔥"
        }
        box.addView(txt(if (unlocked) icon else "🔒", 24f).apply { gravity = Gravity.CENTER })
        box.addView(txt(b.title, 12f, true).apply { gravity = Gravity.CENTER })
        box.addView(
            txt(
                when {
                    claimed -> "✔ اتجمّع"
                    unlocked -> "اجمع ⁦+${b.reward} XP⁩"
                    else -> "⁦${RBadges.current(data, b.kind)}/${b.goal}⁩"
                },
                11f, false, if (unlocked && !claimed) 0xFFFFC107.toInt() else 0xFFB0BEC5.toInt(),
            ).apply { gravity = Gravity.CENTER },
        )
        box.setOnClickListener {
            if (unlocked && !claimed) {
                val c = RBadges.claim(prefs, listOf(b))
                Toast.makeText(context, "جمعت ⁦+${c.xp} XP⁩", Toast.LENGTH_SHORT).show()
                onXpChanged()
                render()
            } else if (!unlocked) {
                Toast.makeText(context, b.condition, Toast.LENGTH_SHORT).show()
            }
        }
        return box
    }
}
