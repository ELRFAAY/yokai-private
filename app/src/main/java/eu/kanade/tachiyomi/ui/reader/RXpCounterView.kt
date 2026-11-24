package eu.kanade.tachiyomi.ui.reader

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.RXp
import java.util.Locale

/**
 * R: the XP counter in the TOP-LEFT of the reader. When a chapter gives XP the XP badge flies from the middle of the
 * screen into the counter, the number counts up and a retro coin sound plays. The badge spins by itself and follows a
 * finger drag. The counter hides itself a few seconds later.
 */
class RXpCounterView(private val activity: Activity) : LinearLayout(activity) {
    private val density = resources.displayMetrics.density
    private val frames = intArrayOf(
        R.drawable.r_xp_0, R.drawable.r_xp_1, R.drawable.r_xp_2, R.drawable.r_xp_3, R.drawable.r_xp_4, R.drawable.r_xp_5,
        R.drawable.r_xp_6, R.drawable.r_xp_7, R.drawable.r_xp_8, R.drawable.r_xp_9, R.drawable.r_xp_10,
    )
    private val icon = ImageView(activity)
    private val label = TextView(activity)
    private val handler = Handler(Looper.getMainLooper())
    private var frame = 0
    private var spinning = false
    private var lastX = 0f
    private var acc = 0f

    private val spin = object : Runnable {
        override fun run() {
            if (!spinning) return
            frame = (frame + 1) % frames.size
            icon.setImageResource(frames[frame])
            handler.postDelayed(this, 90L)
        }
    }

    private val hide = Runnable {
        animate().alpha(0f).setDuration(350L).withEndAction {
            visibility = GONE
            spinning = false
        }.start()
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = (6 * density).toInt()
        setPadding(pad, pad / 2, pad * 2, pad / 2)
        background = GradientDrawable().apply {
            cornerRadius = 40 * density
            setColor(0xCC10231A.toInt())
            setStroke((1.2f * density).toInt(), 0x884ADE80.toInt())
        }
        icon.setImageResource(frames[0])
        addView(icon, LayoutParams((34 * density).toInt(), (34 * density).toInt()))
        label.setTextColor(Color.WHITE)
        label.textSize = 15f
        label.setTypeface(label.typeface, android.graphics.Typeface.BOLD)
        label.textDirection = TEXT_DIRECTION_LTR
        label.setPadding((4 * density).toInt(), 0, 0, 0)
        addView(label)
        visibility = GONE
        alpha = 0f
        // the badge follows a finger drag sideways (a spin with the finger)
        icon.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.rawX
                    spinning = false
                    handler.removeCallbacks(hide)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    acc += e.rawX - lastX
                    lastX = e.rawX
                    val step = (14 * density)
                    while (acc >= step) { acc -= step; frame = (frame + 1) % frames.size }
                    while (acc <= -step) { acc += step; frame = (frame + frames.size - 1) % frames.size }
                    icon.setImageResource(frames[frame])
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    spinning = true
                    handler.post(spin)
                    handler.postDelayed(hide, 2500L)
                    true
                }
                else -> false
            }
        }
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val top = ViewCompat.getRootWindowInsets(content)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: (24 * density).toInt()
        content.addView(
            this,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = top + (56 * density).toInt()
                marginStart = (10 * density).toInt()
            },
        )
        elevation = 12 * density
    }

    private fun fmt(v: Long) = String.format(Locale.US, "%,d XP", v)

    /** [total] = xp after the gain. */
    fun showGain(total: Long, gain: Int) {
        handler.removeCallbacks(hide)
        label.text = fmt(total - gain)
        visibility = VISIBLE
        animate().alpha(1f).setDuration(200L).start()
        spinning = true
        handler.removeCallbacks(spin)
        handler.post(spin)
        // flying badge: from the middle of the screen into the counter
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        content.post {
            val fly = ImageView(activity).apply { setImageResource(frames[0]) }
            val size = (72 * density).toInt()
            content.addView(fly, FrameLayout.LayoutParams(size, size))
            val loc = IntArray(2)
            icon.getLocationInWindow(loc)
            val cloc = IntArray(2)
            content.getLocationInWindow(cloc)
            val tx = (loc[0] - cloc[0]).toFloat() + (icon.width - size) / 2f
            val ty = (loc[1] - cloc[1]).toFloat() + (icon.height - size) / 2f
            val sx = (content.width - size) / 2f
            val sy = (content.height - size) / 2f
            fly.x = sx
            fly.y = sy
            fly.alpha = 0f
            var f = 0
            val flip = object : Runnable {
                override fun run() {
                    if (fly.parent == null) return
                    f = (f + 1) % frames.size
                    fly.setImageResource(frames[f])
                    handler.postDelayed(this, 60L)
                }
            }
            handler.post(flip)
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 750L
                interpolator = AccelerateInterpolator(1.3f)
                addUpdateListener {
                    val p = it.animatedValue as Float
                    fly.alpha = (p * 4f).coerceAtMost(1f)
                    fly.x = sx + (tx - sx) * p
                    fly.y = sy + (ty - sy) * p
                    val s = 1.6f - 1.0f * p
                    fly.scaleX = s
                    fly.scaleY = s
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) {
                        content.removeView(fly)
                        RXp.playCoin()
                        icon.animate().scaleX(1.35f).scaleY(1.35f).setDuration(110L).withEndAction {
                            icon.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
                        }.start()
                        ValueAnimator.ofInt(0, gain).apply {
                            duration = 550L
                            addUpdateListener { v -> label.text = fmt(total - gain + (v.animatedValue as Int)) }
                            start()
                        }
                        handler.postDelayed(hide, 3200L)
                    }
                })
                start()
            }
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        spinning = false
        (parent as? ViewGroup)?.removeView(this)
    }
}
