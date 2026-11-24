package eu.kanade.tachiyomi.util

import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * R: XP per finished chapter with a combo. A reading session = chapters finished with gaps under 30 minutes.
 * Chapters 1-4 of a session x1, 5-9 x1.5, 10-19 x2, 20+ x3 (approved by the owner). The base is
 * [RDragonLevel.XP_PER_CHAPTER]; the extra part is saved in `r_dragon_xp_bonus` so the history based total stays valid.
 */
object RXp {
    private const val SESSION_GAP_MS = 30 * 60 * 1000L

    /** What the last finished chapter gave (shown on the end card). */
    @Volatile var lastGain: Int = 0
    @Volatile var lastMultiplier: Float = 1f
    @Volatile var lastCombo: Int = 0

    /** The reader sets this to animate the XP counter (gain, total xp after it); called on the main thread. */
    @Volatile var onGain: ((Int, Long) -> Unit)? = null

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

    fun multiplier(count: Int): Float = when {
        count >= 20 -> 3f
        count >= 10 -> 2f
        count >= 5 -> 1.5f
        else -> 1f
    }

    /** Call once when a chapter becomes read (it was not read before). Returns the xp gained. */
    fun onChapterRead(context: Context, prefs: PreferencesHelper = Injekt.get()): Int {
        val sp = context.getSharedPreferences("r_xp", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = sp.getLong("last", 0L)
        val count = (if (now - last <= SESSION_GAP_MS) sp.getInt("count", 0) else 0) + 1
        val mult = multiplier(count)
        val gain = (RDragonLevel.XP_PER_CHAPTER * mult).toInt()
        val extra = gain - RDragonLevel.XP_PER_CHAPTER
        sp.edit().putLong("last", now).putInt("count", count).apply()
        prefs.rDragonXpBonus().set(prefs.rDragonXpBonus().get() + extra)
        prefs.rDragonXp().set(prefs.rDragonXp().get() + gain)
        lastGain = gain
        lastMultiplier = mult
        lastCombo = count
        onGain?.let { cb ->
            val total = prefs.rDragonXp().get().toLong()
            android.os.Handler(android.os.Looper.getMainLooper()).post { cb(gain, total) }
        }
        return gain
    }
}
