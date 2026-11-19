package eu.kanade.tachiyomi.ui.setting.controllers

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.bindTo
import eu.kanade.tachiyomi.ui.setting.intListPreference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.switchPreference

/**
 * "R" settings (تعديلات R): one switch per modification made in this fork, grouped by where it shows up.
 * All texts here are Arabic on purpose (the owner wants no English in the app).
 *
 * Rules (see YOKAI_HANDOFF.md):
 * - every modification gets a switch here (key prefix "r_" in PreferencesHelper);
 * - switch ON = the modified behavior (default), switch OFF = the original Yokai behavior;
 * - anything new we add must also have its settings in this screen, and be listed in the handoff table.
 */
class SettingsRController : SettingsLegacyController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        title = "تعديلات R"

        preferenceCategory {
            title = "عام"

            switchPreference {
                bindTo(preferences.rForceArabic())
                title = "اللغة العربية دائماً"
                summary = "يفتح التطبيق بالعربية في كل مرة، إلا إذا اخترت لغة أخرى من الإعدادات العامة. " +
                    "عند الإيقاف: لغة النظام. يسري بعد إعادة تشغيل التطبيق."
            }

            switchPreference {
                bindTo(preferences.rSingleAppIcon())
                title = "أيقونة واحدة للتطبيق"
                summary = "يُخفي منتقي الأيقونات ويبقى شعار R فقط. عند الإيقاف: منتقي الأيقونات الأصلي."
            }
        }

        preferenceCategory {
            title = "القارئ"

            switchPreference {
                bindTo(preferences.rVerticalNav())
                title = "شريط الصفحات الرأسي"
                summary = "شريط طولي على اليمين فوق شريط الأدوات: التقدم يمتلئ من الأسفل للأعلى، " +
                    "وأسفله زر التشغيل التلقائي. عند الإيقاف: الشريط الأفقي الأصلي. يسري عند فتح القارئ من جديد."
            }

            switchPreference {
                bindTo(preferences.rChapterTimeCard())
                title = "بطاقة مدة الفصل"
                summary = "بين الفصول يظهر الثعبان ويقول كم قضيت من الوقت في الفصل الذي أنهيته. " +
                    "عند الإيقاف: بدون بطاقة."
            }

            switchPreference {
                bindTo(preferences.rAutoScroll())
                title = "زر التمرير التلقائي"
                summary = "زر تشغيل في شريط القارئ يحرّك الصفحات تلقائياً (الوضع المستمر فقط). عند الإيقاف: بدون زر."
            }

            intListPreference(activity) {
                bindTo(preferences.rAutoScrollSpeed())
                title = "سرعة التمرير التلقائي"
                entries = listOf("بطيئة جداً", "بطيئة", "عادية", "سريعة", "سريعة جداً")
                entryValues = listOf(30, 60, 100, 150, 220)
                dependency = preferences.rAutoScroll().key()
            }
        }

        preferenceCategory {
            title = "المكتبة"

            switchPreference {
                bindTo(preferences.rPinManga())
                title = "تثبيت المانهوا"
                summary = "اضغط مطوّلاً على مانهوا ثم من قائمة الثلاث نقاط اختر \"تثبيت / إلغاء التثبيت\". " +
                    "المثبّتة تظهر أولاً في فئتها وبجانب اسمها علامة 📌. عند الإيقاف: بدون تثبيت."
            }
        }

        preferenceCategory {
            title = "التنزيلات"

            switchPreference {
                bindTo(preferences.rFixSplitTallImageError())
                title = "إصلاح تقسيم الصور الطويلة"
                summary = "ملفات الصفحات تُسمّى دائماً بالأرقام اللاتينية (0-9)، ولا تفشل الصفحة إذا غاب الملف أثناء التقسيم. " +
                    "يصلح خطأ \"لم يُعثر على الصفحة أثناء التقسيم\" على الهواتف العربية. عند الإيقاف: السلوك الأصلي."
            }

            switchPreference {
                bindTo(preferences.rMihonFolders())
                title = "قراءة تنزيلات Mihon"
                summary = "يتعرّف على مجلدات Mihon حتى لو أضاف لاحقة (hash) إلى اسم الفصل، أو كتب الحروف غير " +
                    "اللاتينية بصيغة hex (مثل e28099). عند الإيقاف: أسماء المجلدات الأصلية فقط."
            }
        }

        preferenceCategory {
            title = "الإحصائيات"

            switchPreference {
                bindTo(preferences.rStatsMinutes())
                title = "إظهار الدقائق في مدة القراءة"
                summary = "تُعرض الدقائق حتى لو تجاوزت المدة يوماً. عند الإيقاف: أيام وساعات فقط (الأصلي)."
            }
        }

        preferenceCategory {
            title = "الثعبان"

            switchPreference {
                bindTo(preferences.rMascot())
                title = "شخصية الثعبان"
                summary = "ثعبان صغير يطفو على شاشة المكتبة، أمسكه وارمِه. عند الإيقاف: بدون ثعبان (الأصلي)."
            }

            switchPreference {
                bindTo(preferences.rMascotSage())
                title = "الحكيم العجوز"
                summary = "أحياناً عند لمس الثعبان يتحوّل إلى الحكيم بقبّعته ولحيته ويعطيك نصيحة صحيحة تماماً " +
                    "ولا تفيدك بشيء، للمزاح فقط. عند الإيقاف: الثعبان العادي دائماً."
                dependency = preferences.rMascot().key()
            }

            intListPreference(activity) {
                bindTo(preferences.rMascotSageChance())
                title = "كم مرة يظهر الحكيم"
                entries = listOf("نادراً", "أحياناً", "كثيراً", "في كل لمسة")
                entryValues = listOf(15, 35, 60, 100)
                dependency = preferences.rMascotSage().key()
            }
        }
    }
}
