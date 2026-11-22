package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import org.json.JSONObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** R: personal notes for each manga. Keyed by source id + manga url (stable across restores), saved in one preference. */
object RNotes {
    private fun key(source: Long, url: String) = "$source:$url"

    fun get(source: Long, url: String, prefs: PreferencesHelper = Injekt.get()): String =
        try {
            JSONObject(prefs.rMangaNotes().get()).optString(key(source, url), "")
        } catch (e: Exception) {
            ""
        }

    fun set(source: Long, url: String, text: String, prefs: PreferencesHelper = Injekt.get()) {
        val json = try {
            JSONObject(prefs.rMangaNotes().get())
        } catch (e: Exception) {
            JSONObject()
        }
        if (text.isBlank()) json.remove(key(source, url)) else json.put(key(source, url), text)
        prefs.rMangaNotes().set(json.toString())
    }
}
