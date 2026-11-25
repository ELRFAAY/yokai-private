package eu.kanade.tachiyomi.ui.more

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

/**
 * R: the XP badge that evolves with the dragon level (shape + colour per tier, drawn in code from plain geometric shapes), and the
 * gold COIN (the spendable currency: 1 coin per 1000 XP, a shop comes later). Tiers: 1-2 grey circle, 3-4 green square, 5-6 blue
 * diamond, 7-8 purple hexagon, 9-10 red star, 11-12 GOLD star (the highest).
 */
object RXpBadge {
    const val CIRCLE = 0
    const val SQUARE = 1
    const val DIAMOND = 2
    const val HEXAGON = 3
    const val STAR = 4

    class Tier(val shape: Int, val color: Int)

    fun tier(level: Int): Tier = when {
        level <= 2 -> Tier(CIRCLE, 0xFF9AA3AD.toInt())
        level <= 4 -> Tier(SQUARE, 0xFF43A047.toInt())
        level <= 6 -> Tier(DIAMOND, 0xFF2F80ED.toInt())
        level <= 8 -> Tier(HEXAGON, 0xFF8E44AD.toInt())
        level <= 10 -> Tier(STAR, 0xFFE53935.toInt())
        else -> Tier(STAR, 0xFFFFB300.toInt())
    }

    /** The cut + recoloured diamond of the level (r_xpb_1..12, gold at 12); falls back to the code-drawn shape. */
    fun drawable(context: android.content.Context, level: Int): Drawable {
        val id = context.resources.getIdentifier("r_xpb_${level.coerceIn(1, 12)}", "drawable", context.packageName)
        return (if (id != 0) androidx.core.content.ContextCompat.getDrawable(context, id) else null) ?: BadgeDrawable(tier(level))
    }

    fun coin(context: android.content.Context): Drawable {
        val id = context.resources.getIdentifier("r_coin_gold", "drawable", context.packageName)
        return (if (id != 0) androidx.core.content.ContextCompat.getDrawable(context, id) else null) ?: CoinDrawable()
    }

    private fun lighten(c: Int, f: Float): Int {
        val r = ((c shr 16 and 0xFF) + (255 - (c shr 16 and 0xFF)) * f).toInt()
        val g = ((c shr 8 and 0xFF) + (255 - (c shr 8 and 0xFF)) * f).toInt()
        val b = ((c and 0xFF) + (255 - (c and 0xFF)) * f).toInt()
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    private fun darken(c: Int, f: Float): Int {
        val r = ((c shr 16 and 0xFF) * (1 - f)).toInt()
        val g = ((c shr 8 and 0xFF) * (1 - f)).toInt()
        val b = ((c and 0xFF) * (1 - f)).toInt()
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    private fun shapePath(shape: Int, cx: Float, cy: Float, r: Float): Path = Path().apply {
        when (shape) {
            CIRCLE -> addCircle(cx, cy, r, Path.Direction.CW)
            SQUARE -> addRoundRect(RectF(cx - r * 0.86f, cy - r * 0.86f, cx + r * 0.86f, cy + r * 0.86f), r * 0.16f, r * 0.16f, Path.Direction.CW)
            DIAMOND -> { moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close() }
            HEXAGON -> for (i in 0 until 6) {
                val a = Math.toRadians(60.0 * i - 90)
                val x = cx + r * cos(a).toFloat()
                val y = cy + r * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
                if (i == 5) close()
            }
            else -> for (i in 0 until 10) {
                val a = Math.toRadians(36.0 * i - 90)
                val rr = if (i % 2 == 0) r else r * 0.45f
                val x = cx + rr * cos(a).toFloat()
                val y = cy + rr * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
                if (i == 9) close()
            }
        }
    }

    private abstract class Base : Drawable() {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private class BadgeDrawable(val tier: Tier) : Base() {
        override fun draw(c: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val cx = bounds.left + w / 2
            val cy = bounds.top + h / 2 + (if (tier.shape == STAR) h * 0.03f else 0f)
            val r = minOf(w, h) / 2f * 0.94f
            val path = shapePath(tier.shape, cx, cy, r)
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(0f, cy - r, 0f, cy + r, lighten(tier.color, 0.45f), darken(tier.color, 0.18f), Shader.TileMode.CLAMP)
            c.drawPath(path, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.10f
            p.strokeJoin = Paint.Join.ROUND
            p.color = darken(tier.color, 0.40f)
            c.drawPath(path, p)
            // inner plate + "XP"
            p.style = Paint.Style.FILL
            p.color = darken(tier.color, 0.55f)
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textAlign = Paint.Align.CENTER
            p.textSize = r * (if (tier.shape == STAR) 0.62f else 0.78f)
            c.drawText("XP", cx, cy + p.textSize * 0.35f, p)
        }
    }

    private class CoinDrawable : Base() {
        override fun draw(c: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val cx = bounds.left + w / 2
            val cy = bounds.top + h / 2
            val r = minOf(w, h) / 2f * 0.94f
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(0f, cy - r, 0f, cy + r, 0xFFFFE680.toInt(), 0xFFE09A00.toInt(), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.12f
            p.color = 0xFFA86A00.toInt()
            c.drawCircle(cx, cy, r, p)
            p.strokeWidth = r * 0.07f
            p.color = 0x99A86A00.toInt()
            c.drawCircle(cx, cy, r * 0.72f, p)
            p.style = Paint.Style.FILL
            p.color = 0xFFA86A00.toInt()
            c.drawPath(shapePath(STAR, cx, cy + r * 0.02f, r * 0.5f), p)
        }
    }
}
