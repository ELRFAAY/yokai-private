package eu.kanade.tachiyomi.util

import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * R: XP per finished chapter with a combo. A reading session = chapters finished with gaps under 30 minutes.
 * Doubles per chapter of the session: 10, 20, 40, 80... XP (owner, 24 Nov 15:08), x1024 at most. The base is
 * [RDragonLevel.XP_PER_CHAPTER]; the extra part is saved in `r_dragon_xp_bonus` so the history based total stays valid.
 */
object RXp {
    private const val SESSION_GAP_MS = 30 * 60 * 1000L

    /** What the last finished chapter gave (shown on the end card). */
    @Volatile var lastGain: Long = 0L
    @Volatile var lastMultiplier: Float = 1f
    @Volatile var lastCombo: Int = 0

    /** The reader sets this to animate the XP counter (gain, total xp after it); called on the main thread. */
    @Volatile var onGain: ((Long, Long) -> Unit)? = null

    /** A short retro "coin" sound (two square-wave notes), synthesized, no sound file needed. */
    fun playCoin() {
        try {
            val rate = 22050
            fun tone(freq: Double, ms: Int): ShortArray {
                val n = rate * ms / 1000
                return ShortArray(n) { i ->
                    val t = i / rate.toDouble()
                    val sq = if (Math.sin(2 * Math.PI * freq * t) >= 0) 1.0 else -1.0
                    val env = 1.0 - i.toDouble() / n
                    (sq * env * 5200).toInt().toShort()
                }
            }
            val pcm = tone(988.0, 90) + tone(1319.0, 320)
            val track = android.media.AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_GAME)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setNotificationMarkerPosition(pcm.size)
            track.setPlaybackPositionUpdateListener(object : android.media.AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: android.media.AudioTrack) { t.release() }
                override fun onPeriodicNotification(t: android.media.AudioTrack) {}
            })
            track.play()
        } catch (_: Exception) {
        }
    }

    /** Doubles with every chapter of the session: 1st x1 (10 XP), 2nd x2 (20), 3rd x4 (40), 4th x8 (80)... capped at x1024. */
    fun multiplier(count: Int): Float = (1L shl (count - 1).coerceIn(0, MAX_DOUBLINGS)).toFloat()

    /** x2 per chapter with no practical limit: 2^58 x 10 XP is the biggest value that still fits a Long. */
    private const val MAX_DOUBLINGS = 58

    /** XP of the [count]-th chapter of a session: 10, 20, 40, 80 ... (Long). */
    fun gainFor(count: Int): Long = RDragonLevel.XP_PER_CHAPTER.toLong() shl (count - 1).coerceIn(0, MAX_DOUBLINGS)

    /** Call once when a chapter becomes read (it was not read before). Returns the xp gained. */
    fun onChapterRead(context: Context, prefs: PreferencesHelper = Injekt.get()): Long {
        val sp = context.getSharedPreferences("r_xp", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = sp.getLong("last", 0L)
        var keep = now - last <= SESSION_GAP_MS
        // R shop: a combo shield keeps the combo alive once after a long break
        if (!keep && last > 0L && sp.getInt("count", 0) > 0 && prefs.rComboShields().get() > 0) {
            prefs.rComboShields().set(prefs.rComboShields().get() - 1)
            keep = true
        }
        val count = (if (keep) sp.getInt("count", 0) else 0) + 1
        val mult = multiplier(count)
        // R shop: x2 XP booster while it is active
        val boost = if (prefs.rBoostUntil().get() > now) 2L else 1L
        val gain = gainFor(count) * boost
        val extra = gain - RDragonLevel.XP_PER_CHAPTER
        sp.edit().putLong("last", now).putInt("count", count).apply()
        RDragonLevel.addXp(prefs, gain, extra)
        lastGain = gain
        lastMultiplier = mult
        lastCombo = count
        onGain?.let { cb ->
            val total = RDragonLevel.totalXp(prefs)
            android.os.Handler(android.os.Looper.getMainLooper()).post { cb(gain, total) }
        }
        return gain
    }
}
