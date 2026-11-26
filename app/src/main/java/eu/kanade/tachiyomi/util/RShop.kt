package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import java.time.LocalDate

/**
 * R: the coin shop. Coins = 1 per 1000 XP ([RDragonLevel.goldBalance]); spending adds to `rGoldSpent`, XP and level never drop.
 * Kinds: consumables (booster, combo shield, chest re-roll), a stackable pack (+5 favourite slots, max 3), and cosmetics
 * (profile frame, title, reader counter theme) that are bought once and then equipped / unequipped.
 */
object RShop {
    enum class Group(val label: String) {
        BOOST("⚡ مساعدات"),
        FRAME("🖼️ إطارات الملف الشخصي"),
        COBRA("🐍 اكسسوارات الكوبرا"),
        TITLE("🏷️ ألقاب"),
        COUNTER("🪙 ثيمات عداد القراءة"),
    }

    data class Item(val id: String, val group: Group, val name: String, val desc: String, val price: Long, val color: Int = 0, val pic: String = "")

    val items = listOf(
        Item("boost", Group.BOOST, "مضاعف XP ساعة", "كل XP تاخده في الساعة الجاية يتضاعف ×2.", 3),
        Item("shield", Group.BOOST, "حماية الكومبو", "لو سبت القراية أكتر من 30 دقيقة، الكومبو مش بيتصفّر مرة واحدة.", 5),
        Item("reroll", Group.BOOST, "فتح الصندوق تاني", "لو فتحت صندوق النهارده، تقدر تفتحه مرة كمان (نفس اليوم).", 4),
        Item("fav", Group.BOOST, "+5 خانات مفضلة", "تزود خانات المانجا المفضلة 5 (لحد 3 مرات).", 10),
        Item("frame_gold", Group.FRAME, "إطار ذهبي", "إطار ذهبي حوالين بطاقة المستوى.", 8, 0xFFFFC53D.toInt()),
        Item("frame_violet", Group.FRAME, "إطار بنفسجي", "إطار بنفسجي حوالين بطاقة المستوى.", 8, 0xFFB388FF.toInt()),
        Item("frame_ruby", Group.FRAME, "إطار ياقوتي", "إطار أحمر حوالين بطاقة المستوى.", 8, 0xFFFF5252.toInt()),
        Item("cobra_viking", Group.COBRA, "خوذة فايكنج", "الكوبرا بتلبس خوذة فايكنج.", 10, pic = "r_cobra_acc_viking"),
        Item("cobra_detective", Group.COBRA, "قبعة المحقق", "الكوبرا بتلبس قبعة شيرلوك.", 10, pic = "r_cobra_acc_detective"),
        Item("cobra_monocle", Group.COBRA, "مونوكل", "نظارة أحادية بسلسلة.", 10, pic = "r_cobra_acc_monocle"),
        Item("cobra_fedora", Group.COBRA, "قبعة فيدورا", "قبعة سودا أنيقة.", 10, pic = "r_cobra_acc_fedora"),
        Item("cobra_cape", Group.COBRA, "كاب أحمر", "كاب البطل الأحمر.", 10, pic = "r_cobra_acc_cape"),
        Item("cobra_propeller", Group.COBRA, "طاقية المروحة", "طاقية ملونة بمروحة.", 10, pic = "r_cobra_acc_propeller"),
        Item("cobra_pearls", Group.COBRA, "عقد لؤلؤ", "عقد لؤلؤ حوالين الرقبة.", 10, pic = "r_cobra_acc_pearls"),
        Item("cobra_pirate", Group.COBRA, "قبعة قرصان", "قبعة قرصان بجمجمة.", 12, pic = "r_cobra_acc_pirate"),
        Item("cobra_sunglasses", Group.COBRA, "نضارة شمس", "نضارة شمس سودا.", 10, pic = "r_cobra_acc_sunglasses"),
        Item("cobra_jester", Group.COBRA, "قبعة المهرج", "قبعة مهرج ملونة بجراس.", 12, pic = "r_cobra_acc_jester"),
        Item("cobra_vr", Group.COBRA, "نضارة VR", "نضارة واقع افتراضي.", 15, pic = "r_cobra_acc_vr"),
        Item("cobra_bowtie", Group.COBRA, "ببيونة صفرا", "ببيونة صفرا على الرقبة.", 8, pic = "r_cobra_acc_bowtie"),
        Item("title_night", Group.TITLE, "قارئ الليل", "لقب يظهر تحت المستوى.", 6),
        Item("title_hunter", Group.TITLE, "صائد الشباتر", "لقب يظهر تحت المستوى.", 6),
        Item("title_legend", Group.TITLE, "تنين الأسطورة", "لقب يظهر تحت المستوى.", 6),
        Item("ctr_gold", Group.COUNTER, "عداد ذهبي", "إطار ذهبي لعداد الـ XP وقت القراءة.", 5, 0xFFFFC53D.toInt()),
        Item("ctr_violet", Group.COUNTER, "عداد بنفسجي", "إطار بنفسجي لعداد الـ XP وقت القراءة.", 5, 0xFFB388FF.toInt()),
        Item("ctr_ruby", Group.COUNTER, "عداد ياقوتي", "إطار أحمر لعداد الـ XP وقت القراءة.", 5, 0xFFFF5252.toInt()),
    )

    const val MAX_FAV_PACKS = 3

    fun item(id: String) = items.firstOrNull { it.id == id }

    fun balance(prefs: PreferencesHelper) = RDragonLevel.goldBalance(prefs)

    fun owned(prefs: PreferencesHelper, id: String) = id in prefs.rShopOwned().get()

    private fun equipPref(prefs: PreferencesHelper, g: Group) = when (g) {
        Group.FRAME -> prefs.rShopFrame()
        Group.TITLE -> prefs.rShopTitle()
        Group.COBRA -> prefs.rShopCobra()
        else -> prefs.rShopCounter()
    }

    fun equipped(prefs: PreferencesHelper, id: String): Boolean {
        val it = item(id) ?: return false
        return it.group != Group.BOOST && equipPref(prefs, it.group).get() == id
    }

    /** Equipped item's colour for a group (0 = none). */
    fun equippedColor(prefs: PreferencesHelper, g: Group): Int = item(equipPref(prefs, g).get())?.color ?: 0

    /** Drawable name of the equipped cobra outfit picture ("" = none). */
    fun equippedCobraPic(prefs: PreferencesHelper): String = item(prefs.rShopCobra().get())?.pic ?: ""

    fun equippedTitle(prefs: PreferencesHelper): String = item(prefs.rShopTitle().get())?.name ?: ""

    fun boostLeftMs(prefs: PreferencesHelper) = (prefs.rBoostUntil().get() - System.currentTimeMillis()).coerceAtLeast(0L)

    /** Buys [it]. Returns a message for the toast (null = nothing to say). */
    fun buy(prefs: PreferencesHelper, it: Item): String {
        if (it.group != Group.BOOST && owned(prefs, it.id)) return "عندك ده بالفعل"
        if (it.id == "fav" && prefs.rExtraFav().get() >= MAX_FAV_PACKS) return "وصلت للحد الأقصى"
        if (it.id == "reroll" && RChest.available(prefs)) return "صندوق النهارده لسه مقفول، افتحه الأول"
        if (balance(prefs) < it.price) return "عملاتك مش كفاية (محتاج ${it.price})"
        prefs.rGoldSpent().set(prefs.rGoldSpent().get() + it.price)
        when (it.id) {
            "boost" -> prefs.rBoostUntil().set(maxOf(System.currentTimeMillis(), prefs.rBoostUntil().get()) + 60 * 60 * 1000L)
            "shield" -> prefs.rComboShields().set(prefs.rComboShields().get() + 1)
            "reroll" -> {
                val day = RChest.cycleDay(prefs)
                prefs.rChestDay().set(LocalDate.now().minusDays(1).toString())
                prefs.rChestStreak().set((day - 1).coerceAtLeast(0))
            }
            "fav" -> prefs.rExtraFav().set(prefs.rExtraFav().get() + 1)
            else -> {
                prefs.rShopOwned().set(prefs.rShopOwned().get() + it.id)
                equipPref(prefs, it.group).set(it.id)
            }
        }
        return "تم الشراء ✅"
    }

    /** Equip / unequip an owned cosmetic. */
    fun toggleEquip(prefs: PreferencesHelper, it: Item) {
        if (it.group == Group.BOOST || !owned(prefs, it.id)) return
        val p = equipPref(prefs, it.group)
        p.set(if (p.get() == it.id) "" else it.id)
    }
}
