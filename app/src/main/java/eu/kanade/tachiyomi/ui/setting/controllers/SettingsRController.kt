package eu.kanade.tachiyomi.ui.setting.controllers

import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.LayoutAnimationController
import android.view.animation.TranslateAnimation
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.changesIn
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.bindTo
import eu.kanade.tachiyomi.ui.setting.intListPreference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.switchPreference
import eu.kanade.tachiyomi.util.system.getResourceColor

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // the rows glide in one after another (slide from the side + fade), instead of appearing at once
        val enter = AnimationSet(true).apply {
            interpolator = DecelerateInterpolator()
            addAnimation(AlphaAnimation(0f, 1f))
            addAnimation(
                TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0.12f, Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
                ),
            )
            duration = 380L
        }
        listView.layoutAnimation = LayoutAnimationController(enter, 0.09f)
    }

    /** Every row gets a small icon in the accent color so the owner recognizes it without reading the text. */
    private fun Preference.rIcon(res: Int) {
        val ctx = this.context
        icon = AppCompatResources.getDrawable(ctx, res)?.mutate()?.apply {
            setTint(ctx.getResourceColor(R.attr.colorSecondary))
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        title = "تعديلات R"

        preferenceCategory {
            title = "عام"

            switchPreference {
                bindTo(preferences.rSettingsStyle())
                title = "شكل الإعدادات الجديد"
                rIcon(R.drawable.ic_tune_24dp)
                summary = "كل مجموعة إعدادات في كارت بلون سيم التطبيق. يسري لما تفتح الشاشة من جديد. عند الإيقاف: الشكل الأصلي."
            }

            switchPreference {
                bindTo(preferences.rForceArabic())
                title = "اللغة العربية دائماً"
                rIcon(R.drawable.ic_language_24dp)
                summary = "يفتح التطبيق بالعربية في كل مرة، إلا إذا اخترت لغة أخرى من الإعدادات العامة. " +
                    "عند الإيقاف: لغة النظام. يسري بعد إعادة تشغيل التطبيق."
            }

            switchPreference {
                bindTo(preferences.rSingleAppIcon())
                title = "أيقونة واحدة للتطبيق"
                rIcon(R.drawable.ic_appearance_outline_24dp)
                summary = "يُخفي منتقي الأيقونات ويبقى شعار R فقط. عند الإيقاف: منتقي الأيقونات الأصلي."
            }
        }

        preferenceCategory {
            title = "القارئ"

            switchPreference {
                bindTo(preferences.rVerticalNav())
                title = "شريط الصفحات الرأسي"
                rIcon(R.drawable.ic_swap_vert_24dp)
                summary = "شريط طولي على اليمين فوق شريط الأدوات: التقدم يمتلئ من الأسفل للأعلى، " +
                    "وأسفله زر التشغيل التلقائي. عند الإيقاف: الشريط الأفقي الأصلي. يسري عند فتح القارئ من جديد."
            }

            switchPreference {
                bindTo(preferences.rChapterTimeCard())
                title = "بطاقة مدة الفصل"
                rIcon(R.drawable.ic_progress_clock_24dp)
                summary = "بين الفصول يظهر الثعبان ويقول كم قضيت من الوقت في الفصل الذي أنهيته. " +
                    "عند الإيقاف: بدون بطاقة."
            }

            switchPreference {
                bindTo(preferences.rAutoScroll())
                title = "زر التمرير التلقائي"
                rIcon(R.drawable.ic_play_arrow_24dp)
                summary = "زر تشغيل في شريط القارئ يحرّك الصفحات تلقائياً (الوضع المستمر فقط). عند الإيقاف: بدون زر."
            }

            intListPreference(activity) {
                bindTo(preferences.rAutoScrollSpeed())
                title = "سرعة التمرير التلقائي"
                rIcon(R.drawable.ic_tune_24dp)
                entries = listOf("بطيئة جداً", "بطيئة", "عادية", "سريعة", "سريعة جداً")
                entryValues = listOf(30, 60, 100, 150, 220)
                preferences.rAutoScroll().changesIn(viewScope) { isEnabled = it }
            }

            switchPreference {
                bindTo(preferences.rTapScroll())
                title = "التمرير بلمس أسفل الشاشة"
                rIcon(R.drawable.ic_play_arrow_24dp)
                summary = "اضغط في النص السفلي من الشاشة (وضع الويبتون) عشان تنزّل أو تطلّع بقفزة. النص العلوي زي الأصلي. عند الإيقاف: اللمس الأصلي."
            }

            intListPreference(activity) {
                bindTo(preferences.rTapScrollSide())
                title = "جهتا التمرير"
                rIcon(R.drawable.ic_tune_24dp)
                entries = listOf("اليمين ينزّل واليسار يطلّع", "اليسار ينزّل واليمين يطلّع", "الاتنين ينزّلوا")
                entryValues = listOf(0, 1, 2)
                preferences.rTapScroll().changesIn(viewScope) { isEnabled = it }
            }

            intListPreference(activity) {
                bindTo(preferences.rTapJump())
                title = "مقدار القفز"
                rIcon(R.drawable.ic_tune_24dp)
                entries = listOf("٥٠٪ من الشاشة", "٧٠٪ من الشاشة", "٨٥٪ من الشاشة", "١٠٠٪ من الشاشة")
                entryValues = listOf(50, 70, 85, 100)
                preferences.rTapScroll().changesIn(viewScope) { isEnabled = it }
            }

            switchPreference {
                bindTo(preferences.rHoldScroll())
                title = "الضغط المطوّل للتمرير المستمر"
                rIcon(R.drawable.ic_play_arrow_24dp)
                summary = "سيب صباعك مضغوط في النص السفلي والصفحات تتحرك لحد ما تشيله. اليمين/اليسار زي إعداد جهتا التمرير."
                preferences.rTapScroll().changesIn(viewScope) { isEnabled = it }
            }

            intListPreference(activity) {
                bindTo(preferences.rHoldScrollSpeed())
                title = "سرعة الضغط المطوّل"
                rIcon(R.drawable.ic_tune_24dp)
                entries = listOf("بطيئة (٠٫٥ شاشة/ث)", "عادية (١ شاشة/ث)", "سريعة (١٫٥ شاشة/ث)", "سريعة جداً (٢٫٥ شاشة/ث)")
                entryValues = listOf(5, 10, 15, 25)
                preferences.rHoldScroll().changesIn(viewScope) { isEnabled = it }
            }

            intListPreference(activity) {
                bindTo(preferences.rAutoHideTools())
                title = "إخفاء الأدوات تلقائيًا"
                rIcon(R.drawable.ic_tune_24dp)
                entries = listOf("أبدًا", "بعد ٣ ثواني", "بعد ٥ ثواني", "بعد ٨ ثواني")
                entryValues = listOf(0, 3, 5, 8)
            }

            switchPreference {
                bindTo(preferences.rToolsBar())
                title = "شريط أدوات القارئ"
                rIcon(R.drawable.ic_tune_24dp)
                summary = "الشريط الجديد العائم: سرعة التمرير والسطوع والصوت والبطارية وأزرار الفصول. اسحبه لأعلى للفصول، ويمين أو شمال للإعدادات. يسري عند فتح القارئ من جديد."
            }

            switchPreference {
                bindTo(preferences.rSideTools())
                title = "الشريط الجانبي وسهم الأدوات القديمة"
                rIcon(R.drawable.ic_swap_vert_24dp)
                summary = "شريط الصفحات والشباتر على الجنب (من غير زرار التمرير التلقائي). السهم فوقه بيطلّع أدوات البرنامج القديمة جنبه، والشريط القديم اللي تحت بيختفي. عند الإيقاف: الشكل القديم. يسري عند فتح القارئ من جديد."
            }

            switchPreference {
                bindTo(preferences.rOldToolbar())
                title = "شريط الصفحات القديم"
                rIcon(R.drawable.ic_swap_vert_24dp)
                summary = "شريط التطبيق الأصلي أسفل القارئ (شريط الصفحات). عند الإيقاف: يختفي تمامًا ويبقى الشريط الجديد فقط. يمكن تشغيل الاثنين معًا أو أيهما. يسري عند فتح القارئ من جديد."
            }

            switchPreference {
                bindTo(preferences.rClockBattery())
                title = "الساعة والبطارية"
                rIcon(R.drawable.ic_progress_clock_24dp)
                summary = "ساعة ونسبة البطارية صغيرة في ركن القارئ. عند الإيقاف: بدونها."
            }

            switchPreference {
                bindTo(preferences.rProgressBar())
                title = "شريط تقدم دائم"
                rIcon(R.drawable.ic_swap_vert_24dp)
                summary = "خط رفيع أسفل الشاشة يبيّن مكانك في الفصل طول الوقت. عند الإيقاف: بدونه."
            }
        }

        preferenceCategory {
            title = "المكتبة"

            switchPreference {
                bindTo(preferences.rPinManga())
                title = "تثبيت المانهوا"
                rIcon(R.drawable.ic_pin_24dp)
                summary = "اضغط مطوّلاً على مانهوا ثم من قائمة الثلاث نقاط اختر \"تثبيت / إلغاء التثبيت\". " +
                    "المثبّتة تظهر أولاً في فئتها وبجانب اسمها علامة 📌. عند الإيقاف: بدون تثبيت."
            }

            switchPreference {
                bindTo(preferences.rHideLists())
                title = "إخفاء القوائم"
                rIcon(R.drawable.ic_eye_off_24dp)
                summary = "من أيقونة الفلتر (الثلاث شرط أعلى المكتبة) انزل لآخر الشيت وافتح \"الملفات\" (بالبصمة أو النمط) واختار التصنيفات اللي عايز تخفيها. " +
                    "المانجا المخفية تختفي من المكتبة والتحديثات والسجل، وتفضل بتتحدّث مع ريفريش المكتبة، وتقراها من \"الملفات\". " +
                    "لما تخرجها ترجع بتواريخها. عند الإيقاف: كل حاجة تظهر والخيار يختفي (اختياراتك تتحفظ)."
            }
        }

        preferenceCategory {
            title = "التنزيلات"

            switchPreference {
                bindTo(preferences.rFixSplitTallImageError())
                title = "إصلاح تقسيم الصور الطويلة"
                rIcon(R.drawable.ic_file_download_24dp)
                summary = "ملفات الصفحات تُسمّى دائماً بالأرقام اللاتينية (0-9)، ولا تفشل الصفحة إذا غاب الملف أثناء التقسيم. " +
                    "يصلح خطأ \"لم يُعثر على الصفحة أثناء التقسيم\" على الهواتف العربية. عند الإيقاف: السلوك الأصلي."
            }

            switchPreference {
                bindTo(preferences.rMihonFolders())
                title = "قراءة تنزيلات Mihon"
                rIcon(R.drawable.ic_file_open_24dp)
                summary = "يتعرّف على مجلدات Mihon حتى لو أضاف لاحقة (hash) إلى اسم الفصل، أو كتب الحروف غير " +
                    "اللاتينية بصيغة hex (مثل e28099). عند الإيقاف: أسماء المجلدات الأصلية فقط."
            }
        }

        preferenceCategory {
            title = "الإحصائيات"

            switchPreference {
                bindTo(preferences.rStatsMinutes())
                title = "إظهار الدقائق في مدة القراءة"
                rIcon(R.drawable.ic_query_stats_24dp)
                summary = "تُعرض الدقائق حتى لو تجاوزت المدة يوماً. عند الإيقاف: أيام وساعات فقط (الأصلي)."
            }

            switchPreference {
                bindTo(preferences.rStatsExtra())
                title = "أقسام الإحصائيات الإضافية"
                rIcon(R.drawable.ic_query_stats_24dp)
                summary = "سلسلة الأيام، هدف الأسبوع، أكتر ٥ مانهوا، ساعات القراءة، مقارنة الشهر، ودونات الأنواع والمصادر. عند الإيقاف: الشاشة بدونها."
            }

            switchPreference {
                bindTo(preferences.rStatsProfile())
                title = "بروفايل الإحصائيات"
                rIcon(R.drawable.ic_r_mood)
                summary = "صورة بروفايل دائرية صغيرة وبانر كبير بجانبها (أكتر من صورة تتقلب كل ٥ ثوانٍ، مع نقاط وسحب) واسمك، أعلى الإحصائيات. عند الإيقاف: بدونه."
            }
        }

        preferenceCategory {
            title = "الثعبان"

            switchPreference {
                bindTo(preferences.rMascot())
                title = "شخصية الثعبان"
                rIcon(R.drawable.ic_r_mood)
                summary = "ثعبان صغير يطفو على شاشة المكتبة، أمسكه وارمِه. عند الإيقاف: بدون ثعبان (الأصلي)."
            }

            switchPreference {
                bindTo(preferences.rMascotTransform())
                title = "تحويل التنين الصغير"
                rIcon(R.drawable.ic_r_swap)
                summary = "ضغطتان سريعتان عليه تحوّلانه بالدخان لزيّ عشوائي من ٢١ زيّاً (فارس، قرصان، طيّار، طبّاخ...). " +
                    "الزيّ الذي ظهر لا يعود حتى تظهر كل الأزياء، ثم تبدأ الدورة من جديد. الضغطة الواحدة تدغدغه أو يطلع الحكيم بنصيحة. عند الإيقاف: لا تحويل."
                preferences.rMascot().changesIn(viewScope) { isEnabled = it }
            }

            intListPreference(activity) {
                bindTo(preferences.rDragonSize())
                title = "حجم التنين الصغير"
                rIcon(R.drawable.ic_tune_24dp)
                // one value for the home screen AND the reader (also the stick scene): always exactly the same size
                entries = listOf("صغير جداً (٤٨)", "صغير (٦٠)", "متوسط (٧٢)", "كبير (٨٨)", "كبير جداً (١١٠)", "ضخم (١٤٠)")
                entryValues = listOf(48, 60, 72, 88, 110, 140)
            }

            switchPreference {
                bindTo(preferences.rMascotSound())
                title = "صوت التنين الصغير"
                rIcon(R.drawable.ic_r_volume)
                summary = "الكلام يظهر حرفاً حرفاً مع نقرات صغيرة، ويصمت إذا كان الهاتف صامتاً أو على الاهتزاز. عند الإيقاف: كلام بدون صوت."
                preferences.rMascot().changesIn(viewScope) { isEnabled = it }
            }

            switchPreference {
                bindTo(preferences.rMascotSage())
                title = "الحكيم العجوز"
                rIcon(R.drawable.ic_r_school)
                summary = "أحياناً عند لمس الثعبان يتحوّل إلى الحكيم بقبّعته ولحيته ويعطيك نصيحة صحيحة تماماً " +
                    "ولا تفيدك بشيء، للمزاح فقط. عند الإيقاف: الثعبان العادي دائماً."
                preferences.rMascot().changesIn(viewScope) { isEnabled = it }
            }

            intListPreference(activity) {
                bindTo(preferences.rMascotSageChance())
                title = "كم مرة يظهر الحكيم"
                rIcon(R.drawable.ic_tune_24dp)
                entries = listOf("نادراً", "أحياناً", "كثيراً", "في كل لمسة")
                entryValues = listOf(15, 35, 60, 100)
                preferences.rMascotSage().changesIn(viewScope) { isEnabled = it }
            }

            switchPreference {
                bindTo(preferences.rMascotFall())
                title = "التنين الصغير: السقطة"
                rIcon(R.drawable.ic_r_hotel)
                summary = "من وقت لآخر ينزل من فوق ويقع على الأرض والنجوم فوق دماغه، يطلع تاني، " +
                    "وينزل مرة تانية ويفضل واقع شوية. أي لمسة توقفه. عند الإيقاف: لا يقع."
                preferences.rMascot().changesIn(viewScope) { isEnabled = it }
            }

            switchPreference {
                bindTo(preferences.rHideNavButtons())
                title = "القارئ: إخفاء أزرار الموبايل"
                rIcon(R.drawable.ic_r_hotel)
                summary = "لما تدوس في النص تظهر أدوات التطبيق بس، وأزرار الموبايل التلاتة (رجوع / الرئيسية / التطبيقات) " +
                    "ما تظهرش إلا بسوايب لفوق من حافة الشاشة. عند الإيقاف: تظهر زي الأول."
            }

            switchPreference {
                bindTo(preferences.rDragonFloat())
                title = "التنين الصغير: الطيران والإشارة"
                rIcon(R.drawable.ic_r_hotel)
                summary = "في بطاقة آخر الفصل وفي الإحصائيات يطير في مكانه (طلوع ونزول وظل ملمّع ونقطتين منورين حواليه)، " +
                    "ويشاور على الكلام اللي بيقوله. تمسكه وترميه فيطير على الشاشة ويلف ويرجع مكانه. " +
                    "عند الإيقاف: صورة ثابتة زي الأول."
            }

            switchPreference {
                bindTo(preferences.rReminder())
                title = "تذكير التنين بالقراءة"
                rIcon(R.drawable.ic_r_hotel)
                summary = "لو مرّت ٣ أيام من غير قراءة يبعتلك التنين إشعاراً هادئاً (مرة في اليوم، بين ١٠ صباحاً و١٠ مساءً). عند الإيقاف: لا إشعارات."
            }

            switchPreference {
                bindTo(preferences.rMascotSleep())
                title = "التنين الصغير: النوم"
                rIcon(R.drawable.ic_r_hotel)
                summary = "بعد دقيقة بدون لمس الشاشة يقول تصبح على خير، ويسحب لحافاً فوقه وينام. " +
                    "أي لمسة توقظه. عند الإيقاف: لا ينام."
                preferences.rMascot().changesIn(viewScope) { isEnabled = it }
            }

            switchPreference {
                bindTo(preferences.rMascotWelcome())
                title = "التنين الصغير: الترحيب عند فتح التطبيق"
                rIcon(R.drawable.ic_r_home)
                summary = "عند فتح التطبيق ينزل التنين الصغير من أعلى اليمين ويقول لك كم من الوقت غبت عنه. " +
                    "عند الإيقاف: لا يظهر ترحيب."
                preferences.rMascot().changesIn(viewScope) { isEnabled = it }
            }

            switchPreference {
                bindTo(preferences.rWelcomeE2())
                title = "نزول التنين الصغير بالحركة الجديدة"
                rIcon(R.drawable.ic_r_home)
                summary = "في الترحيب ينزل بحركات الشيت الجديدة: يقع، يتفلطح، يتنطط ثم يقعد ويلوّح. عند الإيقاف: التلويح القديم بس."
                preferences.rMascotWelcome().changesIn(viewScope) { isEnabled = it }
            }
        }

        preferenceCategory {
            title = "التنين في القارئ"

            switchPreference {
                bindTo(preferences.rReaderCobra())
                title = "القارئ: التنين الصغير أثناء القراءة"
                rIcon(R.drawable.ic_book_open_variant_24dp)
                summary = "مفعّل: التنين الصغير يطلع من جنب الشاشة وإنت بتقرا وتقولك كلمتين بالمصري. " +
                    "معطّل: القارئ الأصلي (بدون كوبرا). لا تظهر والقائمة مفتوحة، وتضغط عليها علشان تختفي."
            }

            switchPreference {
                bindTo(preferences.rReaderCobraSound())
                title = "التنين الصغير: الأصوات"
                rIcon(R.drawable.ic_r_volume)
                summary = "مفعّل: صوت كتابة الكلام وصوت حركتها وهي داخلة وخارجة (بصوت الوسائط). معطّل: بدون أصوات."
            }

            switchPreference {
                bindTo(preferences.rReaderCobraNight())
                title = "التنين الصغير: وضع الليل"
                rIcon(R.drawable.ic_r_bedtime)
                summary = "مفعّل: من 12 بالليل لحد 5 الصبح تيجي نعسانة وتقولك «خش نام يا عم». معطّل: نفس الكلام العادي طول اليوم."
            }

            intListPreference(activity) {
                bindTo(preferences.rReaderCobraChapters())
                title = "التنين الصغير: يظهر كل كام شابتر"
                rIcon(R.drawable.ic_progress_clock_24dp)
                entries = listOf("كل شابتر", "كل شابترين", "كل 3 شباتر", "كل 5 شباتر", "كل 8 شباتر")
                entryValues = listOf(1, 2, 3, 5, 8)
                summary = "بيظهر بعد ما تفتح الشابتر بنص دقيقة تقريبًا، مش بالدقايق."
            }

            intListPreference(activity) {
                bindTo(preferences.rReaderCobraCooldown())
                title = "بعد المشهد: يختفي قد إيه"
                rIcon(R.drawable.ic_progress_clock_24dp)
                entries = listOf("5 دقايق", "10 دقايق", "15 دقيقة", "30 دقيقة")
                entryValues = listOf(5, 10, 15, 30)
                summary = "بعد ما الحكيم يضرب التنين الصغير، ما حدّش فيهم يظهر غير بعد المدة دي."
                preferences.rReaderCobraScene().changesIn(viewScope) { isEnabled = it }
            }

            switchPreference {
                bindTo(preferences.rReaderCobraScene())
                title = "مشهد العصا: التنين الحكيم"
                rIcon(R.drawable.ic_r_stick)
                summary = "مفعّل: التنين الصغير يطلع مرتين أو تلاتة ورا بعض، وبعدين يجي التنين الحكيم من اليمين ويضربه بالعصا على راسه ويسحبه معاه. معطّل: التنين الصغير بس."
            }

            intListPreference(activity) {
                bindTo(preferences.rReaderCobraSceneEvery())
                title = "مشهد العصا: كل كام زيارة"
                rIcon(R.drawable.ic_progress_clock_24dp)
                entries = listOf("كل زيارة تانية", "كل زيارة تالتة", "كل زيارة رابعة", "كل زيارة خامسة", "كل زيارة سابعة")
                entryValues = listOf(2, 3, 4, 5, 7)
                preferences.rReaderCobraScene().changesIn(viewScope) { isEnabled = it }
            }

            switchPreference {
                bindTo(preferences.rSceneV2())
                title = "مشهد العصا الجديد (الشيتس الجديدة)"
                rIcon(R.drawable.ic_r_stick)
                summary = "مفعّل: الحكيم يمشي من اليمين، يرفع العصا ويضرب، والتنين الصغير يتخبط ويتهز ثم يسحبه. معطّل: المشهد القديم. يسري من الزيارة الجاية."
                preferences.rReaderCobraScene().changesIn(viewScope) { isEnabled = it }
            }
        }
    }
}
