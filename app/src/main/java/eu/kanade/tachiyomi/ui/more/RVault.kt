package eu.kanade.tachiyomi.ui.more

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.biometric.BiometricPrompt
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.RHiddenLists
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.startAuthentication
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import yokai.domain.category.interactor.GetCategories
import android.R as AR

/**
 * R: the hidden-files vault ("الملفات"). Opened from the library's filter sheet; it asks for fingerprint or the
 * phone's pattern / PIN, then lists the hidden library lists, each opening in the normal library look.
 */
class RVault(private val activity: MainActivity) {

    private val preferences: PreferencesHelper by injectLazy()
    private val getCategories: GetCategories by injectLazy()

    /** Asks for fingerprint / pattern / PIN, then shows the vault. */
    fun open() {
        if (!activity.isAuthenticationSupported()) {
            activity.toast("فعّل بصمة أو قفل شاشة (نمط / رقم) في الموبايل الأول")
            return
        }
        activity.startAuthentication(
            VAULT_NAME,
            "بالبصمة أو النمط",
            confirmationRequired = false,
            callback = object : AuthenticatorUtil.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    showVault(activity)
                }
            },
        )
    }

    /** The vault: every hidden list with a down arrow; tapping one opens its manga in the normal library look. */
    private fun showVault(activity: MainActivity) {
        activity.lifecycleScope.launch {
            val all = getCategories.await().filter { it.id != null && it.id != 0 }
            val hiddenIds = RHiddenLists.hiddenListIds(preferences)
            val hiddenLists = all.filter { it.id in hiddenIds }
            val builder = MaterialAlertDialogBuilder(activity).setTitle(VAULT_NAME)
            if (hiddenLists.isEmpty()) {
                builder.setMessage("مفيش حاجة هنا لسه. دوس \"اختيار التصنيفات\" عشان تخفي تصنيف.")
            } else {
                val adapter = object : ArrayAdapter<String>(
                    activity,
                    AR.layout.simple_list_item_1,
                    hiddenLists.map { it.name },
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val row = super.getView(position, convertView, parent) as TextView
                        row.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand_more_24dp, 0)
                        TextViewCompat.setCompoundDrawableTintList(row, ColorStateList.valueOf(VAULT_GREEN))
                        return row
                    }
                }
                builder.setAdapter(adapter) { _, which ->
                    val list = hiddenLists[which]
                    list.id?.let { activity.showRVaultList(list.name, it) }
                }
            }
            builder
                .setNeutralButton("اختيار التصنيفات") { _, _ -> showChooseListsDialog(activity) }
                .setNegativeButton("إغلاق", null)
                .show()
        }
    }

    /** Check a list to hide it; un-check it to take it out (it comes back to the library, Updates and History). */
    private fun showChooseListsDialog(activity: MainActivity) {
        activity.lifecycleScope.launch {
            val lists = getCategories.await().filter { it.id != null && it.id != 0 }
            if (lists.isEmpty()) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle(VAULT_NAME)
                    .setMessage("مفيش تصنيفات في المكتبة لسه.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@launch
            }
            val hidden = preferences.rHiddenLists().get()
            val checked = BooleanArray(lists.size) { lists[it].id.toString() in hidden }
            MaterialAlertDialogBuilder(activity)
                .setTitle("علّم التصنيف عشان يتخفى")
                .setMultiChoiceItems(lists.map { it.name }.toTypedArray(), checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    // keep ids of lists that no longer exist untouched; only change the ones shown here
                    val shownIds = lists.map { it.id.toString() }.toSet()
                    val keep = preferences.rHiddenLists().get().filterNot { it in shownIds }
                    val now = lists.filterIndexed { i, _ -> checked[i] }.map { it.id.toString() }
                    preferences.rHiddenLists().set((keep + now).toMutableSet())
                }
                .setNeutralButton("إخراج الكل") { _, _ ->
                    preferences.rHiddenLists().set(mutableSetOf())
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    companion object {
        const val VAULT_NAME = "الملفات"
        val VAULT_GREEN = Color.rgb(76, 175, 80)
    }
}
