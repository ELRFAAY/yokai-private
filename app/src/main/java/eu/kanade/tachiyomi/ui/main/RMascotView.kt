package eu.kanade.tachiyomi.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.injectLazy
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AnimationUtils
import eu.kanade.tachiyomi.R
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * R: the small snake mascot that floats on the home screen (Settings > R > "Mascot").
 *
 * - It is a full-screen transparent overlay, but it only takes touches that land ON the snake;
 *   every other touch goes through to the app underneath (onTouchEvent returns false).
 * - Grab it and drag it; release with speed and it flies with a dotted trail, bounces on the screen
 *   edges, and slows down until it rests again.
 * - Reactions: it wobbles when you tap it, shakes when it hits a wall, and talks in speech bubbles.
 * - Faces: one picture per mood (happy, hurt, scared, dizzy, sad, blink, back view), see [Mood] and [currentMood].
 *
 * The phrases are in [GRAB], [THROW], [WALL], [CEILING], [LAND] and [TAP] below: edit them freely.
 */
class RMascotView(context: Context) : View(context) {

    private enum class State { IDLE, GRABBED, FLYING }

    private enum class Mood { HAPPY, HURT, SCARED, DIZZY, SAD, BLINK, BACK }

    private class Dot(val x: Float, val y: Float, var life: Float)

    /**
     * A tiny damped spring. [value] is pulled toward a target and overshoots a little before settling,
     * which is what makes the movement feel soft and alive instead of snapping.
     */
    private class Spring(var value: Float, private val stiffness: Float, private val damping: Float) {
        var velocity = 0f

        fun step(target: Float, dt: Float) {
            velocity += ((target - value) * stiffness - velocity * damping) * dt
            value += velocity * dt
        }

        fun kick(extraVelocity: Float) {
            velocity += extraVelocity
        }
    }

    private val density = resources.displayMetrics.density
    // half of the little dragon's size. Settings > R > size (r_dragon_size), the same value the reader uses; refreshed every frame
    private var half = 36f * density

    private fun refreshSize() {
        half = prefs.rDragonSize().get().coerceIn(40, 160) / 2f * density
    }

    /** Extra height of the current costume above the normal dragon (tall hats): the speech bubble sits above it. */
    private var headroom = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private fun load(id: Int): Bitmap? = try {
        BitmapFactory.decodeResource(resources, id)
    } catch (e: Throwable) {
        null
    }

    private val faces: Map<Mood, Bitmap?> = mapOf(
        Mood.HAPPY to load(R.drawable.r_mascot_happy),
        Mood.HURT to load(R.drawable.r_mascot_hurt),
        Mood.SCARED to load(R.drawable.r_mascot_scared),
        Mood.DIZZY to load(R.drawable.r_mascot_dizzy),
        Mood.SAD to load(R.drawable.r_mascot_sad),
        Mood.BLINK to load(R.drawable.r_mascot_blink),
        Mood.BACK to load(R.drawable.r_mascot_back),
    )

    // the wise old snake (hat and beard): sometimes shows up when you tap, and gives useless advice
    private val sageFaces: Map<Mood, Bitmap?> by lazy {
        mapOf(
            // calm / squint / side come from the owner's second wise-dragon sheet (r_sage2_*); scared and dizzy keep the old art
            Mood.HAPPY to load(R.drawable.r_sage2_calm),
            Mood.HURT to load(R.drawable.r_sage2_squint),
            Mood.SCARED to load(R.drawable.r_sage_scared),
            Mood.DIZZY to load(R.drawable.r_sage_dizzy),
            Mood.SAD to load(R.drawable.r_sage2_side),
            Mood.BLINK to load(R.drawable.r_sage2_squint),
            // there is no sage back view: the normal one is used while flying away fast
        )
    }
    private val prefs: PreferencesHelper by injectLazy()
    private var sageUntil = 0L

    // tap = magic transformation: hand seal -> smoke -> cloud -> random character -> back to normal
    private var tfStart = 0L
    private var tfForm = 0
    private var tfSpoke = false
    private val tfBitmaps = HashMap<Int, Bitmap?>()
    private fun tfBitmap(id: Int): Bitmap? = tfBitmaps.getOrPut(id) { load(id) }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6BFF6B.toInt() }
    private val bubbleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2E7D32.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1B1B1B.toInt()
        textSize = 19f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val dest = RectF()
    private val bubbleRect = RectF()
    private val bubblePath = Path()

    private var state = State.IDLE
    private var positioned = false
    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f
    private var targetX = 0f // where the finger wants the snake; the snake eases toward it
    private var targetY = 0f
    private var dragVx = 0f // smoothed drag speed, used to lean the snake
    private var grabDx = 0f
    private var grabDy = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var tracker: VelocityTracker? = null

    private val dots = ArrayList<Dot>()
    private var lastDotX = 0f
    private var lastDotY = 0f

    private var hurtUntil = 0L
    private var happyUntil = 0L
    private var dizzyUntil = 0L
    private var sadUntil = 0L
    private var bounces = 0 // hard hits during the current flight
    private var bubbleText: String? = null
    private var bubbleUntil = 0L
    private var bubbleStart = 0L
    private var bubbleShown = 0 // letters of the bubble that are visible (typewriter effect)

    // tiny "talking" blips, generated in code (no sound files in the app)
    private var soundPool: SoundPool? = null
    private var blipId = 0
    private var blipReady = false
    private var blipCount = 0
    private var poofId = 0
    private var poofReady = 0 // number of loaded sounds (blip + poof) - ready when 2
    private var welcomeGapMs = -1L // >= 0: the app was just opened after this long away, greet once the view is showing
    private var enterStart = 0L // != 0: the little dragon is dropping in from the top right
    private var asleep = false
    private var lastActiveAt = SystemClock.uptimeMillis()
    private var blanketT = 0f // 0 = in bed with the blanket up, 1 = fully asleep; the bed pictures follow this value
    private var yawnUntil = 0L
    private var waveUntil = 0L
    private var whereUntil = 0L

    // pictures cut from the owner's sheets (rA2 tail wag, rB mouth, rC2 wave, rD poses, p3 bed); all keep the short wide body
    private val wagFrames: List<Bitmap?> by lazy {
        listOf(R.drawable.r_mascot_wag1, R.drawable.r_mascot_wag2, R.drawable.r_mascot_wag3, R.drawable.r_mascot_wag4, R.drawable.r_mascot_wag5).map { load(it) }
    }
    private val waveFrames: List<Bitmap?> by lazy {
        listOf(
            R.drawable.r_mascot_wave1, R.drawable.r_mascot_wave2, R.drawable.r_mascot_wave3,
            R.drawable.r_mascot_wave4, R.drawable.r_mascot_wave5, R.drawable.r_mascot_wave6,
        ).map { load(it) }
    }
    private val talkFrames: List<Bitmap?> by lazy { listOf(R.drawable.r_mascot_talk1, R.drawable.r_mascot_talk2).map { load(it) } }
    private val yawnBmp: Bitmap? by lazy { load(R.drawable.r_mascot_yawn) }
    private val whereBmp: Bitmap? by lazy { load(R.drawable.r_mascot_where) }
    private val cheerBmp: Bitmap? by lazy { load(R.drawable.r_mascot_cheer) } // arms up: after a landing
    private val tongueBmp: Bitmap? by lazy { load(R.drawable.r_mascot_tongue) } // tongue out: when you tap him
    private val sageTalkBmp: Bitmap? by lazy { load(R.drawable.r_sage2_talk) } // wise dragon, mouth open while he talks
    private val sagePointBmp: Bitmap? by lazy { load(R.drawable.r_sage2_point) } // wise dragon points when he starts his advice
    private var cheerUntil = 0L
    private var tongueUntil = 0L
    private var sageStart = 0L
    private val bedLie: Bitmap? by lazy { load(R.drawable.r_bed_lie) }
    private val bedCover: Bitmap? by lazy { load(R.drawable.r_bed_cover) }
    private val bedSleep: Bitmap? by lazy { load(R.drawable.r_bed_sleep) }
    private val blanketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1F6F6B.toInt() }
    private val blanketStripe = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF49B6A8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
    }
    private val blanketEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEAF7F2.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    private val blanketRect = RectF()
    private val blanketPath = Path()
    private val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB7F5B9.toInt()
        isFakeBoldText = true
        setShadowLayer(3f * density, 0f, 0f, 0xFF1B5E20.toInt())
    }
    private var tfPoofs = 0 // how many "poof" sounds were played in the current transformation

    // soft, springy look: every visual value eases toward a target instead of jumping
    private val tiltSpring = Spring(0f, 220f, 18f)
    private val scaleXSpring = Spring(1f, 300f, 16f)
    private val scaleYSpring = Spring(1f, 300f, 16f)
    private var stretch = 0f // squash and stretch along the flight direction
    private var bobFactor = 1f // 1 = bobbing while resting, 0 = not bobbing

    // face crossfade
    private var shownMood = Mood.HAPPY
    private var shownSage = false
    private var shownFrameSprite = false
    private var shownBitmap: Bitmap? = null
    private var fromBitmap: Bitmap? = null
    private var fade = 1f
    private var fadeSeconds = 0.14f
    private var showBack = false // with hysteresis, so the back view does not flicker at the speed limit
    private var blinkAt = 0L

    private var running = false
    private var lastFrame = 0L
    private var frameNow = SystemClock.uptimeMillis() // the time of the frame being drawn (stays in sync with step)

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            step(AnimationUtils.currentAnimationTimeMillis())
            invalidate()
            postOnAnimation(this)
        }
    }

    // ---- life cycle: the animation loop only runs while the snake is actually on screen ----

    private fun startLoop() {
        if (running || !isAttachedToWindow || !isShown) return
        running = true
        lastFrame = SystemClock.uptimeMillis()
        blinkAt = lastFrame + 3000
        // coming back to the screen: awake, with a fresh sleep timer
        asleep = false
        blanketT = 0f
        lastActiveAt = lastFrame
        postOnAnimation(frame)
    }

    private fun stopLoop() {
        running = false
        removeCallbacks(frame)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startLoop()
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        soundPool?.release()
        soundPool = null
        blipReady = false
        poofReady = 0
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startLoop() else stopLoop()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            if (prefs.rMascotWelcome().get()) {
                val last = prefs.rLastOpenMs().get()
                val gap = System.currentTimeMillis() - last
                // first run (last == 0) says nothing; a quick app switch (< 3 h) says nothing
                if (last > 0L && gap >= WELCOME_MIN_GAP_MS) welcomeGapMs = gap
            }
            startLoop()
        } else {
            prefs.rLastOpenMs().set(System.currentTimeMillis())
            stopLoop()
        }
    }

    private fun startWelcome(now: Long) {
        val days = (welcomeGapMs / DAY_MS).toInt()
        welcomeGapMs = -1L
        prefs.rLastOpenMs().set(System.currentTimeMillis())
        if (state != State.IDLE || tfStart != 0L) return
        enterStart = now
        waveUntil = now + 2400L
        if (days >= 1) whereUntil = now + 4500L
        val name = prefs.rProfileName().get().trim()
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val who = if (name.isNotEmpty()) " يا $name" else ""
        val text = when {
            days <= 0 -> when (hour) {
                in 5..11 -> "صباح الفل$who!"
                in 12..17 -> "نهارك فل$who!"
                in 18..23 -> "مساء الخير$who!"
                else -> "لسه صاحي$who؟ مش هتنام؟"
            }
            days == 1 -> "كنت فين$who؟ بقالي يوم مستنيك!"
            days == 2 -> "يومين! ما قريتش من يومين، كنت فين؟"
            days in 3..10 -> "بقالي $days أيام مستنيك! ما قريتش من فترة طويلة."
            else -> "بقالي $days يوم مستنيك! افتكرت إنك نسيتني."
        }
        say(text, 4500L)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        if (!positioned) {
            x = w - half - 16f * density
            y = maxY() - 90f * density
            targetX = x
            targetY = y
            positioned = true
        } else {
            x = x.coerceIn(half, max(half, w - half))
            y = y.coerceIn(minY(), maxY())
        }
    }

    private fun minY() = half + 24f * density
    private fun maxY() = max(minY(), height - half - 72f * density) // stays above the bottom bar

    // ---- physics ----

    private fun step(now: Long) {
        val dt = ((now - lastFrame) / 1000f).coerceIn(0.001f, 0.033f)
        lastFrame = now
        frameNow = now
        updateTyping(now)
        if (!positioned) return

        // sleep: a minute without any touch -> goodnight, blanket slides up, zzz
        if (prefs.rMascotSleep().get()) {
            if (!asleep && state == State.IDLE && tfStart == 0L && enterStart == 0L && now - lastActiveAt >= SLEEP_AFTER_MS) {
                asleep = true
                say(GO_SLEEP.random(), 3500L)
            }
        } else if (asleep) {
            asleep = false
        }
        val blanketTarget = if (asleep) 1f else 0f
        blanketT = if (blanketT < blanketTarget) minOf(blanketTarget, blanketT + dt / 1.4f) else maxOf(blanketTarget, blanketT - dt / 0.5f)

        if (welcomeGapMs >= 0L && isShown) startWelcome(now)
        if (enterStart != 0L) {
            // drop in from above the top right corner, easing out; a touch ends it at once
            val p = ((now - enterStart) / ENTER_MS.toFloat()).coerceIn(0f, 1f)
            val e = 1f - (1f - p) * (1f - p) * (1f - p)
            val destX = width - half - 16f * density
            val destY = minY() + 8f * density
            x = destX + half * 1.8f * (1f - e)
            y = destY - half * 2.2f * (1f - e)
            if (p >= 1f || state != State.IDLE) enterStart = 0L
            if (p >= 1f) {
                targetX = x
                targetY = y
                scaleYSpring.kick(-6f)
            }
        }

        // while dragged the snake glides toward the finger instead of jumping to it
        if (state == State.GRABBED && moved) {
            val k = 1f - exp(-dt * 26f)
            val before = x
            x += (targetX - x) * k
            y += (targetY - y) * k
            dragVx += ((x - before) / dt - dragVx) * (1f - exp(-dt * 12f))
        } else {
            dragVx *= exp(-dt * 10f)
        }

        if (state == State.FLYING) {
            x += vx * dt
            y += vy * dt
            val damp = 0.4f.pow(dt)
            vx *= damp
            vy *= damp

            val minX = half
            val maxX = max(half, width - half)
            var hitWall = false
            var hitCeiling = false
            val impact = (hypot(vx, vy) / (2200f * density)).coerceIn(0.25f, 1f) * 6f
            if (x < minX) {
                x = minX
                vx = abs(vx) * 0.8f
                hitWall = true
                scaleXSpring.kick(-impact)
                scaleYSpring.kick(impact * 0.6f)
            } else if (x > maxX) {
                x = maxX
                vx = -abs(vx) * 0.8f
                hitWall = true
                scaleXSpring.kick(-impact)
                scaleYSpring.kick(impact * 0.6f)
            }
            if (y < minY()) {
                y = minY()
                vy = abs(vy) * 0.8f
                hitCeiling = true
                scaleYSpring.kick(-impact)
                scaleXSpring.kick(impact * 0.6f)
            } else if (y > maxY()) {
                y = maxY()
                vy = -abs(vy) * 0.8f
                hitWall = true
                scaleYSpring.kick(-impact)
                scaleXSpring.kick(impact * 0.6f)
            }

            val speed = hypot(vx, vy)
            if ((hitWall || hitCeiling) && speed > 300f * density) {
                hurtUntil = now + 450
                bounces++
                say(if (hitCeiling) CEILING else WALL.random())
            }

            if (hypot(x - lastDotX, y - lastDotY) >= 20f * density) {
                dots.add(Dot(x, y, 1f))
                lastDotX = x
                lastDotY = y
            }

            if (speed < 40f * density) {
                state = State.IDLE
                vx = 0f
                vy = 0f
                // a little bounce when it comes to rest
                scaleYSpring.kick(-4f)
                scaleXSpring.kick(3f)
                when {
                    bounces >= 3 -> {
                        dizzyUntil = now + 2500
                        say(DIZZY_TALK.random())
                    }
                    bounces >= 1 -> {
                        sadUntil = now + 1800
                        say(SAD_TALK.random())
                    }
                    else -> {
                        happyUntil = now + 1200
                        run { cheerUntil = SystemClock.uptimeMillis() + 1800L; say(LAND.random()) }
                    }
                }
                bounces = 0
            }
        }

        updateLook(now, dt)

        val iterator = dots.iterator()
        while (iterator.hasNext()) {
            val dot = iterator.next()
            dot.life -= dt * 1.2f
            if (dot.life <= 0f) iterator.remove()
        }
    }

    /** Eases every visual value (tilt, size, stretch, face) toward where it should be right now. */
    private fun updateLook(now: Long, dt: Float) {
        val idle = state == State.IDLE
        val grabbedMoving = state == State.GRABBED && moved
        val happy = idle && now < happyUntil
        val speed = hypot(vx, vy)

        val tiltTarget = when {
            state == State.FLYING -> (vx / (1500f * density)).coerceIn(-1f, 1f) * 25f
            grabbedMoving -> (dragVx / (1200f * density)).coerceIn(-1f, 1f) * 14f
            happy -> sin(now / 120f) * 6f
            else -> 0f
        }
        val baseScale = if (grabbedMoving) 1.1f else 1f
        val wobble = if (happy) sin(now / 90f) * 0.06f else 0f
        tiltSpring.step(tiltTarget, dt)
        scaleXSpring.step(baseScale * (1f + wobble), dt)
        scaleYSpring.step(baseScale * (1f - wobble), dt)

        val stretchTarget = if (state == State.FLYING) (speed / (2600f * density)).coerceIn(0f, 0.2f) else 0f
        stretch += (stretchTarget - stretch) * (1f - exp(-dt * 10f))
        bobFactor += ((if (idle) 1f else 0f) - bobFactor) * (1f - exp(-dt * 8f))

        // blink at random moments instead of on a fixed clock
        if (now >= blinkAt + BLINK_MS) blinkAt = now + 2500L + (Math.random() * 3000.0).toLong()

        val mood = currentMood(now, idle)
        val sage = now < sageUntil
        var bitmap = (if (sage) sageFaces[mood] else null) ?: faces[mood] ?: faces[Mood.HAPPY]
        var frameSprite = false
        if (!sage && idle && !asleep && tfStart == 0L && mood == Mood.HAPPY) {
            val extra: Bitmap? = when {
                now < waveUntil -> waveFrames.getOrNull(((now / 130L) % waveFrames.size).toInt())
                now < whereUntil -> whereBmp
                now < cheerUntil -> cheerBmp
                now < tongueUntil -> tongueBmp
                now < yawnUntil || (prefs.rMascotSleep().get() && now - lastActiveAt >= SLEEP_AFTER_MS - 2500L) -> yawnBmp
                bubbleText != null && now < bubbleUntil && bubbleShown < (bubbleText?.length ?: 0) ->
                    talkFrames.getOrNull(bubbleShown % 3 - 1) // 0 = closed mouth, 1 and 2 = open
                (now / 1000L) % 9L == 8L && now >= happyUntil ->
                    wagFrames.getOrNull(WAG_ORDER[((now / 140L) % WAG_ORDER.size).toInt()])
                else -> null
            }
            if (extra != null) {
                bitmap = extra
                frameSprite = true
            }
        }
        if (sage && idle && !asleep && tfStart == 0L && mood == Mood.HAPPY) {
            val typing = bubbleText != null && now < bubbleUntil && bubbleShown < (bubbleText?.length ?: 0)
            val extra: Bitmap? = when {
                now - sageStart < 1300L -> sagePointBmp // he starts by pointing at you
                typing && (bubbleShown / 3) % 2 == 1 -> sageTalkBmp // the mouth opens and closes while he talks
                else -> null
            }
            if (extra != null) {
                bitmap = extra
                frameSprite = true
            }
        }
        if (bitmap !== shownBitmap) {
            fromBitmap = shownBitmap
            fade = if (fromBitmap == null) 1f else 0f
            fadeSeconds = when {
                sage != shownSage -> 0.25f // changing into / out of the wise old snake
                mood == Mood.BLINK || shownMood == Mood.BLINK -> 0.05f
                frameSprite || shownFrameSprite -> 0.04f
                else -> 0.14f
            }
            shownBitmap = bitmap
        }
        shownMood = mood
        shownSage = sage
        shownFrameSprite = frameSprite
        if (fade < 1f) fade = (fade + dt / fadeSeconds).coerceAtMost(1f)
    }

    /** Called by MainActivity on every touch anywhere in the app: restarts the sleep timer and wakes the dragon up. */
    fun noteActivity() {
        lastActiveAt = SystemClock.uptimeMillis()
        if (asleep) {
            asleep = false
            happyUntil = lastActiveAt + 1500
            yawnUntil = lastActiveAt + 1300
            say(WAKE.random())
        }
    }

    /**
     * A random costume that has not come out yet in this round. When all of them have come out, a new round starts
     * (and the first costume of the new round is never the last one of the old round). Saved in r_mascot_tf_seen.
     */
    private fun nextCostume(): Int {
        val pref = prefs.rMascotTfSeen()
        var order = pref.get().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in TF_FORMS.indices }.distinct()
        var left = TF_FORMS.indices.filter { it !in order }
        if (left.isEmpty()) {
            val last = order.lastOrNull()
            order = emptyList()
            left = TF_FORMS.indices.filter { it != last }
        }
        val pick = left.random()
        pref.set((order + pick).joinToString(","))
        return pick
    }

    private fun say(text: String, millis: Long = 2200L) {
        val now = SystemClock.uptimeMillis()
        bubbleText = text
        bubbleStart = now
        bubbleShown = 0
        // typing time + the time the whole sentence stays on screen
        bubbleUntil = now + text.length * CHAR_MS + millis
    }

    /** Typewriter: reveal one more letter every [CHAR_MS] and play a short blip for it. */
    private fun updateTyping(now: Long) {
        val text = bubbleText ?: return
        val target = ((now - bubbleStart) / CHAR_MS).toInt().coerceIn(0, text.length)
        if (target > bubbleShown) {
            val c = text[target - 1]
            bubbleShown = target
            if (!c.isWhitespace()) playBlip()
        }
    }

    private fun playBlip() {
        if (!prefs.rMascotSound().get()) return
        val pool = soundPool ?: createSoundPool().also { soundPool = it } ?: return
        if (!blipReady) return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return // silent / vibrate = no sound
        // a few pitches in a row so it sounds like talking, not like a beep
        val rate = BLIP_RATES[blipCount++ % BLIP_RATES.size]
        pool.play(blipId, 0.35f, 0.35f, 1, 0, rate)
    }

    private fun createSoundPool(): SoundPool? = try {
        val pool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (sampleId == blipId) blipReady = status == 0
            if (sampleId == poofId) poofReady = if (status == 0) 1 else 0
        }
        val file = File(context.cacheDir, "r_mascot_blip.wav")
        writeBlipWav(file)
        blipId = pool.load(file.absolutePath, 1)
        val poofFile = File(context.cacheDir, "r_mascot_poof.wav")
        writePoofWav(poofFile)
        poofId = pool.load(poofFile.absolutePath, 1)
        pool
    } catch (e: Throwable) {
        null
    }

    /** Ninja "poof": a soft noise burst with a low thump, 420 ms, written as a 16-bit mono WAV. */
    private fun writePoofWav(file: File) {
        val sampleRate = 22050
        val n = sampleRate * 420 / 1000
        val data = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        val rnd = java.util.Random(7)
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            lp += (rnd.nextDouble() * 2.0 - 1.0 - lp) * 0.35 // low-passed noise = airy smoke
            val noiseEnv = kotlin.math.exp(-t / 0.11) * (1.0 - kotlin.math.exp(-t / 0.004))
            val thumpEnv = kotlin.math.exp(-t / 0.07)
            val thump = sin(2.0 * Math.PI * (110.0 - 60.0 * t) * t)
            val v = lp * 1.6 * noiseEnv + thump * 0.7 * thumpEnv
            data.putShort((v.coerceIn(-1.0, 1.0) * 0.85 * Short.MAX_VALUE).toInt().toShort())
        }
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(0)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36 + n * 2).put("WAVE".toByteArray())
            header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            header.putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
            header.put("data".toByteArray()).putInt(n * 2)
            f.write(header.array())
            f.write(data.array())
        }
    }

    private fun playPoof() {
        if (!prefs.rMascotSound().get()) return
        val pool = soundPool ?: createSoundPool().also { soundPool = it } ?: return
        if (poofReady == 0) return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        pool.play(poofId, 0.8f, 0.8f, 1, 0, 1f)
    }

    /** A 45 ms sine blip with a fast fade-out, written as a 16-bit mono WAV. */
    private fun writeBlipWav(file: File) {
        val sampleRate = 22050
        val n = sampleRate * 45 / 1000
        val data = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = kotlin.math.exp(-i.toDouble() / (n * 0.35)) * (1.0 - kotlin.math.exp(-i / 40.0))
            val v = (sin(2.0 * Math.PI * 520.0 * t) * 0.8 + sin(2.0 * Math.PI * 1040.0 * t) * 0.2) * env
            data.putShort((v * 0.8 * Short.MAX_VALUE).toInt().toShort())
        }
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(0)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36 + n * 2).put("WAVE".toByteArray())
            header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            header.putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
            header.put("data".toByteArray()).putInt(n * 2)
            f.write(header.array())
            f.write(data.array())
        }
    }

    /** The wise old snake takes over for a few seconds and says something that helps nobody. */
    private fun sageAdvice(now: Long) {
        sageUntil = now + 7500
        sageStart = now
        happyUntil = now + 1200
        say(ADVICE.random(), 7000L)
    }

    // ---- touch: only the snake itself takes touches ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!positioned || tfStart != 0L || hypot(event.x - x, event.y - y) > half * 1.1f) return false
                state = State.GRABBED
                grabDx = x - event.x
                grabDy = y - event.y
                downX = event.x
                downY = event.y
                targetX = x
                targetY = y
                dragVx = 0f
                moved = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(event) }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (state != State.GRABBED) return false
                tracker?.addMovement(event)
                if (!moved && hypot(event.x - downX, event.y - downY) > touchSlop) {
                    moved = true
                    say(GRAB.random())
                }
                if (moved) {
                    targetX = (event.x + grabDx).coerceIn(half, max(half, width - half))
                    targetY = (event.y + grabDy).coerceIn(minY(), maxY())
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (state != State.GRABBED) return false
                tracker?.addMovement(event)
                tracker?.computeCurrentVelocity(1000)
                var tvx = tracker?.xVelocity ?: 0f
                var tvy = tracker?.yVelocity ?: 0f
                tracker?.recycle()
                tracker = null

                val now = SystemClock.uptimeMillis()
                val speed = hypot(tvx, tvy)
                val maxSpeed = 4500f * density / 2.75f
                if (speed > maxSpeed) {
                    tvx *= maxSpeed / speed
                    tvy *= maxSpeed / speed
                }
                if (!moved) {
                    state = State.IDLE
                    happyUntil = now + 900
                    tiltSpring.kick(if (Math.random() < 0.5) -280f else 280f)
                    scaleYSpring.kick(-5f)
                    val chance = prefs.rMascotSageChance().get()
                    if (prefs.rMascotTransform().get()) {
                        tfStart = now
                        tfForm = nextCostume()
                        tfSpoke = false
                        tfPoofs = 0
                        if (soundPool == null && prefs.rMascotSound().get()) soundPool = createSoundPool() // load the poof early
                    } else if (prefs.rMascotSage().get() && Math.random() * 100.0 < chance) {
                        sageAdvice(now)
                    } else {
                        tongueUntil = SystemClock.uptimeMillis() + 1500L
                        say(TAP.random())
                    }
                } else if (speed > 350f * density) {
                    state = State.FLYING
                    vx = tvx
                    vy = tvy
                    bounces = 0
                    lastDotX = x
                    lastDotY = y
                    say(THROW.random())
                } else {
                    state = State.IDLE
                    happyUntil = now + 900
                    run { cheerUntil = SystemClock.uptimeMillis() + 1800L; say(LAND.random()) }
                }
                invalidate()
                return true
            }
        }
        return false
    }

    // ---- drawing ----

    override fun onDraw(canvas: Canvas) {
        if (!positioned) return

        for (dot in dots) {
            dotPaint.alpha = (dot.life.coerceIn(0f, 1f) * 200f).toInt()
            canvas.drawCircle(dot.x, dot.y, (3f + 3f * dot.life) * density, dotPaint)
        }

        val now = frameNow
        val idle = state == State.IDLE
        val bob = sin(now / 380f) * 4f * density * bobFactor
        val breath = if (idle) sin(now / 650f) * 0.018f else 0f
        // the shake fades out smoothly instead of stopping dead
        val shake = sin(now / 22f) * 5f * density * ((hurtUntil - now) / 450f).coerceIn(0f, 1f)

        refreshSize()
        var tfBmp: Bitmap? = null
        var tfCostume = false
        headroom = 0f
        if (tfStart != 0L) {
            val t = now - tfStart
            val form = TF_FORMS[tfForm]
            // "poof!" when the smoke covers the snake and again when it turns back
            if (tfPoofs == 0 && t >= TF_SEAL + TF_WRAP) { tfPoofs = 1; playPoof() }
            if (tfPoofs == 1 && t >= TF_SEAL + TF_WRAP + TF_CLOUD + TF_HOLD) { tfPoofs = 2; playPoof() }
            val id = when {
                t < TF_SEAL -> R.drawable.r_tf_seal
                t < TF_SEAL + TF_WRAP -> R.drawable.r_tf_wrap
                t < TF_SEAL + TF_WRAP + TF_CLOUD -> R.drawable.r_tf_cloud
                t < TF_SEAL + TF_WRAP + TF_CLOUD + TF_HOLD -> {
                    if (!tfSpoke) {
                        tfSpoke = true
                        say(form.second, TF_HOLD - 600L)
                    }
                    form.first
                }
                t < TF_SEAL + TF_WRAP + TF_CLOUD + TF_HOLD + TF_CLOUD -> R.drawable.r_tf_cloud
                else -> 0
            }
            if (id == 0) {
                tfStart = 0L
            } else {
                tfBmp = tfBitmap(id)
                tfCostume = id == form.first
            }
        }
        if (tfBmp == null && blanketT > 0f) {
            // in bed: lie down -> blanket up -> asleep (the picture already has its zzz); waking shows the blanket picture
            tfBmp = when {
                !asleep -> bedCover
                blanketT < 0.35f -> bedLie
                blanketT < 0.8f -> bedCover
                else -> bedSleep
            }
        }
        val current = tfBmp ?: shownBitmap ?: return
        val previous = if (tfBmp == null && fade < 1f) fromBitmap else null

        canvas.save()
        canvas.translate(x + shake, y + bob)
        canvas.rotate(tiltSpring.value)
        if (stretch > 0.005f) {
            // squash and stretch: longer along the flight direction, thinner across it
            val deg = Math.toDegrees(atan2(vy, vx).toDouble()).toFloat()
            canvas.rotate(deg)
            canvas.scale(1f + stretch, 1f / (1f + stretch))
            canvas.rotate(-deg)
        }
        canvas.scale(scaleXSpring.value * (1f - breath * 0.5f), scaleYSpring.value * (1f + breath))
        if (tfBmp != null && tfCostume) {
            // a costume has the dragon's own pixel scale and bottom line: exactly the normal size, tall hats reach higher
            val kk = half * 2f / 384f
            val w2 = tfBmp.width * kk / 2f
            val h = tfBmp.height * kk
            dest.set(-w2, half - h, w2, half)
            headroom = (h - half * 2f).coerceAtLeast(0f)
        } else if (tfBmp != null) {
            val box = half * 2.3f
            val k = minOf(box / tfBmp.width, box / tfBmp.height)
            val w2 = tfBmp.width * k / 2f
            val h2 = tfBmp.height * k / 2f
            dest.set(-w2, -h2, w2, h2)
        } else {
            dest.set(-half, -half, half, half)
        }
        if (previous != null) {
            bitmapPaint.alpha = 255
            canvas.drawBitmap(previous, null, dest, bitmapPaint)
        }
        bitmapPaint.alpha = (fade * 255f).toInt().coerceIn(0, 255)
        canvas.drawBitmap(current, null, dest, bitmapPaint)
        bitmapPaint.alpha = 255
        canvas.restore()

        drawBubble(canvas, now)
    }

    /** A teal quilt with diagonal stripes that slides up over the lower half of the dragon. */
    private fun drawBlanket(canvas: Canvas) {
        val t = blanketT.coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)
        val slide = (1f - eased) * half * 1.4f
        blanketRect.set(-half * 0.95f, half * 0.1f + slide, half * 0.95f, half * 1.0f + slide)
        val radius = 12f * density
        blanketPath.reset()
        blanketPath.addRoundRect(blanketRect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(blanketPath)
        canvas.drawRect(blanketRect, blanketPaint)
        var sx = blanketRect.left - blanketRect.height()
        while (sx < blanketRect.right) {
            canvas.drawLine(sx, blanketRect.bottom, sx + blanketRect.height(), blanketRect.top, blanketStripe)
            sx += 18f * density
        }
        canvas.restore()
        canvas.drawRoundRect(blanketRect, radius, radius, blanketEdge)
    }

    /** Three small "z" letters drifting up from the sleeping dragon. */
    private fun drawZzz(canvas: Canvas, now: Long) {
        for (i in 0 until 3) {
            val p = (((now / 1000f) + i * 0.9f) % 2.7f) / 2.7f
            zPaint.alpha = ((1f - p) * 255f).toInt().coerceIn(0, 255)
            zPaint.textSize = (12f + 8f * p + i * 2f) * density
            canvas.drawText("z", x + half * 0.35f + p * half * 0.5f + i * 6f * density, y - half * 0.55f - p * half * 0.9f, zPaint)
        }
    }

    /** Which of the pictures to show right now. */
    private fun currentMood(now: Long, idle: Boolean): Mood {
        val speed = hypot(vx, vy)
        showBack = if (showBack) speed > 560f * density else speed > 760f * density
        return when {
            asleep -> Mood.BLINK // eyes closed
            now < hurtUntil -> Mood.HURT
            state == State.GRABBED && moved -> Mood.SCARED
            state == State.FLYING -> when {
                bounces >= 3 -> Mood.DIZZY
                showBack -> Mood.BACK // flying away fast: we see its back
                else -> Mood.SCARED
            }
            idle && now < dizzyUntil -> Mood.DIZZY
            idle && now < sadUntil -> Mood.SAD
            idle && now >= happyUntil && now >= blinkAt && now < blinkAt + BLINK_MS -> Mood.BLINK
            else -> Mood.HAPPY
        }
    }

    /** Splits [text] into lines no wider than [maxWidth] (long advice does not fit on one line). */
    private fun wrap(text: String, maxWidth: Float): List<String> {
        val lines = ArrayList<String>()
        var current = ""
        for (word in text.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isEmpty() || textPaint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                lines.add(current)
                current = word
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }

    private fun drawBubble(canvas: Canvas, now: Long) {
        val text = bubbleText ?: return
        if (now > bubbleUntil) {
            bubbleText = null
            return
        }
        val pad = 10f * density
        val margin = 8f * density
        val fm = textPaint.fontMetrics
        val lineHeight = fm.descent - fm.ascent
        val lines = wrap(text, min(width - margin * 2f - pad * 2f, 250f * density))
        val textWidth = lines.maxOfOrNull { textPaint.measureText(it) } ?: return
        val bw = textWidth + pad * 2f
        val bh = lineHeight * lines.size + pad * 1.4f

        val left = (x - bw / 2f).coerceIn(margin, max(margin, width - bw - margin))
        var top = y - half - headroom - bh - 14f * density
        // R: stay under the status bar (the view can start behind it, which cut the bubble at the top)
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val statusTop = androidx.core.view.ViewCompat.getRootWindowInsets(this)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())?.top ?: 0
        val safeTop = max(4f * density, statusTop - loc[1] + 6f * density)
        val below = top < safeTop
        if (below) top = y + half + 14f * density
        bubbleRect.set(left, top, left + bw, top + bh)

        val radius = 14f * density
        canvas.drawRoundRect(bubbleRect, radius, radius, bubbleFill)
        canvas.drawRoundRect(bubbleRect, radius, radius, bubbleStroke)

        val tailX = x.coerceIn(bubbleRect.left + 16f * density, max(bubbleRect.left + 16f * density, bubbleRect.right - 16f * density))
        val edge = if (below) bubbleRect.top else bubbleRect.bottom
        val tip = if (below) edge - 10f * density else edge + 10f * density
        bubblePath.reset()
        bubblePath.moveTo(tailX - 7f * density, edge)
        bubblePath.lineTo(tailX + 7f * density, edge)
        bubblePath.lineTo(tailX, tip)
        bubblePath.close()
        canvas.drawPath(bubblePath, bubbleFill)

        // the bubble has the size of the whole sentence; the letters appear one by one from the reading side
        val rtl = text.firstOrNull { Character.isLetter(it) }
            ?.let { Character.getDirectionality(it) == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC } ?: false
        textPaint.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        var remaining = bubbleShown.coerceIn(0, text.length)
        lines.forEachIndexed { i, line ->
            val shown = line.substring(0, remaining.coerceIn(0, line.length))
            remaining -= line.length + 1
            if (shown.isNotEmpty()) {
                val tx = if (rtl) bubbleRect.right - pad else bubbleRect.left + pad
                canvas.drawText(shown, tx, bubbleRect.top + pad * 0.7f - fm.ascent + lineHeight * i, textPaint)
            }
        }
        textPaint.textAlign = Paint.Align.CENTER
    }

    private companion object {
        const val BLINK_MS = 140L
        const val TF_SEAL = 450L
        const val TF_WRAP = 450L
        const val TF_CLOUD = 450L
        const val TF_HOLD = 4200L
        // the costumes from the owner's sheets (branding/mascot-models/costumes-2026-11-21); the phrases are my drafts, NOT approved.
        // One of each kind only: the sheets repeated a detective, a chef and an explorer, those repeats are left out.
        val TF_FORMS = listOf(
            R.drawable.r_costume_knight to "أنا الفارس! محدش يقرب من المانجا دي!",
            R.drawable.r_costume_detective to "فيه شابتر ناقص... وأنا هاعرف مين خدّه!",
            R.drawable.r_costume_pirate to "هاااار! المانجا دي كنزي أنا!",
            R.drawable.r_costume_guard to "حارس ملكي! ممنوع حرق الأحداث في حضرتي!",
            R.drawable.r_costume_chef to "الطبخة جاهزة... والشابتر كمان!",
            R.drawable.r_costume_astronaut to "هبوط على كوكب المانجا... خطوة صغيرة!",
            R.drawable.r_costume_pharaoh to "أنا فرعون المانجا! اقرا واسكت!",
            R.drawable.r_costume_doctor to "الوصفة: فصلين دلوقتي وفصل قبل النوم.",
            R.drawable.r_costume_firefighter to "الأحداث ولّعت! هاتوا الخرطوم!",
            R.drawable.r_costume_explorer to "بدوّر على فصل ضايع في الغابة...",
            R.drawable.r_costume_magician to "أبرا كا دابرا! الشابتر اختفى!",
            R.drawable.r_costume_painter to "أنا اللي رسمت الغلاف ده! صدّقني!",
            R.drawable.r_costume_rockstar to "الفصل ده يستاهل حفلة!",
            R.drawable.r_costume_executive to "اجتماع الساعة تسعة: قراءة المانجا.",
            R.drawable.r_costume_pilot to "معاكم القبطان: اربطوا الأحزمة، الأحداث هتسخن!",
            R.drawable.r_costume_builder to "بنيت لك مكتبة جديدة... لسه بتتبني!",
            R.drawable.r_costume_alchemist to "الخلطة السرية: مانجا وسهر!",
            R.drawable.r_costume_opera to "آآآآه! كمّل قراءة بهدوء بس!",
            R.drawable.r_costume_bonsai to "الصبر يا صاحبي... زي صبر الفصل الجاي.",
            R.drawable.r_costume_steampunk to "أقلعنا! وجهتنا الفصل المية!",
            R.drawable.r_costume_scribe to "كتبت لك ملخص الفصل بالهيروغليفي.",
        )
        const val CHAR_MS = 38L // typing speed of the speech bubble
        val GO_SLEEP = listOf("هنام شوية... صحّيني لما ترجع", "الدنيا هادية... هجيب اللحاف وأنام", "تصبح على خير، المانجا مش هتهرب")
        val WAKE = listOf("هاه؟ صحيت!", "كنت في أحلى حلم...", "خلاص خلاص، صاحي!")
        const val SLEEP_AFTER_MS = 60_000L
        val WAG_ORDER = intArrayOf(0, 1, 2, 3, 4, 3, 2, 1)
        const val WELCOME_MIN_GAP_MS = 2 * 60_000L // R: greet after 2 minutes away (was 3 h)
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val ENTER_MS = 800L
        val BLIP_RATES = floatArrayOf(1.0f, 1.18f, 0.9f, 1.32f, 1.08f)
        val GRAB = listOf("سيبني بقى!", "إنت ماسكني ليه؟", "هششش! أنا التعبان!")
        val THROW = listOf("آآآآه!", "ارمي براحة!", "للانهاية وما بعدها!")
        val WALL = listOf("آي! الحيطة!", "كده هتكسر دماغي!", "بيوجع والله!")
        const val CEILING = "خد بالك من السقف!"
        val LAND = listOf("كان ممتع! تاني!", "تاني تاني!", "أنا لسه بخير")
        val DIZZY_TALK = listOf("الدنيا بتلف بيا...", "أنا شايف نجوم!", "لفّلي دماغي!")
        val SAD_TALK = listOf("حرام عليك...", "ده أنا زعلان!", "كده يا صاحبي؟")
        val TAP = listOf("أيوه؟", "عايز إيه؟", "بس بتدغدغني!")

        /** The wise old snake's advice: all of it true, none of it useful. On purpose. */
        val ADVICE = listOf(
            "نصيحة الحكيم: لو قرأت فصل، تكون قرأت فصل.",
            "تذكّر دايماً: الصفحة الأخيرة بتيجي بعد الصفحة اللي قبلها.",
            "لو الشاشة اتطفت، شغّلها. وده سر النجاح.",
            "اللي بينام مش بيقرأ، واللي بيقرأ مش بينام. خد بالك.",
            "الماية مبلولة. أنا قلت لك دلوقتي.",
            "الأحسن تقلب الصفحة بإصبعك، مش بمناخيرك.",
            "لو التطبيق مفتوح، فإنت جواه.",
            "المانجا بتخلص لما تخلص.",
            "الفصل الجاي بعد الفصل اللي فات، ودايماً كده.",
            "لو جعت، الأكل ممكن يساعد.",
            "الحكمة بتقول: اللي ما يعرفش، ما يعرفش.",
            "السر في القراءة إنك تقرأ.",
            "كل شيء بيبدأ بخطوة، وأحياناً بفصل.",
            "الصبر مفتاح الفرج، ومفتاح الباب كمان.",
            "النهاية السعيدة أحسن من النهاية الحزينة. في الغالب.",
            "ما تقلقش على الأحداث الجاية، هتيجي لوحدها.",
            "اللي يقرأ بسرعة يخلص بسرعة، واللي يقرأ ببطء يخلص ببطء.",
            "اشرب ماية. مش لازم دلوقتي، بس في وقت ما.",
            "لو غمّضت عينيك، الصفحات بتختفي. جرّب بنفسك.",
            "لو الفصل طويل، اقرأه على مرتين. أو مرة. أو ما تقراهوش.",
            "اقلب الموبايل مقلوب وهتشوف المانجا مقلوبة. دي نصيحة ذهب.",
            "الأسبوع فيه سبع أيام، كلهم مناسبين للقراءة.",
            "لو لقيت نفسك بتقرأ، يبقى إنت بتقرأ.",
            "الصفحة اللي ما فتحتهاش، ما قرأتهاش. اتعلّم من الحكيم.",
        )
    }
}
