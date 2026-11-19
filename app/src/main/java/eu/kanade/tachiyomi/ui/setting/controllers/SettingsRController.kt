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

        switchPreference {
            bindTo(preferences.rMascotSleep())
            title = "الرئيسية: الثعبان ينام"
            summary = "مفعّل: لو محدش لمس الشاشة دقيقة، الثعبان بيجيب بطانية وينام، ولمسة واحدة بتصحّيه. معطّل: بيفضل صاحي."
        }

        switchPreference {
            bindTo(preferences.rMascotFloat())
            title = "الرئيسية: الثعبان يطفو ويتنفس"
            summary = "مفعّل: الثعبان طاير في الهوا يطلع وينزل ببطء ويتنفس، وله ضل تحته. معطّل: حركة صغيرة بس (الأصلي)."
        }

        intListPreference(activity) {
            bindTo(preferences.rAutoScrollSpeed())
            title = "القارئ: سرعة التمرير التلقائي"
            entries = listOf("بطيئة جداً", "بطيئة", "عادية", "سريعة", "سريعة جداً")
            entryValues = listOf(30, 60, 100, 150, 220)
        }

        switchPreference {
            bindTo(preferences.rReaderCobra())
            title = "القارئ: الكوبرا الخضراء أثناء القراءة"
            summary = "مفعّل: الكوبرا تطلع من جنب الشاشة وإنت بتقرا وتقولك كلمتين بالمصري. " +
                "معطّل: القارئ الأصلي (بدون كوبرا). لا تظهر والقائمة مفتوحة، وتضغط عليها علشان تختفي."
        }

        switchPreference {
            bindTo(preferences.rReaderCobraSound())
            title = "الكوبرا: الأصوات"
            summary = "مفعّل: صوت كتابة الكلام وصوت حركتها وهي داخلة وخارجة (بصوت الوسائط). معطّل: بدون أصوات."
        }

        switchPreference {
            bindTo(preferences.rReaderCobraNight())
            title = "الكوبرا: وضع الليل"
            summary = "مفعّل: من 12 بالليل لحد 5 الصبح تيجي نعسانة وتقولك «خش نام يا عم». معطّل: نفس الكلام العادي طول اليوم."
        }

        intListPreference(activity) {
            bindTo(preferences.rReaderCobraInterval())
            title = "الكوبرا: كل قد إيه تظهر"
            entries = listOf("كل 3 دقايق", "كل 6 دقايق", "كل 10 دقايق", "كل 20 دقيقة", "كل 45 دقيقة")
            entryValues = listOf(3, 6, 10, 20, 45)
        }
    }
}
