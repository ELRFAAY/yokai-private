package eu.kanade.tachiyomi.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * R: the small dragon of the chapter-end card and of the statistics.
 * Idle: it hovers like the little spirit in the owner's video (slow bob, soft tilt, breathing glow shadow, two glowing
 * orbs around it, a short glide with a sparkle trail every few seconds).
 * Touch: you can grab it and THROW it, like the home dragon: it flies over the screen (spinning, scared face, sparkle
 * trail), bounces off the screen edges, gets dizzy for a moment and glides back to its place.
 * With [pointing] it uses the pointing pose and pokes toward its text ([pointRight] = the text is on the right of the dragon).
 * With [floating] = false (switch off in Settings > R) it is the plain still picture again and cannot be touched.
 */
class RFloatDragonView(context: Context) : View(context) {

    var sizeDp = 64
        set(value) {
            field = value
            requestLayout()
        }
    var floating = true
        set(value) {
            field = value
            invalidate()
        }
    var pointing = false
        set(value) {
            field = value
            invalidate()
        }
    var pointRight = true

    /** R: hammer mode (reader toggle): every few seconds a hammer falls on its head, it squashes and shows the hurt face. */
    var hammer = false
        set(value) {
            field = value
            hitAt = 0L
            invalidate()
        }
    private var hitAt = 0L
    private var nextHitAt = 0L
    private val hurt: Bitmap? by lazy { load(R.drawable.r_mascot_hurt) }
    private val hammerUp: Bitmap? by lazy { load(R.drawable.r_hammer_up) }
    private val hammerRaised: Bitmap? by lazy { load(R.drawable.r_hammer_raised) }

    private val density = resources.displayMetrics.density
    private val slack get() = (14 * density).toInt()
    private val still: Bitmap? by lazy { load(R.drawable.r_mascot_happy) }
    private val point: Bitmap? by lazy { load(R.drawable.r_mascot_point) }
    private val scared: Bitmap? by lazy { load(R.drawable.r_mascot_scared) }
    private val dizzy: Bitmap? by lazy { load(R.drawable.r_mascot_dizzy) }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GLOW }
    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dst = RectF()

    /** Sparkle in "home" coordinates (the view's own spot without the throw offset), so it stays where it was left. */
    private class Dot(val x: Float, val y: Float, val born: Long)
    private val dots = ArrayList<Dot>()
    private var lastDotAt = 0L
    private var running = false

    private enum class Mode { HOME, DRAG, FLY, DIZZY, RETURN }
    private var mode = Mode.HOME
    private var offX = 0f
    private var offY = 0f
    private var vx = 0f
    private var vy = 0f
    private var spin = 0f
    private var lastFrame = 0L
    private var dizzyUntil = 0L
    private var downRawX = 0f
    private var downRawY = 0f
    private var offStartX = 0f
    private var offStartY = 0f
    private var lastMoveAt = 0L
    private var estVx = 0f
    private var estVy = 0f
    private var moved = false
    private var minX = 0f
    private var maxX = 0f
    private var minY = 0f
    private var maxY = 0f
    private val unclipped = ArrayList<Triple<ViewGroup, Boolean, Boolean>>()

    private val animationsOn: Boolean
        get() = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            step()
            invalidate()
            postOnAnimation(this)
        }
    }

    private fun load(id: Int): Bitmap? = runCatching { BitmapFactory.decodeResource(resources, id) }.getOrNull()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val side = (sizeDp * density).toInt()
        setMeasuredDimension(side + 2 * slack, side + 2 * slack)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        lastFrame = SystemClock.uptimeMillis()
        postOnAnimation(tick)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(tick)
        restoreAncestors()
        super.onDetachedFromWindow()
    }

    // ---- throw physics (same feel as the home dragon: loses speed fast, a glide and not a ball) ----

    private fun step() {
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastFrame) / 1000f).coerceIn(0f, 0.05f)
        lastFrame = now
        when (mode) {
            Mode.FLY -> {
                // straight flight in the throw direction at a calm constant speed until it hits the screen edge
                offX += vx * dt
                offY += vy * dt
                var hit = false
                if (offX < minX) { offX = minX; hit = true }
                if (offX > maxX) { offX = maxX; hit = true }
                if (offY < minY) { offY = minY; hit = true }
                if (offY > maxY) { offY = maxY; hit = true }
                spin += (vx / density) * 0.12f * dt
                if (now - lastDotAt > 30L) {
                    lastDotAt = now
                    dots.add(Dot(offX + width / 2f, offY + height * 0.55f, now))
                }
                if (hit) {
                    // the bump: a short rebound, then dizzy
                    vx *= -0.18f
                    vy *= -0.18f
                    mode = Mode.DIZZY
                    dizzyUntil = now + 900L
                }
            }
            Mode.DIZZY -> {
                offX = (offX + vx * dt).coerceIn(minX, maxX)
                offY = (offY + vy * dt).coerceIn(minY, maxY)
                val rd = 0.002f.pow(dt)
                vx *= rd
                vy *= rd
                spin += (-spin) * (1f - exp(-5f * dt)) // untwist
                if (now >= dizzyUntil) mode = Mode.RETURN
            }
            Mode.RETURN -> {
                val k = 1f - exp(-4f * dt) // smooth glide home
                spin += (-spin) * (1f - exp(-8f * dt))
                if (now - lastDotAt > 45L) {
                    lastDotAt = now
                    dots.add(Dot(offX + width / 2f, offY + height * 0.55f, now))
                }
                offX += -offX * k
                offY += -offY * k
                if (abs(offX) < 1f && abs(offY) < 1f) {
                    offX = 0f
                    offY = 0f
                    spin = 0f
                    mode = Mode.HOME
                    restoreAncestors()
                }
            }
            else -> {}
        }
        translationX = offX
        translationY = offY
    }

    private fun unclipAncestors() {
        if (unclipped.isNotEmpty()) return
        var p = parent
        while (p is ViewGroup) {
            unclipped.add(Triple(p, p.clipChildren, p.clipToPadding))
            p.clipChildren = false
            p.clipToPadding = false
            p = p.parent
        }
    }

    private fun restoreAncestors() {
        unclipped.forEach { (g, c, pad) ->
            g.clipChildren = c
            g.clipToPadding = pad
        }
        unclipped.clear()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!floating || !animationsOn) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                unclipAncestors()
                val loc = IntArray(2)
                getLocationOnScreen(loc)
                val homeLeft = loc[0] - translationX
                val homeTop = loc[1] - translationY
                val dm = resources.displayMetrics
                minX = -homeLeft
                maxX = dm.widthPixels - homeLeft - width
                minY = -homeTop
                maxY = dm.heightPixels - homeTop - height
                downRawX = event.rawX
                downRawY = event.rawY
                offStartX = offX
                offStartY = offY
                estVx = 0f
                estVy = 0f
                lastMoveAt = SystemClock.uptimeMillis()
                moved = false
                mode = Mode.DRAG
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode != Mode.DRAG) return true
                val nx = (offStartX + event.rawX - downRawX).coerceIn(minX, maxX)
                val ny = (offStartY + event.rawY - downRawY).coerceIn(minY, maxY)
                val now = SystemClock.uptimeMillis()
                val dt = max(1L, now - lastMoveAt) / 1000f
                estVx = estVx * 0.5f + ((nx - offX) / dt) * 0.5f
                estVy = estVy * 0.5f + ((ny - offY) / dt) * 0.5f
                lastMoveAt = now
                offX = nx
                offY = ny
                if (abs(event.rawX - downRawX) + abs(event.rawY - downRawY) > 8f * density) moved = true
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode != Mode.DRAG) return true
                // direction = where it was thrown (release speed), else where it was dragged to, else up
                var dx = 0f
                var dy = -1f
                val sp = sqrt(estVx * estVx + estVy * estVy)
                if (moved && sp > 150f * density) {
                    dx = estVx / sp
                    dy = estVy / sp
                } else if (moved && sqrt(offX * offX + offY * offY) > 12f * density) {
                    val l = sqrt(offX * offX + offY * offY)
                    dx = offX / l
                    dy = offY / l
                }
                // the calm speed; thrown UP it is quicker
                val speed = (if (dy < -0.5f) 1700f else 1000f) * density
                vx = dx * speed
                vy = dy * speed
                mode = Mode.FLY
                lastFrame = SystemClock.uptimeMillis()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // ---- drawing ----

    override fun onDraw(canvas: Canvas) {
        val idle = mode == Mode.HOME
        // hammer schedule: first hit 1.2 s after it shows, then every 4.5 s (only while at home)
        val nowMs = SystemClock.uptimeMillis()
        if (hammer && floating && animationsOn && idle) {
            if (nextHitAt == 0L) nextHitAt = nowMs + 1200L
            if (hitAt == 0L && nowMs >= nextHitAt) {
                hitAt = nowMs
                unclipAncestors()
            }
        }
        val hitT = if (hitAt != 0L) nowMs - hitAt else -1L
        if (hitAt != 0L && hitT > HIT_MS) {
            hitAt = 0L
            nextHitAt = nowMs + HIT_EVERY_MS
            restoreAncestors()
        }
        val hurtNow = hitT in HIT_BONK_MS..(HIT_MS - 150L)
        val bmp = when {
            mode == Mode.DRAG || mode == Mode.FLY -> scared
            mode == Mode.DIZZY -> dizzy
            hurtNow -> hurt
            pointing -> point
            else -> null
        } ?: still ?: return
        val side = sizeDp * density
        val animate = floating && animationsOn
        val now = SystemClock.uptimeMillis()
        val sec = now / 1000f

        // hover: slow bob, soft tilt, a hint of squash
        val bob = if (animate) sin(sec * 2f * PI.toFloat() / 2.6f) else 0f
        val tilt = if (animate) sin(sec * 2f * PI.toFloat() / 3.4f + 1f) * 3f else 0f
        val sy = 1f + 0.025f * bob
        val sx = 1f - 0.015f * bob
        val lift = if (animate) 6f * density else 0f
        val dy = -lift - bob * 4f * density

        // glide to the side every ~7 s (0.9 s out and back) leaving sparkles (only while idle at home)
        var dx = 0f
        var moving = false
        if (animate && idle) {
            val phase = (now % GLIDE_EVERY_MS).toFloat()
            if (phase < GLIDE_MS) {
                val p = phase / GLIDE_MS
                dx = sin(p * PI.toFloat()) * 12f * density * (if ((now / GLIDE_EVERY_MS) % 2L == 0L) 1f else -1f)
                moving = true
            }
        }

        // poke toward the text while pointing
        var poke = 0f
        if (animate && pointing && idle) {
            val p = max(0f, sin(sec * 2f * PI.toFloat() / 2.4f))
            poke = p * p * p
        }
        val dir = if (pointRight) 1f else -1f

        val cx = width / 2f
        val baseY = height - slack.toFloat() * 0.55f

        // glowing shadow on the "ground": breathes with the bob (only at home)
        if (idle) {
            val shadowW = side * 0.5f * (1f - 0.12f * bob)
            val shadowH = side * 0.07f
            shadowPaint.shader = RadialGradient(0f, 0f, 1f, GLOW_SOFT, 0x0022C55E, Shader.TileMode.CLAMP)
            canvas.save()
            canvas.translate(cx + dx * 0.4f, baseY)
            canvas.scale(shadowW, shadowH)
            canvas.drawCircle(0f, 0f, 1f, shadowPaint)
            canvas.restore()
        }

        // sparkle trail
        if (moving && now - lastDotAt > 45L) {
            lastDotAt = now
            dots.add(Dot(cx + dx, baseY + dy - side * 0.25f + (Math.random().toFloat() - 0.5f) * side * 0.3f, now))
        }
        val iter = dots.iterator()
        while (iter.hasNext()) {
            val d = iter.next()
            val age = (now - d.born) / DOT_LIFE_MS.toFloat()
            if (age >= 1f) {
                iter.remove()
                continue
            }
            dotPaint.alpha = ((1f - age) * 200).toInt()
            canvas.drawCircle(d.x - offX, d.y - offY + age * 8f * density, (2.6f - age * 1.4f) * density, dotPaint)
        }

        // two glowing orbs that circle the dragon (like the spirit in the video)
        if (animate) {
            val a = sec * 2f * PI.toFloat() / 2.2f
            for (i in 0..1) {
                val ang = a + i * PI.toFloat()
                val ox = cx + dx + cos(ang) * side * 0.42f
                val oy = baseY + dy - side * 0.4f + sin(ang) * side * 0.1f
                val front = sin(ang) > 0f
                orbPaint.color = GLOW
                orbPaint.alpha = if (front) 235 else 120
                canvas.drawCircle(ox, oy, (if (front) 2.8f else 2f) * density, orbPaint)
            }
        }

        // the dragon itself, bottom-center anchored so it squashes from the ground
        canvas.save()
        canvas.translate(cx + dx + dir * poke * 5f * density, baseY + dy)
        canvas.rotate(tilt + dir * poke * 4f + spin)
        // squash under the hammer
        val squash = if (hitT in HIT_BONK_MS..(HIT_BONK_MS + 260L)) 1f - 0.16f * (1f - (hitT - HIT_BONK_MS) / 260f) else 1f
        canvas.scale(sx * (2f - squash).coerceAtMost(1.1f), sy * squash)
        if (pointing && !pointRight && bmp === point) canvas.scale(-1f, 1f)
        dst.set(-side / 2f, -side, side / 2f, 0f)
        canvas.drawBitmap(bmp, null, dst, bitmapPaint)
        canvas.restore()

        // the hammer: hovers tilted above, drops fast onto the head, rests a moment, then lifts away
        if (hitT in 0..HIT_MS) {
            val hs = side * 0.62f
            val dragonTop = baseY + dy - side * sy
            val impactTop = dragonTop + 0.12f * side - 0.45f * hs
            val farTop = impactTop - 0.7f * side
            val t = hitT
            val top: Float
            var alpha = 255
            val img: Bitmap?
            when {
                t < 350L -> {
                    val p = t / 350f
                    top = farTop + sin(sec * 18f) * 2f * density
                    alpha = (255 * p).toInt()
                    img = hammerRaised
                }
                t < HIT_BONK_MS -> {
                    val p = (t - 350L) / (HIT_BONK_MS - 350L).toFloat()
                    top = farTop + (impactTop - farTop) * p * p
                    img = hammerUp
                }
                t < 1000L -> {
                    top = impactTop
                    img = hammerUp
                }
                else -> {
                    val p = (t - 1000L) / (HIT_MS - 1000L).toFloat()
                    top = impactTop + (farTop - impactTop) * p
                    alpha = (255 * (1f - p)).toInt()
                    img = hammerRaised
                }
            }
            img?.let {
                bitmapPaint.alpha = alpha.coerceIn(0, 255)
                dst.set(cx + dx - hs / 2f, top, cx + dx + hs / 2f, top + hs)
                canvas.drawBitmap(it, null, dst, bitmapPaint)
                bitmapPaint.alpha = 255
            }
        }
    }

    companion object {
        private const val GLIDE_EVERY_MS = 7000L
        private const val HIT_MS = 1500L
        private const val HIT_BONK_MS = 480L
        private const val HIT_EVERY_MS = 4500L
        private const val GLIDE_MS = 900f
        private const val DOT_LIFE_MS = 800L
        private const val GLOW = 0xFF4ADE80.toInt()
        private const val GLOW_SOFT = 0x6622C55E
    }
}
