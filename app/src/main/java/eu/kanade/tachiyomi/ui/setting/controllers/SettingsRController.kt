package eu.kanade.tachiyomi.ui.setting.controllers

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.bindTo
import eu.kanade.tachiyomi.ui.setting.intListPreference
import eu.kanade.tachiyomi.ui.setting.switchPreference

/**
 * "R" settings (تعديلات R): one switch per modification made in this fork.
 * All texts here are Arabic on purpose (the owner wants no English in the app).
 *
 * Rules (see YOKAI_HANDOFF.md):
 * - every modification gets a switch here (key prefix "r_" in PreferencesHelper);
 * - switch ON = the modified behavior (default), switch OFF = the original Yokai behavior;
 * - anything new we add must also have its settings in this screen.
 */
class SettingsRController : SettingsLegacyController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        title = "تعديلات R"

        switchPreference {
            bindTo(preferences.rForceArabic())
            title = "اللغة: العربية دائماً"
            summary = "مفعّل: يفتح التطبيق بالعربية في كل مرة ما لم تختر لغة أخرى من الإعدادات العامة. " +
                "معطّل: لغة النظام (السلوك الأصلي). يُطبَّق بعد إعادة تشغيل التطبيق."
        }

        switchPreference {
            bindTo(preferences.rFixSplitTallImageError())
            title = "التنزيل: إصلاح أسماء الصفحات وتقسيم الصور الطويلة"
            summary = "مفعّل: تُسمّى ملفات الصفحات دائماً بالأرقام اللاتينية (0-9) لإصلاح خطأ \"لم يُعثر على الصفحة أثناء التقسيم\" " +
                "على الهواتف العربية، ويُكتفى بتسجيل غياب الملف أثناء التقسيم دون فشل الصفحة. " +
                "معطّل: السلوك الأصلي (أرقام لغة الجهاز وقد تفشل الصفحة بذلك الخطأ)."
        }

        switchPreference {
            bindTo(preferences.rAutoScroll())
            title = "القارئ: زر التمرير التلقائي (الوضع المستمر)"
            summary = "مفعّل: زر تشغيل في قائمة القارئ يحرّك الصفحات تلقائياً. معطّل: القارئ الأصلي (بدون زر)."
        }

        switchPreference {
            bindTo(preferences.rSingleAppIcon())
            title = "المظهر: أيقونة واحدة للتطبيق"
            summary = "مفعّل: يُخفى منتقي الأيقونات ويبقى شعار R فقط (أعد فتح المظهر لترى ذلك). " +
                "معطّل: منتقي الأيقونات الأصلي."
        }

        switchPreference {
            bindTo(preferences.rMascot())
            title = "الرئيسية: شخصية الثعبان"
            summary = "مفعّل: ثعبان صغير يطفو على شاشة المكتبة، أمسكه وارمِه. معطّل: بدون الثعبان (الأصلي)."
        }

        intListPreference(activity) {
            bindTo(preferences.rAutoScrollSpeed())
            title = "القارئ: سرعة التمرير التلقائي"
            entries = listOf("بطيئة جداً", "بطيئة", "عادية", "سريعة", "سريعة جداً")
            entryValues = listOf(30, 60, 100, 150, 220)
        }
    }
}
