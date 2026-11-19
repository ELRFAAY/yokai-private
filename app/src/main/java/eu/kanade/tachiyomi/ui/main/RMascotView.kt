package eu.kanade.tachiyomi.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.injectLazy
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
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

    /** Sleep: after a minute without any touch the snake says goodnight, a blanket slides up and it sleeps. */
    private enum class SleepPhase { AWAKE, DROWSY, ASLEEP, WAKING }

    private enum class Mood { HAPPY, HURT, SCARED, DIZZY, SAD, BLINK, BACK }

    private class Dot(val x: Float, val y: Float, var life: Float)

    private val density = resources.displayMetrics.density
    private val half = 42f * density // the snake is drawn 84dp wide
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

    /** The sleeping picture from the model sheet (snake snug under a red blanket). If it is missing, a blanket is drawn in code. */
    private val asleepBitmap: Bitmap? = load(R.drawable.r_mascot_asleep)
    private val asleepDest = RectF()

    /** The going-to-bed sequence from the bedtime model sheet: dragging the blanket, covering the tail, pulling it up, snuggling under. */
    private val bedFrames: List<Bitmap?> = listOf(
        R.drawable.r_bed_drag,
        R.drawable.r_bed_tail,
        R.drawable.r_bed_pull,
        R.drawable.r_bed_snug,
    ).map { load(it) }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val shadowRect = RectF()

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
        textSize = 14f * density
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
    private var angle = 0f
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

    private val rPrefs: PreferencesHelper by injectLazy()
    private var sleepPhase = SleepPhase.AWAKE
    private var lastActiveAt = SystemClock.uptimeMillis()
    private var drowsyUntil = 0L
    private var sleepStartedAt = 0L
    private var settleStart = 0L // when the snake last came to rest (drives the little landing bounce)
    private var blanketT = 0f // 0 = no blanket, 1 = blanket fully up

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

    private var running = false
    private var lastFrame = 0L

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            step()
            invalidate()
            postOnAnimation(this)
        }
    }

    // ---- life cycle: the animation loop only runs while the snake is actually on screen ----

    private fun startLoop() {
        if (running || !isAttachedToWindow || !isShown) return
        running = true
        lastFrame = SystemClock.uptimeMillis()
        // coming back to the screen: start awake, with a fresh timer
        sleepPhase = SleepPhase.AWAKE
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
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startLoop() else stopLoop()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) startLoop() else stopLoop()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        if (!positioned) {
            x = w - half - 16f * density
            y = maxY() - 90f * density
            positioned = true
        } else {
            x = x.coerceIn(half, max(half, w - half))
            y = y.coerceIn(minY(), maxY())
        }
    }

    private fun minY() = half + 24f * density
    private fun maxY() = max(minY(), height - half - 72f * density) // stays above the bottom bar

    // ---- physics ----

    private fun step() {
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastFrame) / 1000f).coerceIn(0.001f, 0.033f)
        lastFrame = now
        if (!positioned) return

        // sleep logic
        if (rPrefs.rMascotSleep().get()) {
            if (sleepPhase == SleepPhase.AWAKE && state == State.IDLE && now - lastActiveAt >= SLEEP_AFTER_MS) {
                sleepPhase = SleepPhase.DROWSY
                sleepStartedAt = now
                drowsyUntil = now + BED_STEP_MS * 4
                say(GO_SLEEP.random(), 3000L)
            }
        } else if (sleepPhase == SleepPhase.DROWSY || sleepPhase == SleepPhase.ASLEEP) {
            sleepPhase = SleepPhase.WAKING
        }
        if (sleepPhase == SleepPhase.DROWSY && now >= drowsyUntil) sleepPhase = SleepPhase.ASLEEP
        val blanketTarget = if (sleepPhase == SleepPhase.ASLEEP) 1f else 0f
        if (blanketT < blanketTarget) {
            blanketT = minOf(blanketTarget, blanketT + dt / 1.2f)
        } else if (blanketT > blanketTarget) {
            blanketT = maxOf(blanketTarget, blanketT - dt / 0.6f)
        }
        if (sleepPhase == SleepPhase.WAKING && blanketT <= 0f) sleepPhase = SleepPhase.AWAKE

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
            if (x < minX) {
                x = minX
                vx = abs(vx) * 0.8f
                hitWall = true
            } else if (x > maxX) {
                x = maxX
                vx = -abs(vx) * 0.8f
                hitWall = true
            }
            if (y < minY()) {
                y = minY()
                vy = abs(vy) * 0.8f
                hitCeiling = true
            } else if (y > maxY()) {
                y = maxY()
                vy = -abs(vy) * 0.8f
                hitWall = true
            }

            val speed = hypot(vx, vy)
            if ((hitWall || hitCeiling) && speed > 300f * density) {
                hurtUntil = now + 450
                bounces++
                say(if (hitCeiling) CEILING else WALL.random())
            }
            angle = (vx / (1500f * density)).coerceIn(-1f, 1f) * 25f

            if (hypot(x - lastDotX, y - lastDotY) >= 20f * density) {
                dots.add(Dot(x, y, 1f))
                lastDotX = x
                lastDotY = y
            }

            if (speed < 40f * density) {
                state = State.IDLE
                settleStart = now
                vx = 0f
                vy = 0f
                angle = 0f
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
                        say(LAND.random())
                    }
                }
                bounces = 0
            }
        }

        val iterator = dots.iterator()
        while (iterator.hasNext()) {
            val dot = iterator.next()
            dot.life -= dt * 1.2f
            if (dot.life <= 0f) iterator.remove()
        }
    }

    private fun say(text: String, durationMs: Long = 2200L) {
        bubbleText = text
        bubbleUntil = SystemClock.uptimeMillis() + durationMs
    }

    /** Called by MainActivity on every touch anywhere in the app: resets the sleep timer and wakes the snake up. */
    fun noteActivity() {
        lastActiveAt = SystemClock.uptimeMillis()
        if (sleepPhase == SleepPhase.DROWSY || sleepPhase == SleepPhase.ASLEEP) {
            sleepPhase = SleepPhase.WAKING
            happyUntil = lastActiveAt + 1500
            say(WAKE.random())
        }
    }

    // ---- touch: only the snake itself takes touches ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!positioned || hypot(event.x - x, event.y - y) > half * 1.1f) return false
                noteActivity()
                state = State.GRABBED
                grabDx = x - event.x
                grabDy = y - event.y
                downX = event.x
                downY = event.y
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
                    x = (event.x + grabDx).coerceIn(half, max(half, width - half))
                    y = (event.y + grabDy).coerceIn(minY(), maxY())
                    angle = 0f
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
                settleStart = now
                val speed = hypot(tvx, tvy)
                val maxSpeed = 4500f * density / 2.75f
                if (speed > maxSpeed) {
                    tvx *= maxSpeed / speed
                    tvy *= maxSpeed / speed
                }
                if (!moved) {
                    state = State.IDLE
                    happyUntil = now + 900
                    say(TAP.random())
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
                    say(LAND.random())
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

        val now = SystemClock.uptimeMillis()
        val idle = state == State.IDLE
        val sleepingNow = sleepPhase == SleepPhase.DROWSY || sleepPhase == SleepPhase.ASLEEP
        val floatOn = rPrefs.rMascotFloat().get()
        val hover = when {
            !idle -> 0f
            sleepingNow -> sin(now / 900f) * 2f * density
            // R: weightless hover: slow big drift up and down plus a faint quick wobble
            floatOn -> sin(now / 1150f) * 7f * density + sin(now / 430f) * 1.5f * density
            else -> sin(now / 380f) * 4f * density
        }
        // gravity-like landing: a small damped bounce right after it comes to rest
        val settleSeconds = (now - settleStart) / 1000f
        val settle = if (idle && floatOn && !sleepingNow && settleSeconds in 0f..2.5f) {
            -exp(-3f * settleSeconds) * cos(9f * settleSeconds) * 9f * density
        } else {
            0f
        }
        val bob = hover + settle
        val isHurt = now < hurtUntil
        val isHappy = idle && now < happyUntil
        val bmp = faces[currentMood(now, idle)] ?: faces[Mood.HAPPY] ?: return

        var scaleX = 1f
        var scaleY = 1f
        var shake = 0f
        var tilt = angle
        if (isHurt) shake = sin(now / 18f) * 4f * density
        if (isHappy) {
            val k = sin(now / 90f) * 0.06f
            scaleX = 1f + k
            scaleY = 1f - k
            tilt = sin(now / 120f) * 6f
        }
        if (state == State.GRABBED && moved) {
            scaleX = 1.1f
            scaleY = 1.1f
        }

        if (sleepingNow) { // slow breathing
            val k = sin(now / 900f) * 0.025f
            scaleX = 1f + k
            scaleY = 1f - k
        } else if (idle && floatOn && scaleX == 1f && scaleY == 1f) { // breathing while it floats
            val k = sin(now / 700f) * 0.03f
            scaleX = 1f + k
            scaleY = 1f - k
        }

        if (idle && floatOn && !sleepingNow && sleepPhase == SleepPhase.AWAKE) {
            // soft shadow on the "ground" under it: smaller and lighter when it is high
            val rise = (-bob / (9f * density)).coerceIn(-1f, 1f)
            val sw = half * (1.0f - 0.3f * rise)
            val sh = half * 0.16f * (1.0f - 0.3f * rise)
            shadowPaint.alpha = (55f - 22f * rise).toInt().coerceIn(0, 255)
            shadowRect.set(x - sw, y + half * 1.02f - sh, x + sw, y + half * 1.02f + sh)
            canvas.drawOval(shadowRect, shadowPaint)
        }

        canvas.save()
        canvas.translate(x + shake, y + bob)
        canvas.rotate(tilt)
        canvas.scale(scaleX, scaleY)
        dest.set(-half, -half, half, half)
        val asleepPic = asleepBitmap
        val framesOk = bedFrames.all { it != null }
        if (sleepPhase == SleepPhase.DROWSY && framesOk) {
            // going to bed: poses 1..4 one after the other, each one fading in over the previous picture
            val elapsed = (now - sleepStartedAt).coerceAtLeast(0L)
            val index = (elapsed / BED_STEP_MS).toInt().coerceIn(0, bedFrames.size - 1)
            val progress = ((elapsed - index * BED_STEP_MS).toFloat() / BED_STEP_MS).coerceIn(0f, 1f)
            val fadeIn = (progress / 0.2f).coerceIn(0f, 1f)
            if (index == 0) {
                drawFace(canvas, bmp, ((1f - fadeIn) * 255f).toInt())
            } else {
                drawFit(canvas, bedFrames[index - 1]!!, 255)
            }
            drawFit(canvas, bedFrames[index]!!, (fadeIn * 255f).toInt())
        } else if (asleepPic != null && blanketT > 0f) {
            // cross-fade between the "snuggling" pose (or the face, when waking up) and the sleeping picture
            val before = if (sleepPhase == SleepPhase.ASLEEP && framesOk) bedFrames.last() else null
            if (before != null) {
                drawFit(canvas, before, ((1f - blanketT) * 255f).toInt())
            } else {
                drawFace(canvas, bmp, ((1f - blanketT) * 255f).toInt())
            }
            drawFit(canvas, asleepPic, (blanketT * 255f).toInt())
        } else {
            canvas.drawBitmap(bmp, null, dest, bitmapPaint)
            if (blanketT > 0f) drawBlanket(canvas)
        }
        canvas.restore()

        if (sleepPhase == SleepPhase.ASLEEP && blanketT > 0.85f) drawZzz(canvas, now)
        drawBubble(canvas, now)
    }

    /** Which of the six pictures to show right now. */
    private fun currentMood(now: Long, idle: Boolean): Mood = when {
        sleepPhase == SleepPhase.DROWSY || sleepPhase == SleepPhase.ASLEEP -> Mood.BLINK // eyes closed
        now < hurtUntil -> Mood.HURT
        state == State.GRABBED && moved -> Mood.SCARED
        state == State.FLYING -> when {
            bounces >= 3 -> Mood.DIZZY
            hypot(vx, vy) > 700f * density -> Mood.BACK // flying away fast: we see its back
            else -> Mood.SCARED
        }
        idle && now < dizzyUntil -> Mood.DIZZY
        idle && now < sadUntil -> Mood.SAD
        idle && now >= happyUntil && now % 3600L < 160L -> Mood.BLINK
        else -> Mood.HAPPY
    }

    private fun drawFace(canvas: Canvas, face: Bitmap, alpha: Int) {
        bitmapPaint.alpha = alpha.coerceIn(0, 255)
        canvas.drawBitmap(face, null, dest, bitmapPaint)
        bitmapPaint.alpha = 255
    }

    /** Draws a sleeping/bed picture at a common pixel scale, centered, with its bottom on a fixed "ground" line. */
    private fun drawFit(canvas: Canvas, pic: Bitmap, alpha: Int) {
        val w = pic.width * half * 0.0059f
        val h = pic.height * half * 0.0059f
        asleepDest.set(-w / 2f, half * 0.95f - h, w / 2f, half * 0.95f)
        bitmapPaint.alpha = alpha.coerceIn(0, 255)
        canvas.drawBitmap(pic, null, asleepDest, bitmapPaint)
        bitmapPaint.alpha = 255
    }

    /** The blanket is drawn in code: a teal quilt with diagonal stripes that slides up over the lower half of the snake. */
    private fun drawBlanket(canvas: Canvas) {
        val t = blanketT.coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)
        val slide = (1f - eased) * half * 1.4f
        blanketRect.set(-half * 1.08f, half * 0.05f + slide, half * 1.08f, half * 1.12f + slide)
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

    /** Three small "z" letters that drift up from the sleeping snake and fade out. */
    private fun drawZzz(canvas: Canvas, now: Long) {
        for (i in 0 until 3) {
            val p = (((now / 1000f) + i * 0.9f) % 2.7f) / 2.7f
            zPaint.alpha = ((1f - p) * 255f).toInt().coerceIn(0, 255)
            zPaint.textSize = (12f + 8f * p + i * 2f) * density
            canvas.drawText("z", x + half * 0.35f + p * half * 0.5f + i * 6f * density, y - half * 0.55f - p * half * 0.9f, zPaint)
        }
    }

    private fun drawBubble(canvas: Canvas, now: Long) {
        val text = bubbleText ?: return
        if (now > bubbleUntil) {
            bubbleText = null
            return
        }
        val pad = 10f * density
        val fm = textPaint.fontMetrics
        val bw = textPaint.measureText(text) + pad * 2f
        val bh = fm.descent - fm.ascent + pad * 1.4f
        val margin = 8f * density

        val left = (x - bw / 2f).coerceIn(margin, max(margin, width - bw - margin))
        var top = y - half - bh - 14f * density
        val below = top < 4f * density
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

        canvas.drawText(text, bubbleRect.centerX(), bubbleRect.top + pad * 0.7f - fm.ascent, textPaint)
    }

    private companion object {
        val GRAB = listOf("سيبني بقى!", "إنت ماسكني ليه؟", "هششش! أنا التعبان!")
        val THROW = listOf("آآآآه!", "ارمي براحة!", "للانهاية وما بعدها!")
        val WALL = listOf("آي! الحيطة!", "كده هتكسر دماغي!", "بيوجع والله!")
        const val CEILING = "خد بالك من السقف!"
        val LAND = listOf("كان ممتع! تاني!", "تاني تاني!", "أنا لسه بخير")
        val DIZZY_TALK = listOf("الدنيا بتلف بيا...", "أنا شايف نجوم!", "لفّلي دماغي!")
        val SAD_TALK = listOf("حرام عليك...", "ده أنا زعلان!", "كده يا صاحبي؟")
        val TAP = listOf("أيوه؟", "عايز إيه؟", "بس بتدغدغني!")
        val GO_SLEEP = listOf("لما تبقى جاهز صحّيني... هنام شوية", "الدنيا هادية... هجيب البطانية وأنام", "صحّيني لما ترجع، تمام؟")
        val WAKE = listOf("هاه؟ صحيت!", "إيه ده؟ كنت في أحلى حلم...", "خلاص خلاص، صاحي!")
        const val SLEEP_AFTER_MS = 60_000L
        const val BED_STEP_MS = 1300L // one bed pose every 1.3 s
    }
}
