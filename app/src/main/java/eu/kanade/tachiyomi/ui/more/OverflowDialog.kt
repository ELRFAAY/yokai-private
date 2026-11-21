package eu.kanade.tachiyomi.ui.more

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.biometric.BiometricPrompt
import androidx.core.graphics.ColorUtils
import androidx.core.widget.TextViewCompat
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import yokai.i18n.MR
import yokai.util.lang.getString
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.core.preference.toggle
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.TachiOverflowLayoutBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.RHiddenLists
import eu.kanade.tachiyomi.util.lang.addBetaTag
import eu.kanade.tachiyomi.util.lang.withSubtitle
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.startAuthentication
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import yokai.domain.category.interactor.GetCategories
import android.R as AR

class OverflowDialog(activity: MainActivity) : Dialog(activity, R.style.OverflowDialogTheme) {

    val binding = TachiOverflowLayoutBinding.inflate(activity.layoutInflater, null, false)
    val preferences: PreferencesHelper by injectLazy()
    private val getCategories: GetCategories by injectLazy()

    init {
        setContentView(binding.root)

        binding.overflowCardView.backgroundTintList = ColorStateList.valueOf(
            ColorUtils.blendARGB(
                activity.getResourceColor(R.attr.background),
                activity.getResourceColor(R.attr.colorSecondary),
                0.075f,
            ),
        )
        binding.touchOutside.setOnClickListener {
            cancel()
        }
        val incogText = context.getString(MR.strings.incognito_mode)
        with(binding.incognitoModeItem) {
            val titleText = context.getString(
                if (preferences.incognitoMode().get()) {
                    MR.strings.turn_off_
                } else {
                    MR.strings.turn_on_
                },
                incogText,
            )
            val subtitleText = context.getString(MR.strings.pauses_reading_history)
            text = titleText.withSubtitle(context, subtitleText)
            setIcon(
                if (preferences.incognitoMode().get()) {
                    R.drawable.ic_incognito_24dp
                } else {
                    R.drawable.ic_glasses_24dp
                },
            )
            setOnClickListener {
                preferences.incognitoMode().toggle()
                val incog = preferences.incognitoMode().get()
                val newTitle = context.getString(
                    if (incog) {
                        MR.strings.turn_off_
                    } else {
                        MR.strings.turn_on_
                    },
                    incogText,
                )
                text = newTitle.withSubtitle(context, subtitleText)
                val drawable = AnimatedVectorDrawableCompat.create(
                    context,
                    if (incog) {
                        R.drawable.anim_read_to_incog
                    } else {
                        R.drawable.anim_incog_to_read
                    },
                )
                setIcon(drawable)
                (getIcon() as? AnimatedVectorDrawableCompat)?.start()
            }
        }
        binding.settingsItem.setOnClickListener {
            activity.showSettings()
            dismiss()
        }

        binding.helpItem.setOnClickListener {
            activity.openInBrowser(URL_HELP)
            dismiss()
        }

        val vName = "v${BuildConfig.VERSION_NAME}".substringBefore("-")
        val newVName = buildSpannedString {
            color(context.getResourceColor(AR.attr.textColorSecondary)) {
                append(vName)
            }
            if (BuildConfig.BETA) {
                append("".addBetaTag(context, false))
            }
        }

        binding.aboutItem.text = context.getString(MR.strings.about).withSubtitle(newVName)

        binding.aboutItem.setOnClickListener {
            activity.showAbout()
            dismiss()
        }

        binding.statsItem.setOnClickListener {
            activity.showStats()
            dismiss()
        }

        // R: hidden files vault. The row has a neutral name and a green arrow so nobody guesses what it holds;
        // it asks for fingerprint / the phone's pattern or PIN before showing anything.
        with(binding.hideListsItem) {
            isVisible = preferences.rHideLists().get()
            text = VAULT_NAME
            setIcon(R.drawable.ic_expand_more_24dp)
            setIconColor(VAULT_GREEN)
            setOnClickListener {
                dismiss()
                if (!activity.isAuthenticationSupported()) {
                    activity.toast("فعّل بصمة أو قفل شاشة (نمط / رقم) في الموبايل الأول")
                    return@setOnClickListener
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

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                        }
                    },
                )
            }
        }

        binding.overflowCardView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = activity.toolbarHeight - 2.dpToPx
        }
        window?.let { window ->
            window.navigationBarColor = Color.TRANSPARENT
            window.decorView.fitsSystemWindows = true
            val wic = WindowInsetsControllerCompat(window, window.decorView)
            wic.isAppearanceLightStatusBars = false
            wic.isAppearanceLightNavigationBars = false
        }
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

    private companion object {
        private const val URL_HELP = "https://tachiyomi.org/docs/guides/troubleshooting/"
        private const val VAULT_NAME = "الملفات"
        private val VAULT_GREEN = Color.rgb(76, 175, 80)
    }
}
