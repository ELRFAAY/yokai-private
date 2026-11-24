package eu.kanade.tachiyomi.ui.more.stats

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.bluelinelabs.conductor.Router
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import eu.kanade.tachiyomi.util.view.withFadeTransaction

/**
 * R: the top switcher of the ONE merged screen (profile / statistics / dragon level / outfits). The rank card at the top of the
 * menu opens it; the old separate menu rows are hidden.
 */
object RHubTabs {
    const val PROFILE = 0
    const val STATS = 1
    const val LEVEL = 2
    const val DRESS = 3

    /** Tab the next screen opens on (set before pushing / replacing a controller). */
    @Volatile var pendingTab = PROFILE

    private val labels = arrayOf("الملف الشخصي", "الإحصائيات", "مستوى التنين", "الأزياء")

    /** A small scrollable-free toggle group. [onSelect] gets the tab index; the caller decides how to react. */
    fun build(ctx: Context, selected: Int, onSelect: (Int) -> Unit): View {
        val group = MaterialButtonToggleGroup(ctx).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val ids = IntArray(labels.size) { View.generateViewId() }
        labels.forEachIndexed { i, text ->
            val b = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = ids[i]
                this.text = text
                textSize = 12f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                setPadding(0, 0, 0, 0)
            }
            group.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        group.check(ids[selected.coerceIn(0, ids.lastIndex)])
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) onSelect(ids.indexOf(checkedId))
        }
        return group
    }

    /** Moves to the screen that owns [tab]: tabs 0-1 live in the statistics screen, 2-3 in the dragon screen. */
    fun go(router: Router, tab: Int, fromDragonScreen: Boolean) {
        pendingTab = tab
        val toDragon = tab == LEVEL || tab == DRESS
        if (toDragon == fromDragonScreen) return // same screen: the caller just switches its own content
        router.replaceTopController(
            (if (toDragon) RDragonLevelController() else StatsController()).withFadeTransaction(),
        )
    }
}
