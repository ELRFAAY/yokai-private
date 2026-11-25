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
        // the dragon grows with reading: x0.80 at level 1 up to x1.15 at level 12 (RDragonLevel)
        half = prefs.rDragonSize().get().coerceIn(40, 160) / 2f * density *
            eu.kanade.tachiyomi.util.RDragonLevel.sizeFactor(eu.kanade.tachiyomi.util.RDragonLevel.info(eu.kanade.tachiyomi.util.RDragonLevel.totalXp(prefs)).level)
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
            // all from the owner's p5 wise-dragon sheet (branding/tools/make_sheets_2026_11_21b.py)
            Mood.HAPPY to load(R.drawable.r_sage_happy),
            Mood.HURT to load(R.drawable.r_sage_hurt),
            Mood.SCARED to load(R.drawable.r_sage_scared),
            Mood.DIZZY to load(R.drawable.r_sage_dizzy),
            Mood.SAD to load(R.drawable.r_sage_sad),
            Mood.BLINK to load(R.drawable.r_sage_blink),
            // there is no sage back view: the normal one is used while flying away fast
        )
    }
    private val prefs: PreferencesHelper by injectLazy()
    private var sageUntil = 0L

    // tap = magic transformation: hand seal -> smoke -> cloud -> random character -> back to normal
    private var tfStart = 0L
    private var tfForm = 0
    private var tfSpoke = false
    private var tfEvo = 0 // > 0 while the egg evolution plays: the new level

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

    // R: the drop-in welcome with the E2 sheet (falling, squash, bounce, sitting and waving)
    private var e2Start = 0L
    private var e2LandAt = 0L
    private val e2Frames: List<Bitmap?> by lazy {
        listOf(
            R.drawable.r_e2_0, R.drawable.r_e2_1, R.drawable.r_e2_2, R.drawable.r_e2_3,
            R.drawable.r_e2_4, R.drawable.r_e2_5, R.drawable.r_e2_6, R.drawable.r_e2_7,
        ).map { load(it) }
    }

    private fun e2Frame(now: Long): Bitmap? {
        if (e2Start == 0L || state != State.IDLE) return null
        if (enterStart != 0L) return e2Frames.getOrNull(((now / 120L) % 2L).toInt()) // falling, arms up / hood flapping
        if (e2LandAt == 0L) return null
        val t = now - e2LandAt
        val idx = when {
            t < 130L -> 2 // squash + dust
            t < 270L -> 3 // max squash
            t < 520L -> 4 // bounce, stretched, dizzy
            t < 800L -> 5 // small bounce, relieved
            t < 1300L -> 6 // settling, shaking his head
            t < 2600L -> 7 // sitting, waving
            else -> return null
        }
        return e2Frames.getOrNull(idx)
    }

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
    private val sageTalkBmp: Bitmap? by lazy { load(R.drawable.r_sage2_laugh) } // wise dragon, mouth open while he talks
    private val shockBmp: Bitmap? by lazy { load(R.drawable.r_mascot_shock) } // arms out, wide eyes: while he flies
    private val fist1Bmp: Bitmap? by lazy { load(R.drawable.r_mascot_fist1) } // fists up: while you hold him still
    private val fist2Bmp: Bitmap? by lazy { load(R.drawable.r_mascot_fist2) }
    private var lastTapAt = 0L // two quick taps = the costume transformation
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
        if (prefs.rWelcomeE2().get()) {
            e2Start = now
            e2LandAt = 0L
        }
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
            x = homeX()
            y = homeY()
            targetX = x
            targetY = y
            positioned = true
        } else {
            x = x.coerceIn(half, max(half, w - half))
            y = y.coerceIn(minY(), maxY())
        }
    }

    /** R: where the dragon lives: next to the big library title, on the side opposite to it. Set by MainActivity. */
    var homeYProvider: (() -> Float?)? = null
    private var tfShift = 0f // R: costume pushed down while it is taller than the room above it

    private fun homeX() = if (layoutDirection == LAYOUT_DIRECTION_RTL) half + 16f * density else width - half - 16f * density
    private fun homeY() = (homeYProvider?.invoke() ?: (minY() + 8f * density)).coerceIn(minY(), maxY())

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
            val destX = homeX()
            val destY = homeY()
            x = destX + half * 1.8f * (1f - e)
            y = destY - half * 2.2f * (1f - e)
            if (p >= 1f || state != State.IDLE) enterStart = 0L
            if (p >= 1f) {
                targetX = x
                targetY = y
                scaleYSpring.kick(-6f)
                if (e2Start != 0L) e2LandAt = now
            }
        }

        fallGag(now, dt)

        // R: a thrown or dragged dragon does not stay in the middle of the list: a few seconds after the last touch
        // it glides back to its home spot beside the title (smooth, no jump)
        if (fallPhase == 0 && state == State.IDLE && !asleep && tfStart == 0L && enterStart == 0L && now - lastActiveAt >= HOME_AFTER_MS) {
            val hx = homeX()
            val hy = homeY()
            if (hypot(hx - x, hy - y) > 6f * density) {
                val k = 1f - exp(-dt * 2.6f)
                x += (hx - x) * k
                y += (hy - y) * k
                targetX = x
                targetY = y
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
            val damp = 0.07f.pow(dt) // R: loses speed fast: a glide, not a ball (was 0.4)
            vx *= damp
            vy *= damp

            val minX = half
            val maxX = max(half, width - half)
            var hitWall = false
            var hitCeiling = false
            val impact = (hypot(vx, vy) / (2200f * density)).coerceIn(0.25f, 1f) * 6f
            if (x < minX) {
                x = minX
                vx = abs(vx) * 0.5f
                hitWall = true
                scaleXSpring.kick(-impact)
                scaleYSpring.kick(impact * 0.6f)
            } else if (x > maxX) {
                x = maxX
                vx = -abs(vx) * 0.5f
                hitWall = true
                scaleXSpring.kick(-impact)
                scaleYSpring.kick(impact * 0.6f)
            }
            if (y < minY()) {
                y = minY()
                vy = abs(vy) * 0.5f
                hitCeiling = true
                scaleYSpring.kick(-impact)
                scaleXSpring.kick(impact * 0.6f)
            } else if (y > maxY()) {
                y = maxY()
                vy = -abs(vy) * 0.5f
                hitWall = true
                scaleYSpring.kick(-impact)
                scaleXSpring.kick(impact * 0.6f)
            }

            val speed = hypot(vx, vy)
            if ((hitWall || hitCeiling) && speed > 300f * density) {
                hurtUntil = now + 450
                bounces++
                say(if (hitCeiling) CEILING.random() else WALL.random())
            }

            if (hypot(x - lastDotX, y - lastDotY) >= 20f * density) {
                dots.add(Dot(x, y, 1f))
                lastDotX = x
                lastDotY = y
            }

            if (speed < 70f * density) {
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

    /** R: now and then (a few minutes, only when nobody touched it for 20 s) the dragon falls from the top, gets up, and falls again. */
    private fun fallGag(now: Long, dt: Float) {
        if (fallPhase == 0) {
            if (nextFallAt == 0L) nextFallAt = now + 60_000L + (Math.random() * 60_000L).toLong()
            if (!prefs.rMascotFall().get() || !isShown || state != State.IDLE || asleep || tfStart != 0L || enterStart != 0L) return
            if (now < nextFallAt || now - lastActiveAt < 20_000L) return
            // start at the top, right where it lives, then drop
            x = homeX()
            y = minY()
            fallVy = 0f
            fallPhase = 1
            say(FALL_START.random(), 1500L)
            return
        }
        val floor = maxY()
        when (fallPhase) {
            1, 4 -> {
                fallVy += 2600f * density * dt
                y += fallVy * dt
                if (y >= floor) {
                    y = floor
                    scaleYSpring.kick(-9f)
                    scaleXSpring.kick(7f)
                    hurtUntil = now + 300
                    dizzyUntil = now + if (fallPhase == 1) 2600L else 6000L
                    fallUntil = dizzyUntil
                    say((if (fallPhase == 1) FALL_HIT1 else FALL_HIT2).random(), 2600L)
                    fallPhase = if (fallPhase == 1) 2 else 5
                }
            }
            2, 5 -> {
                if (now >= fallUntil) {
                    if (fallPhase == 2) {
                        fallPhase = 3
                        say(FALL_UP.random(), 1800L)
                    } else {
                        fallPhase = 0
                        nextFallAt = now + 240_000L + (Math.random() * 240_000L).toLong()
                    }
                }
            }
            3 -> {
                // climbs back to its spot (smoothly), then drops again
                val k = 1f - exp(-dt * 2.4f)
                x += (homeX() - x) * k
                y += (minY() - y) * k
                if (abs(y - minY()) < 6f * density) {
                    fallVy = 0f
                    fallPhase = 4
                    say(FALL_START.random(), 1500L)
                }
            }
        }
        targetX = x
        targetY = y
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
        if (!sage && tfStart == 0L && e2Start != 0L && !asleep) {
            val f = e2Frame(now)
            if (f != null) {
                bitmap = f
                frameSprite = true
            } else if (e2LandAt != 0L || state != State.IDLE) {
                e2Start = 0L
                e2LandAt = 0L
            }
        }
        if (!sage && tfStart == 0L && state == State.GRABBED && !moved && !asleep) {
            bitmap = (if ((now / 200L) % 2L == 0L) fist1Bmp else fist2Bmp) ?: bitmap // angry fists while he is held
            frameSprite = true
        }
        if (!sage && state == State.FLYING && mood == Mood.SCARED) {
            bitmap = shockBmp ?: bitmap // shocked, arms out, while he flies
            frameSprite = true
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
        var sheet = false
        if (!sage && idle && !asleep && tfStart == 0L) {
            val cf = clipFrame(now)
            if (cf != null) {
                bitmap = cf
                sheet = true
            } else if (mood == Mood.HAPPY && !frameSprite && isFriday()) {
                // Friday outfit (SG0); the mouth moves with the words (SG3: 0 closed smile, 1 small o, 2 open smile)
                val typing = bubbleText != null && now < bubbleUntil && bubbleShown < (bubbleText?.length ?: 0)
                val id = if (typing) SG_TALK[(bubbleShown % 3)] else R.drawable.r_sg0
                tfBitmap(id)?.let {
                    bitmap = it
                    sheet = true
                }
            }
        }
        if (sheet) frameSprite = true
        if (bitmap !== shownBitmap) {
            fromBitmap = shownBitmap
            fromSheet = shownSheet
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
        shownSheet = sheet
        shownFrameSprite = frameSprite
        if (fade < 1f) fade = (fade + dt / fadeSeconds).coerceAtMost(1f)
    }

    /** Called by MainActivity on every touch anywhere in the app: restarts the sleep timer and wakes the dragon up. */
    private var lastOpinionAt = 0L

    // ---- R: clips cut from the owner's 2026-11-23 sheets (branding/tools/make_dragon_sheet_cells.py, drawables r_sa_* ... r_sg3_*).
    // Their frames share one scale and one floor line, so they are drawn like a costume: k = half * 2 / 384, bottom at +half.
    private class Clip(val ids: IntArray, val frameMs: Long, val holdLastMs: Long = 0L)

    private var clip: Clip? = null
    private var clipStart = 0L
    private var shownSheet = false // the sprite on screen is a sheet frame (drawn with the costume scale, not squeezed into a square)
    private var fromSheet = false
    private var lastGreetAt = 0L
    private var lastShownCheckAt = 0L
    private var lastStatsCheckAt = 0L

    private fun isFriday() = java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY && prefs.rMascotFriday().get()

    /** Plays [c] once, if the dragon is free (sitting still, awake, not in a transformation). */
    private fun playClip(c: Clip) {
        if (state != State.IDLE || asleep || tfStart != 0L || fallPhase != 0) return
        clip = c
        clipStart = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    private fun clipFrame(now: Long): Bitmap? {
        val c = clip ?: return null
        val t = now - clipStart
        val i = (t / c.frameMs).toInt()
        if (i >= c.ids.size && t >= c.ids.size * c.frameMs + c.holdLastMs) {
            clip = null
            return null
        }
        return tfBitmap(c.ids[i.coerceAtMost(c.ids.size - 1)])
    }

    // "key=epochDay;key=epochDay": each clip that is once a day / once a week remembers when it last played
    private fun clipDay(key: String): Long = prefs.rMascotClipDays().get().split(';')
        .firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.toLongOrNull() ?: -1L

    private fun setClipDay(key: String, day: Long) {
        val rest = prefs.rMascotClipDays().get().split(';').filter { it.isNotBlank() && !it.startsWith("$key=") }
        prefs.rMascotClipDays().set((rest + "$key=$day").joinToString(";"))
    }

    private fun today() = java.time.LocalDate.now().toEpochDay()

    /**
     * Called by MainActivity each time the dragon appears (library / manga page). Night yawn, Friday phrase, and the
     * streak / weekly goal clips (those two need the stats, read once on a background thread).
     */
    /** R: when the reading level went up since the last time we looked, play the egg evolution once. */
    private fun checkEvolution() {
        val level = eu.kanade.tachiyomi.util.RDragonLevel.info(eu.kanade.tachiyomi.util.RDragonLevel.totalXp(prefs)).level
        val seen = prefs.rDragonSeenLevel().get()
        prefs.rDragonSeenLevel().set(level)
        if (seen <= 0 || level <= seen || !prefs.rMascotEvolve().get()) return
        if (!isShown || !positioned || state != State.IDLE || asleep || tfStart != 0L) return
        tfEvo = level
        tfStart = SystemClock.uptimeMillis()
        tfSpoke = false
        tfPoofs = 0
        if (soundPool == null && prefs.rMascotSound().get()) soundPool = createSoundPool()
        invalidate()
    }

    fun onShown() {
        val now = SystemClock.uptimeMillis()
        if (!positioned || now - lastShownCheckAt < 60_000L) return
        lastShownCheckAt = now
        val day = today()
        if (prefs.rMascotNight().get() && java.time.LocalTime.now().hour in 0..4 && clipDay("night") != day) {
            setClipDay("night", day)
            postDelayed({ playClip(Clip(SD_YAWN, 420L, 900L)) }, 900L)
        }
        if (isFriday() && clipDay("friday") != day) {
            setClipDay("friday", day)
            postDelayed({ if (isShown && state == State.IDLE && !asleep) say(FRIDAY_SAY.random(), 4200L) }, 1500L)
        }
        checkEvolution() // XP also grows in the reader, so look at the saved level every time the dragon is shown
        if (now - lastStatsCheckAt > 30 * 60_000L) { // also keeps the dragon's level (size) up to date
            lastStatsCheckAt = now
            Thread {
                val d = runCatching { eu.kanade.tachiyomi.ui.more.stats.StatsPresenter().getRData() }.getOrNull() ?: return@Thread
                eu.kanade.tachiyomi.util.RDragonLevel.setTotalXp(prefs, eu.kanade.tachiyomi.util.RDragonLevel.xp(d.totalChapters, d.totalReadMs, eu.kanade.tachiyomi.util.RDragonLevel.bonusXp(prefs)))
                post { onStats(d.streakNow, d.weekChapters); checkEvolution() }
            }.start()
        }
    }

    private fun onStats(streak: Int, weekChapters: Int) {
        val day = today()
        val goal = prefs.rWeeklyGoal().get()
        if (prefs.rMascotGoalSpin().get() && goal > 0 && weekChapters > goal && clipDay("goal") != day / 7) {
            setClipDay("goal", day / 7)
            playClip(Clip(SC_SPIN + SC2_AFTER, 110L, 1200L)) // spin (SC) then dizzy and back (SC2)
            say(GOAL_SAY.random(), 3600L)
            return
        }
        val tier = when {
            streak >= 30 -> 2
            streak >= 7 -> 1
            streak >= 3 -> 0
            else -> -1
        }
        if (prefs.rMascotStreak().get() && tier >= 0 && clipDay("streak") != day) {
            setClipDay("streak", day)
            playClip(Clip(SF_ROWS[tier], 320L, 900L)) // row 1 = 3 days, row 2 = 7, row 3 = 30
            say(STREAK_SAY[tier], 3200L)
        }
    }

    // ---- R: the fall gag. 0 none, 1 falling, 2 dizzy on the floor, 3 climbing back up, 4 falling again, 5 dizzy on the floor (longer)
    private var fallPhase = 0
    private var fallVy = 0f
    private var fallUntil = 0L
    private var nextFallAt = 0L

    /**
     * R: the dragon gives a (random, joking) opinion when the owner opens a manga page. It looks at nothing: no
     * ratings, no lists, no websites; the owner asked for exactly that.
     */
    fun reactToManga() {
        val now = SystemClock.uptimeMillis()
        if (positioned && prefs.rMascotGreet().get() && !isFriday() && now - lastGreetAt > 30_000L) {
            lastGreetAt = now
            postDelayed({ playClip(Clip(SA_WAVE, 170L, 500L)) }, 500L) // SA greeting
        }
        if (!positioned || now - lastOpinionAt < 20_000L || Math.random() > 0.7) return
        lastOpinionAt = now
        postDelayed(
            {
                if (isShown && state == State.IDLE && !asleep && tfStart == 0L) {
                    val t = SystemClock.uptimeMillis()
                    if (Math.random() < 0.55) {
                        cheerUntil = t + 1800L
                        say(OPINION_GOOD.random(), 3200L)
                    } else {
                        sadUntil = t + 2200L
                        say(OPINION_BAD.random(), 3200L)
                    }
                }
            },
            1200L,
        )
    }

    fun noteActivity() {
        lastActiveAt = SystemClock.uptimeMillis()
        if (fallPhase != 0) { // any touch ends the gag; the dragon goes home by itself
            fallPhase = 0
            nextFallAt = lastActiveAt + 240_000L
        }
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
                val maxSpeed = 2300f * density / 2.75f // R: gentler throw (was 4500)
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
                    val doubleTap = now - lastTapAt < DOUBLE_TAP_MS
                    lastTapAt = now
                    if (prefs.rMascotTransform().get() && doubleTap) {
                        lastTapAt = 0L
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
                    vx = tvx * 0.6f
                    vy = tvy * 0.6f
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
            if (tfEvo > 0) {
                // egg evolution: seal -> smoke -> egg (appear, wobble, cracks, burst, shell) -> smoke -> new look
                val eggT = t - TF_SEAL - TF_WRAP - TF_CLOUD
                val eggTotal = EGG_STEPS.sumOf { it.second }
                if (tfPoofs == 0 && t >= TF_SEAL + TF_WRAP) { tfPoofs = 1; playPoof() }
                if (tfPoofs == 1 && eggT >= eggTotal) { tfPoofs = 2; playPoof() }
                val eid = when {
                    t < TF_SEAL -> R.drawable.r_tf_seal
                    t < TF_SEAL + TF_WRAP -> R.drawable.r_tf_wrap
                    eggT < 0 -> R.drawable.r_tf_cloud
                    eggT < eggTotal -> {
                        var acc = 0L
                        var frame = 1
                        for ((f, ms) in EGG_STEPS) { acc += ms; if (eggT < acc) { frame = f; break } }
                        if (frame >= 7 && !tfSpoke) {
                            tfSpoke = true
                            say("مبروك! وصلت للمستوى $tfEvo 🎉", 3200L)
                        }
                        EGG_FRAMES[frame - 1]
                    }
                    eggT < eggTotal + TF_CLOUD -> R.drawable.r_tf_cloud
                    else -> 0
                }
                if (eid == 0) {
                    tfStart = 0L
                    tfEvo = 0
                } else {
                    tfBmp = tfBitmap(eid)
                }
            } else {
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
        // R: the look the owner put on the dragon (costume or stage picture) replaces every normal frame
        var wornFit = false
        if (tfBmp == null && blanketT <= 0f) {
            val wid = eu.kanade.tachiyomi.util.RDragonLook.resId(context, prefs)
            if (wid != 0) {
                tfBmp = tfBitmap(wid)
                if (tfBmp != null) {
                    wornFit = eu.kanade.tachiyomi.util.RDragonLook.isStage(prefs.rDragonOutfit().get())
                    tfCostume = !wornFit
                }
            }
        }
        val current = tfBmp ?: shownBitmap ?: return
        val previous = if (tfBmp == null && fade < 1f && !fromSheet && !shownSheet) fromBitmap else null

        // R: a tall costume (hat, helmet) must not be cut by the top of the screen: slide the dragon down while it wears it
        var shiftTarget = 0f
        if (tfStart != 0L && tfEvo == 0) {
            val fb = tfBitmap(TF_FORMS[tfForm].first)
            if (fb != null) {
                val hr = (fb.height * (half * 2f / 384f) - half * 2f).coerceAtLeast(0f)
                val statusTop = androidx.core.view.ViewCompat.getRootWindowInsets(this)
                    ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())?.top ?: 0
                val topEdge = y - half - hr // where the top of the costume would be
                shiftTarget = (statusTop + 12f * density - topEdge).coerceAtLeast(0f)
            }
        }
        tfShift += (shiftTarget - tfShift) * 0.16f
        if (tfShift < 0.5f && shiftTarget == 0f) tfShift = 0f
        if (tfShift > 0f) postInvalidateOnAnimation()

        canvas.save()
        canvas.translate(x + shake, y + bob + tfShift)
        canvas.rotate(tiltSpring.value)
        if (stretch > 0.005f) {
            // squash and stretch: longer along the flight direction, thinner across it
            val deg = Math.toDegrees(atan2(vy, vx).toDouble()).toFloat()
            canvas.rotate(deg)
            canvas.scale(1f + stretch, 1f / (1f + stretch))
            canvas.rotate(-deg)
        }
        canvas.scale(scaleXSpring.value * (1f - breath * 0.5f), scaleYSpring.value * (1f + breath))
        if (tfBmp != null && wornFit) {
            // a stage picture: fit into the dragon's square, bottom on the same line
            val k = minOf(half * 2f / tfBmp.width, half * 2f / tfBmp.height)
            val w2 = tfBmp.width * k / 2f
            dest.set(-w2, half - tfBmp.height * k, w2, half)
        } else if (tfBmp != null && tfCostume) {
            // a costume has the dragon's own pixel scale and bottom line: exactly the normal size, tall hats reach higher
            val kk = half * 2f / 384f
            val w2 = tfBmp.width * kk / 2f
            val h = tfBmp.height * kk
            dest.set(-w2, half - h, w2, half)
            headroom = (h - half * 2f).coerceAtLeast(0f)
        } else if (tfBmp == null && shownSheet) {
            val kk = half * 2f / 384f
            val w2 = current.width * kk / 2f
            val h = current.height * kk
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
            fallPhase == 1 || fallPhase == 4 -> Mood.SCARED
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
        var top = y + tfShift - half - headroom - bh - 14f * density
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
        // clips from the 2026-11-23 sheets. Frame orders were read off the sheets (see branding/mascot-models/cutouts-2026-11-23)
        val SA_WAVE = intArrayOf(R.drawable.r_sa_0, R.drawable.r_sa_2, R.drawable.r_sa_3, R.drawable.r_sa_4, R.drawable.r_sa_5, R.drawable.r_sa_3, R.drawable.r_sa_2, R.drawable.r_sa_0)
        val SD_YAWN = intArrayOf(R.drawable.r_sd_0, R.drawable.r_sd_1, R.drawable.r_sd_2, R.drawable.r_sd_3, R.drawable.r_sd_4, R.drawable.r_sd_5)
        val SC_SPIN = intArrayOf(R.drawable.r_sc_0, R.drawable.r_sc_1, R.drawable.r_sc_2, R.drawable.r_sc_3, R.drawable.r_sc_4, R.drawable.r_sc_5, R.drawable.r_sc_6, R.drawable.r_sc_7)
        val SC2_AFTER = intArrayOf(R.drawable.r_sc2_0, R.drawable.r_sc2_1, R.drawable.r_sc2_2, R.drawable.r_sc2_3, R.drawable.r_sc2_4, R.drawable.r_sc2_5)
        val SF_ROWS = arrayOf(
            intArrayOf(R.drawable.r_sf_0, R.drawable.r_sf_1, R.drawable.r_sf_2, R.drawable.r_sf_3),
            intArrayOf(R.drawable.r_sf_4, R.drawable.r_sf_5, R.drawable.r_sf_6, R.drawable.r_sf_7),
            intArrayOf(R.drawable.r_sf_8, R.drawable.r_sf_9, R.drawable.r_sf_10, R.drawable.r_sf_11),
        )
        val SG_TALK = intArrayOf(R.drawable.r_sg3_0, R.drawable.r_sg3_2, R.drawable.r_sg3_1)

        // sentences are the owner's drafts, not approved yet
        val FRIDAY_SAY = listOf("ما تنساش تصلي على النبي ﷺ", "جمعة مباركة! صلّي على النبي ﷺ", "النهارده الجمعة.. سورة الكهف؟")
        val GOAL_SAY = listOf("عدّيت هدف الأسبوع! برافو عليك", "هدف الأسبوع خلص.. يلا الأسبوع الجاي")
        val STREAK_SAY = arrayOf("٣ أيام ورا بعض! كمّل", "أسبوع كامل من غير ما تفوّت يوم!", "٣٠ يوم ورا بعض.. إنت أسطورة")
        const val BLINK_MS = 140L
        val EGG_FRAMES = intArrayOf(
            R.drawable.r_egg_1, R.drawable.r_egg_2, R.drawable.r_egg_3, R.drawable.r_egg_4,
            R.drawable.r_egg_5, R.drawable.r_egg_6, R.drawable.r_egg_7, R.drawable.r_egg_8,
        )

        /** (egg frame 1..8, ms): appear, wobble, first crack, more cracks, light, burst, empty shell. */
        val EGG_STEPS = listOf(
            1 to 700L, 2 to 330L, 3 to 330L, 2 to 330L, 3 to 330L, 4 to 650L, 5 to 650L, 6 to 650L, 7 to 550L, 8 to 800L,
        )
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
        const val DOUBLE_TAP_MS = 700L // second tap within this = transformation
        const val CHAR_MS = 38L // typing speed of the speech bubble
        val GO_SLEEP = listOf("هنام شوية... صحّيني لما ترجع", "الدنيا هادية... هجيب اللحاف وأنام", "تصبح على خير، المانجا مش هتهرب")
        val WAKE = listOf("هاه؟ صحيت!", "كنت في أحلى حلم...", "خلاص خلاص، صاحي!")
        const val SLEEP_AFTER_MS = 60_000L
        const val HOME_AFTER_MS = 3500L // R: back to the home spot this long after the last touch
        val WAG_ORDER = intArrayOf(0, 1, 2, 3, 4, 3, 2, 1)
        const val WELCOME_MIN_GAP_MS = 2 * 60_000L // R: greet after 2 minutes away (was 3 h)
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val ENTER_MS = 800L
        val BLIP_RATES = floatArrayOf(1.0f, 1.18f, 0.9f, 1.32f, 1.08f)
        val GRAB = listOf(
            "سيبني بقى!", "إنت ماسكني ليه؟", "هششش! أنا التعبان!",
            "إنزلني! عندي مواعيد!", "إيدك دافية... بس سيبني", "أنا مش لعبة! أنا تنين!",
            "أنا حساس! بالراحة!", "ماسكني زي الكيس كده؟",
        )
        val THROW = listOf(
            "آآآآه!", "ارمي براحة!", "للانهاية وما بعدها!",
            "أنا بطير! شايف؟!", "قول لأمي إني بحبها!", "ثبّتوا الحزام!",
            "الطيران ببلاش؟ تمام!", "ابعتلي كارت بوستال!",
        )
        val WALL = listOf(
            "آي! الحيطة!", "كده هتكسر دماغي!", "بيوجع والله!",
            "الحيطة دي مين حطها هنا؟!", "آخ! ده كان تحت الحساب!", "هاخد إجازة مرضي",
            "الحيطة ما اتحركتش... يبقى أنا الغلطان!",
        )
        val CEILING = listOf("خد بالك من السقف!", "السقف ده واطي أوي!", "آي! نسيت إني بطير", "مين اللي حاطط السقف هنا؟!")
        val LAND = listOf(
            "كان ممتع! تاني!", "تاني تاني!", "أنا لسه بخير",
            "هبوط ناعم... تقريبًا", "يلا مرة كمان؟", "٩ من ١٠ من الحكام!",
            "هبطت بسلام... وكرامتي لأ.",
        )
        val DIZZY_TALK = listOf(
            "الدنيا بتلف بيا...", "أنا شايف نجوم!", "لفّلي دماغي!",
            "الأرض بتتحرك ولا أنا؟", "شايف ٣ مانجا بدل واحدة!", "استنى... أنا مين؟",
            "الدنيا بقت كاروسيل!",
        )
        val SAD_TALK = listOf(
            "حرام عليك...", "ده أنا زعلان!", "كده يا صاحبي؟",
            "ماشي... أنا هقعد لوحدي", "كنت فاكرك صاحبي...", "كنت عايز بس حضن...", "طيب... هاروح أقرا لوحدي",
        )
        val OPINION_GOOD = listOf(
            "المانهوا دي شكلها حلو، اقراها!", "غلاف جامد! ابدأ فيها.", "أنا حاسس إنها هتعجبك!",
            "من النوع اللي بتقول فيه «شابتر كمان بس».", "أنا موافق عليها... معلش، مقريتهاش.",
            "ابدأ فيها وقولي رأيك.", "شكلها هتسهرك. جرب!", "دي عليها القيمة!",
        )
        val OPINION_BAD = listOf(
            "مش عاجبتني المانهوا دي، بصراحة.", "الغلاف مش مريحني... بس إنت حر.",
            "حاسس إنها هتعمل لك دوشة في دماغك.", "ممكن تقرا غيرها؟ أنا بس بقترح.",
            "مش لونها! أنا بحب الأخضر.", "أنا مش واثق فيها، بس إنت حر.",
            "دي مش من النوع بتاعي... ولا أنا بفهم أصلاً.",
        )
        val FALL_START = listOf("آآآآآه!", "يا نهار!", "مسكوني!", "وااااه!")
        val FALL_HIT1 = listOf("آي! دماغي!", "أنا كويس... أنا كويس...", "كان لازم الأرض تكون بعيدة شوية!")
        val FALL_UP = listOf("هطلع تاني... وبخطة أحسن.", "مفيش حاجة حصلت، أوعد.", "هجرب تاني!")
        val FALL_HIT2 = listOf("ماشي، ده كفاية النهارده...", "الأرض بتحبني!", "هقعد هنا شوية... الدنيا بتلف.")
        val TAP = listOf(
            "أيوه؟", "عايز إيه؟", "بس بتدغدغني!",
            "هيهي! بتدغدغني!", "تاني؟ طيب!", "إيه ده؟ حد ضغط علي؟", "قول كلمة السر الأول!",
        )

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
