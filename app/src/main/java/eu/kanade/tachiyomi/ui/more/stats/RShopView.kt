package eu.kanade.tachiyomi.ui.more.stats

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.more.RXpBadge
import eu.kanade.tachiyomi.util.RDragonLevel
import eu.kanade.tachiyomi.util.RShop

/** R: the shop tab. Pays with gold coins (1 per 1000 XP). Rebuilds itself after each buy / equip. */
object RShopView {
    private var selectedGroup: RShop.Group? = null
    private fun dp(ctx: Context, v: Float) = (v * ctx.resources.displayMetrics.density).toInt()

    fun build(ctx: Context, root: LinearLayout, prefs: PreferencesHelper, onChanged: () -> Unit) {
        root.removeAllViews()
        fun refresh() {
            onChanged()
            build(ctx, root, prefs, onChanged)
        }

        // wallet: coins + what is in the bag
        val wallet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14f), dp(ctx, 12f), dp(ctx, 14f), dp(ctx, 12f))
            background = GradientDrawable().apply { cornerRadius = dp(ctx, 14f).toFloat(); setColor(0xFF1F4D2E.toInt()) }
        }
        val coinRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        coinRow.addView(ImageView(ctx).apply { setImageDrawable(RXpBadge.coin(ctx)) }, LinearLayout.LayoutParams(dp(ctx, 32f), dp(ctx, 32f)))
        coinRow.addView(
            TextView(ctx).apply {
                text = "${RDragonLevel.full(RShop.balance(prefs))} عملة"
                setTextColor(0xFFFFD866.toInt())
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                textDirection = android.view.View.TEXT_DIRECTION_LTR
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(ctx, 8f) },
        )
        wallet.addView(coinRow)
        val boostMin = (RShop.boostLeftMs(prefs) / 60000L).toInt()
        val bag = buildList {
            if (boostMin > 0) add("مضاعف ×2 شغال: باقي $boostMin دقيقة")
            if (prefs.rComboShields().get() > 0) add("حماية كومبو: ${prefs.rComboShields().get()}")
            add("خانات المفضلة: ${10 + 5 * prefs.rExtraFav().get()}")
        }
        wallet.addView(
            TextView(ctx).apply { text = bag.joinToString("  •  "); setTextColor(0xCCFFFFFF.toInt()); textSize = 12f },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(ctx, 4f) },
        )
        root.addView(wallet, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(ctx, 10f) })

        // category chips (the filter is remembered while the screen is open)
        val chips = android.widget.HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
        val chipRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL }
        fun chip(label: String, selected: Boolean, onClick: () -> Unit) {
            chipRow.addView(
                TextView(ctx).apply {
                    text = label
                    textSize = 13f
                    setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
                    setTextColor(if (selected) android.graphics.Color.BLACK else 0xFFDDDDDD.toInt())
                    setPadding(dp(ctx, 14f), dp(ctx, 7f), dp(ctx, 14f), dp(ctx, 7f))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(ctx, 20f).toFloat()
                        setColor(if (selected) 0xFFFFC53D.toInt() else 0x33808080)
                    }
                    setOnClickListener { onClick() }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(ctx, 6f) },
            )
        }
        chip("الكل", selectedGroup == null) { selectedGroup = null; refresh() }
        for (g in RShop.Group.values()) chip(g.label, selectedGroup == g) { selectedGroup = g; refresh() }
        chips.addView(chipRow)
        root.addView(chips, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(ctx, 10f) })

        // 2-column grid of item cards: big picture, name, short line, price / equip button
        val list = RShop.items.filter { selectedGroup == null || it.group == selectedGroup }
        var rowView: LinearLayout? = null
        list.forEachIndexed { index, it ->
            if (index % 2 == 0) {
                rowView = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f; layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR }
                root.addView(rowView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(ctx, 10f) })
            }
            val owned = it.group != RShop.Group.BOOST && RShop.owned(prefs, it.id)
            val maxed = it.id == "fav" && prefs.rExtraFav().get() >= RShop.MAX_FAV_PACKS
            val equipped = RShop.equipped(prefs, it.id)
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
                setPadding(dp(ctx, 10f), dp(ctx, 12f), dp(ctx, 10f), dp(ctx, 10f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(ctx, 16f).toFloat()
                    setColor(0x14808080)
                    setStroke(dp(ctx, if (equipped) 2f else 1f), if (equipped) 0xFFFFC53D.toInt() else 0x33808080)
                }
            }
            // picture: r_shop_<id> (or the cobra picture); the group's emoji until the artwork exists
            val picName = if (it.pic.isNotEmpty()) it.pic else "r_shop_${it.id}"
            val rid = ctx.resources.getIdentifier(picName, "drawable", ctx.packageName)
            if (rid != 0) {
                card.addView(
                    ImageView(ctx).apply { setImageResource(rid); scaleType = ImageView.ScaleType.FIT_CENTER },
                    LinearLayout.LayoutParams(dp(ctx, 96f), dp(ctx, 96f)),
                )
            } else {
                card.addView(
                    TextView(ctx).apply {
                        text = when (it.group) {
                            RShop.Group.BOOST -> "⚡"
                            RShop.Group.FRAME -> "🖼️"
                            RShop.Group.COBRA -> "🐍"
                            RShop.Group.TITLE -> "🏷️"
                            RShop.Group.COUNTER -> "🪙"
                        }
                        textSize = 38f
                        gravity = Gravity.CENTER
                        if (it.color != 0) setTextColor(it.color)
                    },
                    LinearLayout.LayoutParams(dp(ctx, 96f), dp(ctx, 96f)),
                )
            }
            card.addView(TextView(ctx).apply { text = it.name; textSize = 14f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER })
            card.addView(
                TextView(ctx).apply { text = it.desc; textSize = 11f; alpha = 0.7f; gravity = Gravity.CENTER; minLines = 2; maxLines = 3 },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(ctx, 2f) },
            )
            val btn = MaterialButton(ctx).apply {
                isAllCaps = false
                textSize = 13f
                insetTop = 0
                insetBottom = 0
                when {
                    maxed -> { text = "الحد الأقصى"; isEnabled = false }
                    owned -> {
                        text = if (equipped) "لابسه ✓" else "البس"
                        setOnClickListener { _ -> RShop.toggleEquip(prefs, it); refresh() }
                    }
                    else -> {
                        val missing = it.price - RShop.balance(prefs)
                        text = "${it.price}"
                        icon = RXpBadge.coin(ctx)
                        iconTint = null
                        iconSize = dp(ctx, 20f)
                        if (missing > 0) {
                            // not enough coins: the button is dimmed and says how many are missing
                            alpha = 0.55f
                            setOnClickListener { _ -> Toast.makeText(ctx, "ناقصك $missing عملة", Toast.LENGTH_SHORT).show() }
                        } else {
                            // ask first, so a stray tap never spends coins
                            setOnClickListener { _ ->
                                com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                                    .setTitle(it.name)
                                    .setMessage("هتدفع ${it.price} عملة، ويفضل معاك ${RDragonLevel.full(RShop.balance(prefs) - it.price)}. تكمّل؟")
                                    .setPositiveButton("اشتري") { _, _ ->
                                        Toast.makeText(ctx, RShop.buy(prefs, it), Toast.LENGTH_SHORT).show()
                                        refresh()
                                    }
                                    .setNegativeButton("لأ", null)
                                    .show()
                            }
                        }
                    }
                }
            }
            card.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 42f)).apply { topMargin = dp(ctx, 8f) })
            rowView?.addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(ctx, 4f); marginEnd = dp(ctx, 4f) })
        }
    }
}
