package eu.kanade.tachiyomi.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import eu.kanade.tachiyomi.R
import kotlin.math.abs
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
 * - Reactions: it wobbles when you tap it, turns red and shakes when it hits a wall, and talks in speech bubbles.
 *   (There is only one picture of the snake, so the "faces" are tint / squash / tilt for now.)
 *
 * The phrases are in [GRAB], [THROW], [WALL], [CEILING], [LAND] and [TAP] below: edit them freely.
 */
class RMascotView(context: Context) : View(context) {

    private enum class State { IDLE, GRABBED, FLYING }

    private class Dot(val x: Float, val y: Float, var life: Float)

    private val density = resources.displayMetrics.density
    private val half = 42f * density // the snake is drawn 84dp wide
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val bitmap: Bitmap? = try {
        BitmapFactory.decodeResource(resources, R.drawable.r_mascot)
    } catch (e: Throwable) {
        null
    }

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
    private val hurtFilter = PorterDuffColorFilter(0x66FF0000, PorterDuff.Mode.SRC_ATOP)
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
    private var bubbleText: String? = null
    private var bubbleUntil = 0L

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
                vx = 0f
                vy = 0f
                angle = 0f
                happyUntil = now + 1200
                say(LAND.random())
            }
        }

        val iterator = dots.iterator()
        while (iterator.hasNext()) {
            val dot = iterator.next()
            dot.life -= dt * 1.2f
            if (dot.life <= 0f) iterator.remove()
        }
    }

    private fun say(text: String) {
        bubbleText = text
        bubbleUntil = SystemClock.uptimeMillis() + 2200
    }

    // ---- touch: only the snake itself takes touches ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!positioned || hypot(event.x - x, event.y - y) > half * 1.1f) return false
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
        val bmp = bitmap ?: return
        if (!positioned) return

        for (dot in dots) {
            dotPaint.alpha = (dot.life.coerceIn(0f, 1f) * 200f).toInt()
            canvas.drawCircle(dot.x, dot.y, (3f + 3f * dot.life) * density, dotPaint)
        }

        val now = SystemClock.uptimeMillis()
        val idle = state == State.IDLE
        val bob = if (idle) sin(now / 380f) * 4f * density else 0f
        val isHurt = now < hurtUntil
        val isHappy = idle && now < happyUntil

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

        bitmapPaint.colorFilter = if (isHurt) hurtFilter else null
        canvas.save()
        canvas.translate(x + shake, y + bob)
        canvas.rotate(tilt)
        canvas.scale(scaleX, scaleY)
        dest.set(-half, -half, half, half)
        canvas.drawBitmap(bmp, null, dest, bitmapPaint)
        canvas.restore()

        drawBubble(canvas, now)
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
        val TAP = listOf("أيوه؟", "عايز إيه؟", "بس بتدغدغني!")
    }
}
