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
                textDirection = TEXT_DIRECTION_LTR
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

        for (g in RShop.Group.values()) {
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(ctx, 14f), dp(ctx, 12f), dp(ctx, 14f), dp(ctx, 8f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(ctx, 14f).toFloat()
                    setColor(0x14808080)
                    setStroke(dp(ctx, 1f), 0x33808080)
                }
            }
            card.addView(
                TextView(ctx).apply { text = g.label; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 0, 0, dp(ctx, 6f)) },
            )
            for (it in RShop.items.filter { i -> i.group == g }) {
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                if (it.color != 0) {
                    row.addView(
                        android.view.View(ctx).apply {
                            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(it.color) }
                        },
                        LinearLayout.LayoutParams(dp(ctx, 18f), dp(ctx, 18f)).apply { marginEnd = dp(ctx, 8f) },
                    )
                }
                val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                texts.addView(TextView(ctx).apply { text = it.name; textSize = 14.5f; setTypeface(typeface, Typeface.BOLD) })
                texts.addView(TextView(ctx).apply { text = it.desc; textSize = 11.5f; alpha = 0.7f })
                row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val owned = g != RShop.Group.BOOST && RShop.owned(prefs, it.id)
                val maxed = it.id == "fav" && prefs.rExtraFav().get() >= RShop.MAX_FAV_PACKS
                val btn = MaterialButton(ctx).apply {
                    isAllCaps = false
                    textSize = 12f
                    insetTop = 0
                    insetBottom = 0
                    when {
                        maxed -> { text = "الحد الأقصى"; isEnabled = false }
                        owned -> {
                            val on = RShop.equipped(prefs, it.id)
                            text = if (on) "لابسه ✓" else "البس"
                            setOnClickListener { _ -> RShop.toggleEquip(prefs, it); refresh() }
                        }
                        else -> {
                            text = "${it.price} عملة"
                            setOnClickListener { _ ->
                                Toast.makeText(ctx, RShop.buy(prefs, it), Toast.LENGTH_SHORT).show()
                                refresh()
                            }
                        }
                    }
                }
                row.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ctx, 40f)))
                card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(ctx, 8f) })
            }
            root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(ctx, 10f) })
        }
    }
}
