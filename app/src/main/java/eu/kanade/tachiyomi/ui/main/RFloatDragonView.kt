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
import android.view.View
import eu.kanade.tachiyomi.R
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * R: the small dragon of the chapter-end card and of the statistics.
 * It hovers like the little spirit in the owner's video: slow bob, a soft tilt, a glowing shadow that breathes with the bob
 * and, every few seconds, a short glide to the side that leaves a trail of fading sparkles.
 * With [pointing] it uses the pointing pose and pokes toward its text ([pointRight] = the text is on the right of the dragon).
 * With [floating] = false (switch off in Settings > R) it is the plain still picture again.
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

    private val density = resources.displayMetrics.density
    private val slack get() = (14 * density).toInt()
    private val still: Bitmap? by lazy { load(RES_STILL) }
    private val point: Bitmap? by lazy { load(RES_POINT) }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GLOW }
    private val dst = RectF()

    private class Dot(val x: Float, val y: Float, val born: Long)
    private val dots = ArrayList<Dot>()
    private var lastDotAt = 0L
    private var running = false
    private val animationsOn: Boolean
        get() = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
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
        postOnAnimation(tick)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = (if (pointing) point else null) ?: still ?: return
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

        // glide to the side every ~7 s (0.9 s out and back) leaving sparkles
        var dx = 0f
        var moving = false
        if (animate) {
            val phase = (now % GLIDE_EVERY_MS).toFloat()
            if (phase < GLIDE_MS) {
                val p = phase / GLIDE_MS
                dx = sin(p * PI.toFloat()) * 12f * density * (if ((now / GLIDE_EVERY_MS) % 2L == 0L) 1f else -1f)
                moving = true
            }
        }

        // poke toward the text while pointing
        var poke = 0f
        if (animate && pointing) {
            val p = max(0f, sin(sec * 2f * PI.toFloat() / 2.4f))
            poke = p * p * p
        }
        val dir = if (pointRight) 1f else -1f

        val cx = width / 2f
        val baseY = height - slack.toFloat() * 0.55f

        // glowing shadow on the "ground": breathes with the bob
        val shadowW = side * 0.5f * (1f - 0.12f * bob)
        val shadowH = side * 0.07f
        shadowPaint.shader = RadialGradient(0f, 0f, 1f, GLOW_SOFT, 0x0022C55E, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.translate(cx + dx * 0.4f, baseY)
        canvas.scale(shadowW, shadowH)
        canvas.drawCircle(0f, 0f, 1f, shadowPaint)
        canvas.restore()

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
            canvas.drawCircle(d.x, d.y + age * 8f * density, (2.6f - age * 1.4f) * density, dotPaint)
        }

        // the dragon itself, bottom-center anchored so it squashes from the ground
        canvas.save()
        canvas.translate(cx + dx + dir * poke * 5f * density, baseY + dy)
        canvas.rotate(tilt + dir * poke * 4f)
        canvas.scale(sx, sy)
        if (pointing && !pointRight) canvas.scale(-1f, 1f)
        dst.set(-side / 2f, -side, side / 2f, 0f)
        canvas.drawBitmap(bmp, null, dst, bitmapPaint)
        canvas.restore()
    }

    companion object {
        private const val GLIDE_EVERY_MS = 7000L
        private const val GLIDE_MS = 900f
        private const val DOT_LIFE_MS = 800L
        private const val GLOW = 0xFF4ADE80.toInt()
        private const val GLOW_SOFT = 0x6622C55E
        private val RES_STILL = R.drawable.r_mascot_happy
        private val RES_POINT = R.drawable.r_mascot_point
    }
}
