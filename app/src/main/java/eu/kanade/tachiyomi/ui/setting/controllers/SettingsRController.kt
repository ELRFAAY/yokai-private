package eu.kanade.tachiyomi.ui.setting.controllers

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.data.preference.changesIn
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
                preferences.rAutoScroll().changesIn(viewScope) { isEnabled = it }
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
                bindTo(preferences.rMascotTransform())
                title = "تحويل التنين الصغير"
                summary = "الضغط عليه يحوّله بالدخان لشخصية عشوائية (فارس، نينجا، قرصان، محقق) وبعدها يرجع. عند الإيقاف: الضغط يعمل الحركة العادية."
                preferences.rMascot().changesIn(viewScope) { isEnabled = it }
            }

            switchPreference {
                bindTo(preferences.rMascotSound())
                title = "صوت التنين الصغير"
                summary = "الكلام يظهر حرفاً حرفاً مع نقرات صغيرة، ويصمت إذا كان الهاتف صامتاً أو على الاهتزاز. عند الإيقاف: كلام بدون صوت."
                preferences.rMascot().changesIn(viewScope) { isEnabled = it }
            }

            switchPreference {
                bindTo(preferences.rMascotSage())
                title = "الحكيم العجوز"
                summary = "أحياناً عند لمس الثعبان يتحوّل إلى الحكيم بقبّعته ولحيته ويعطيك نصيحة صحيحة تماماً " +
                    "ولا تفيدك بشيء، للمزاح فقط. عند الإيقاف: الثعبان العادي دائماً."
                preferences.rMascot().changesIn(viewScope) { isEnabled = it }
            }

            intListPreference(activity) {
                bindTo(preferences.rMascotSageChance())
                title = "كم مرة يظهر الحكيم"
                entries = listOf("نادراً", "أحياناً", "كثيراً", "في كل لمسة")
                entryValues = listOf(15, 35, 60, 100)
                preferences.rMascotSage().changesIn(viewScope) { isEnabled = it }
            }
        }

        switchPreference {
            bindTo(preferences.rMascotWelcome())
            title = "التنين الصغير: الترحيب عند فتح التطبيق"
            summary = "عند فتح التطبيق ينزل التنين الصغير من أعلى اليمين ويقول لك كم من الوقت غبت عنه. " +
                "عند الإيقاف: لا يظهر ترحيب."
            preferences.rMascot().changesIn(viewScope) { isEnabled = it }
        }

        switchPreference {
            bindTo(preferences.rReaderCobra())
            title = "القارئ: التنين الصغير أثناء القراءة"
            summary = "مفعّل: التنين الصغير يطلع من جنب الشاشة وإنت بتقرا وتقولك كلمتين بالمصري. " +
                "معطّل: القارئ الأصلي (بدون كوبرا). لا تظهر والقائمة مفتوحة، وتضغط عليها علشان تختفي."
        }

        switchPreference {
            bindTo(preferences.rReaderCobraSound())
            title = "التنين الصغير: الأصوات"
            summary = "مفعّل: صوت كتابة الكلام وصوت حركتها وهي داخلة وخارجة (بصوت الوسائط). معطّل: بدون أصوات."
        }

        switchPreference {
            bindTo(preferences.rReaderCobraNight())
            title = "التنين الصغير: وضع الليل"
            summary = "مفعّل: من 12 بالليل لحد 5 الصبح تيجي نعسانة وتقولك «خش نام يا عم». معطّل: نفس الكلام العادي طول اليوم."
        }

        intListPreference(activity) {
            bindTo(preferences.rReaderCobraInterval())
            title = "التنين الصغير: كل قد إيه يظهر"
            entries = listOf("كل 3 دقايق", "كل 6 دقايق", "كل 10 دقايق", "كل 20 دقيقة", "كل 45 دقيقة")
            entryValues = listOf(3, 6, 10, 20, 45)
        }

        switchPreference {
            bindTo(preferences.rReaderCobraScene())
            title = "مشهد العصا: التنين الحكيم"
            summary = "مفعّل: التنين الصغير يطلع مرتين أو تلاتة ورا بعض، وبعدين يجي التنين الحكيم من اليمين ويضربه بالعصا على راسه ويسحبه معاه. معطّل: التنين الصغير بس."
        }

        intListPreference(activity) {
            bindTo(preferences.rReaderCobraSceneEvery())
            title = "مشهد العصا: كل كام زيارة"
            entries = listOf("كل زيارة تالتة", "كل زيارة رابعة", "كل زيارة خامسة", "كل زيارة سابعة")
            entryValues = listOf(3, 4, 5, 7)
            preferences.rReaderCobraScene().changesIn(viewScope) { isEnabled = it }
        }
    }
}
