package eu.kanade.tachiyomi.ui.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import eu.kanade.tachiyomi.R
import java.util.Calendar
import kotlin.math.ceil
import kotlin.random.Random

/**
 * R: the green cobra that peeks in from the left edge of the screen while you read
 * (Settings > R > "Reader cobra").
 *
 * - Full-screen transparent overlay in the reader. It only takes a touch that lands ON the cobra
 *   (tap = dismiss); every other touch goes through to the reader (onTouchEvent returns false).
 * - A first visit comes after about 45 s of reading, then one every few minutes (interval is a setting,
 *   randomized by +-20 %). It never shows while the reader menu is open, and it leaves at once if you open it.
 * - It slides in with a movement sound, says one sentence in Egyptian Arabic in a speech bubble that is
 *   typed letter by letter with a typing sound, waits, and slides out with a second movement sound.
 * - Late at night (00:00 - 04:59 device time, switchable) it comes sleepy and tells you to go to sleep.
 * - Sounds are tiny generated WAV files in res/raw, played with a SoundPool on the media volume (low).
 *
 * The phrases are in [NORMAL] and [NIGHT] at the bottom of this file: edit them freely.
 */
class RReaderCobraView(
    context: Context,
    private val enabled: () -> Boolean,
    private val canShow: () -> Boolean,
    private val soundOn: () -> Boolean,
    private val nightOn: () -> Boolean,
    private val intervalMinutes: () -> Int,
    private val sceneOn: () -> Boolean = { false },
    private val sceneEvery: () -> Int = { 3 },
    private val sizeDp: () -> Int = { 72 },
) : View(context) {

    private val density = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    private fun load(id: Int): Bitmap? = try {
        BitmapFactory.decodeResource(resources, id)
    } catch (e: Throwable) {
        null
    }

    /**
     * The same full-body little dragon as on the home screen (same pictures, same pixel scale), so that both places show
     * him at EXACTLY the same size (Settings > R > dragon size). Loaded lazily, kept in memory.
     */
    private enum class Pose(val res: Int) {
        NORMAL(R.drawable.r_mascot_happy),
        LOOK(R.drawable.r_mascot_where),
        LAUGH(R.drawable.r_mascot_proud),
        POINT(R.drawable.r_mascot_open),
        YAWN(R.drawable.r_mascot_yawn),
        RUB(R.drawable.r_mascot_tired),
        BORED(R.drawable.r_mascot_stretch),
        NOD(R.drawable.r_mascot_tired),
    }

    private class Line(val text: String, val pose: Pose)

    /**
     * One beat of the stick scene: which picture the wise dragon shows, what is said and by whom, how long it stays
     * after the text is typed, and an optional sound (1 = swing whoosh, 2 = hit tick).
     */
    private class Step(
        val wise: Int,
        val text: String,
        val holdMs: Long,
        val sound: Int = 0,
        val speaker: Int = WISE,
    )

    private val bitmaps = HashMap<Int, Bitmap?>()
    private fun bitmapFor(res: Int): Bitmap? = bitmaps.getOrPut(res) { load(res) }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bubbleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2E7D32.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeJoin = Paint.Join.ROUND
    }
    private val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1B1B1B.toInt()
        textSize = 22f * density
        isFakeBoldText = true
    }

    // ---- sounds -------------------------------------------------------------------------------------------
    private val loadedSounds = HashSet<Int>()
    private var released = false
    private val sounds: SoundPool? = try {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
    } catch (e: Throwable) {
        null
    }
    private var tick1 = 0
    private var tick2 = 0
    private var whooshIn = 0
    private var whooshOut = 0
    private var tickFlip = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        sounds?.setOnLoadCompleteListener { _, id, status -> if (status == 0) loadedSounds.add(id) }
        try {
            tick1 = sounds?.load(context, R.raw.r_snd_tick1, 1) ?: 0
            tick2 = sounds?.load(context, R.raw.r_snd_tick2, 1) ?: 0
            whooshIn = sounds?.load(context, R.raw.r_snd_whoosh_in, 1) ?: 0
            whooshOut = sounds?.load(context, R.raw.r_snd_whoosh_out, 1) ?: 0
        } catch (e: Throwable) {
            // no sound is better than a crash
        }
    }

    private fun play(id: Int, volume: Float) {
        if (released || id == 0 || !soundOn() || id !in loadedSounds) return
        try {
            sounds?.play(id, volume, volume, 1, 0, 1f)
        } catch (e: Throwable) {
            // ignore
        }
    }

    // ---- state --------------------------------------------------------------------------------------------
    private var running = false
    private var firstDone = false
    private var shown = false
    private var pose = Pose.NORMAL
    private var visits = 0
    private var scenePending = false // the little dragon is making his quick visits; the wise dragon comes after them
    private var burstLeft = 0
    private var sceneIdx = -1 // -1 = normal visit, otherwise the current step of the stick scene
    private var wiseRes = 0 // picture of the wise dragon in the stick scene
    private var smallRes = R.drawable.r_mascot_happy // picture of the little dragon in the stick scene
    private var speaker = WISE
    private var smallSlide = 0f // little dragon slides in from the left edge
    private var wiseSlide = 0f // wise dragon slides in from the right edge
    private var squash = 0f // the little dragon is squashed by the blow
    private var grab = 0f // the little dragon is pulled into the wise dragon's coils
    private var exitT = 0f // the wise dragon leaves to the right
    private var slideT = 0f // 0 = hidden behind the edge, 1 = fully peeking
    private var bubbleAlpha = 0f
    private var typedChars = 0
    private var yFrac = 0.4f
    private var text = ""
    private var animator: ValueAnimator? = null
    private val cobraRect = RectF()
    private val smallRect = RectF()

    private var cachedText = ""
    private var cachedWidth = 0
    private var fullLayout: StaticLayout? = null
    private var textW0 = 0

    private val showRunnable: Runnable = Runnable {
        if (!running) return@Runnable
        if (!enabled() || !canShow()) {
            handler.postDelayed(showRunnable, RETRY_MS)
        } else {
            appear()
        }
    }
    private val hideRunnable: Runnable = Runnable { disappear() }
    private val stepRunnable: Runnable = Runnable { sceneStep(sceneIdx + 1) }

    // ---- lifecycle (called from ReaderActivity) -----------------------------------------------------------
    fun onActivityResume() {
        running = true
        cancelAll()
        handler.postDelayed(showRunnable, if (firstDone) AFTER_RESUME_MS else FIRST_MS)
    }

    fun onActivityPause() {
        running = false
        cancelAll()
        shown = false
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
        cancelAll()
        if (!released) {
            released = true
            try {
                sounds?.release()
            } catch (e: Throwable) {
                // ignore
            }
        }
    }

    private fun cancelAll() {
        handler.removeCallbacks(showRunnable)
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(stepRunnable)
        animator?.cancel()
        animator = null
    }

    private fun scheduleNext() {
        if (!running) return
        val base = intervalMinutes().coerceIn(1, 60) * 60_000L
        val delay = (base * (0.8 + Random.nextDouble() * 0.4)).toLong()
        handler.postDelayed(showRunnable, delay)
    }

    // ---- animation ----------------------------------------------------------------------------------------
    private fun run(
        durationMs: Long,
        from: Float,
        to: Float,
        interpolator: TimeInterpolator,
        onUpdate: (Float) -> Unit,
        onDone: () -> Unit,
    ) {
        val a = ValueAnimator.ofFloat(from, to)
        a.duration = durationMs
        a.interpolator = interpolator
        a.addUpdateListener {
            onUpdate(it.animatedValue as Float)
            invalidate()
        }
        a.addListener(
            object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) onDone()
                }
            },
        )
        animator = a
        a.start()
    }

    private fun appear() {
        sceneIdx = -1
        val night0 = nightOn() && Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 0..4
        if (scenePending && (!sceneOn() || night0)) scenePending = false
        if (!scenePending) {
            visits++
            if (sceneOn() && !night0 && visits % sceneEvery().coerceIn(2, 10) == 0) {
                scenePending = true
                burstLeft = Random.nextInt(2, 4) // the little dragon shows up 2-3 times in a row
            }
        }
        if (scenePending && burstLeft <= 0) {
            scenePending = false
            // both dragons are on screen together: the little one on the LEFT, the wise one comes from the RIGHT
            sceneIdx = 0
            wiseRes = SCENE[0].wise
            smallRes = R.drawable.r_mascot_happy
            speaker = WISE
            smallSlide = 0f
            wiseSlide = 0f
            squash = 0f
            grab = 0f
            exitT = 0f
            typedChars = 0
            bubbleAlpha = 0f
            firstDone = true
            shown = true
            slideT = 1f
            yFrac = 0.20f + Random.nextFloat() * 0.25f
            play(whooshIn, 0.5f)
            run(420, 0f, 1f, DecelerateInterpolator(), { smallSlide = it }) { sceneStep(0) }
            return
        }
        val night = nightOn() && Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 0..4
        val line = (if (night) NIGHT else NORMAL).random()
        text = line.text
        pose = line.pose
        yFrac = 0.30f + Random.nextFloat() * 0.25f
        firstDone = true
        shown = true
        slideT = 0f
        typedChars = 0
        bubbleAlpha = 0f
        play(whooshIn, 0.5f)
        run(420, 0f, 1f, DecelerateInterpolator(), { slideT = it }) { typeText() }
    }

    /**
     * The stick scene, one beat at a time: the wise dragon slides in from the right, raises the stick, the stick comes
     * down ON the little dragon's head (he is squashed and shows the dizzy picture), then the wise one pulls him into
     * his coils and leaves to the right.
     */
    private fun sceneStep(i: Int) {
        if (!running) return
        if (i >= SCENE.size) {
            sceneExit()
            return
        }
        sceneIdx = i
        val st = SCENE[i]
        wiseRes = st.wise
        text = st.text
        speaker = st.speaker
        typedChars = 0
        bubbleAlpha = 0f
        if (st.sound == 1) play(whooshOut, 0.7f)
        if (st.sound == 2) play(tick1, 1f)
        when (i) {
            0 -> {
                play(whooshIn, 0.5f)
                run(650, 0f, 1f, DecelerateInterpolator(), { wiseSlide = it }) { afterBeat(st) }
            }
            2 -> {
                // the contact: the stick tip is exactly on his head, he squashes and turns dizzy
                smallRes = R.drawable.r_stick_small_hit
                squash = 0f
                run(420, 0f, 1f, LinearInterpolator(), { squash = it }) { }
                afterBeat(st)
            }
            4 -> {
                // keep the swing picture while he is pulled in, then show the wise dragon carrying him
                wiseRes = SCENE[3].wise
                run(380, 0f, 1f, AccelerateInterpolator(), { grab = it }) {
                    wiseRes = st.wise
                    afterBeat(st)
                }
            }
            else -> afterBeat(st)
        }
    }

    private fun afterBeat(st: Step) {
        if (st.text.isEmpty()) {
            invalidate()
            handler.postDelayed(stepRunnable, st.holdMs)
        } else {
            typeText()
        }
    }

    private fun sceneExit() {
        play(whooshOut, 0.6f)
        bubbleAlpha = 0f
        run(600, 0f, 1f, AccelerateInterpolator(), { exitT = it }) { finishVisit() }
    }

    private fun finishVisit() {
        shown = false
        sceneIdx = -1
        invalidate()
        scheduleNext()
    }

    private fun typeText() {
        bubbleAlpha = 1f
        val n = text.length
        run(
            (n * 48L).coerceAtLeast(300L),
            0f,
            n.toFloat(),
            LinearInterpolator(),
            { v ->
                val c = v.toInt()
                if (c != typedChars) {
                    typedChars = c
                    if (c > 0 && c % 2 == 0 && !text[c - 1].isWhitespace()) {
                        tickFlip = !tickFlip
                        play(if (tickFlip) tick1 else tick2, 0.35f)
                    }
                }
            },
        ) {
            typedChars = n
            if (sceneIdx >= 0) {
                handler.postDelayed(stepRunnable, SCENE[sceneIdx].holdMs)
            } else {
                handler.postDelayed(hideRunnable, 2200L + n * 45L)
            }
        }
    }

    private fun disappear() {
        play(whooshOut, 0.45f)
        val from = slideT
        run(
            320,
            from,
            0f,
            AccelerateInterpolator(),
            {
                slideT = it
                bubbleAlpha = (it * 2f).coerceIn(0f, 1f)
            },
        ) {
            shown = false
            invalidate()
            if (scenePending) {
                if (burstLeft > 0) burstLeft--
                if (running) handler.postDelayed(showRunnable, QUICK_MS) else scheduleNext()
            } else {
                scheduleNext()
            }
        }
    }

    private fun dismiss() {
        cancelAll()
        if (sceneIdx >= 0) hideNow() else disappear()
    }

    /** The reader menu opened (or the reader is closing) while the cobra is out: leave without a sound. */
    private fun hideNow() {
        cancelAll()
        shown = false
        sceneIdx = -1
        scenePending = false
        invalidate()
        scheduleNext()
    }

    // ---- touch --------------------------------------------------------------------------------------------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!shown) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> cobraRect.contains(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                if (cobraRect.contains(event.x, event.y)) dismiss()
                true
            }
            else -> true
        }
    }

    // ---- drawing ------------------------------------------------------------------------------------------
    override fun onDraw(canvas: Canvas) {
        if (!shown) return
        if (!canShow()) {
            post { if (shown) hideNow() }
            return
        }
        if (sceneIdx >= 0) {
            drawScene(canvas)
            return
        }
        // the nodding pose swaps between its two frames while the text is being typed
        val res = if (pose == Pose.NOD && (typedChars / 5) % 2 == 1) R.drawable.r_mascot_blink else pose.res
        val bmp = bitmapFor(res) ?: return
        val cobraW = sizeDp().coerceIn(40, 160) * density // the same size as the home dragon
        val cobraH = cobraW * bmp.height / bmp.width
        val top = (height - cobraH) * yFrac
        val restLeft = 0f
        val left = restLeft - (1f - slideT) * (cobraW + 12f * density)
        cobraRect.set(left, top, left + cobraW, top + cobraH)
        canvas.drawBitmap(bmp, null, cobraRect, bitmapPaint)
        if (bubbleAlpha > 0f && typedChars > 0) {
            drawBubble(canvas, restLeft + cobraW - 4f * density, top + cobraH * 0.30f, false)
        }
    }

    /**
     * Draws the stick scene. The little dragon sits at the left edge. The wise dragon's pictures are drawn mirrored
     * (they face right) in a fixed frame placed so that, in the "hit" picture, the tip of the stick lands exactly on
     * the little dragon's head. Native numbers: "hit" picture is 402x312 and its stick tip is at (338, 278).
     */
    private fun drawScene(canvas: Canvas) {
        val d = density
        val sizeK = sizeDp().coerceIn(40, 160) / 100f // the scene was drawn for a 100dp dragon; everything scales with the size setting
        val s = 100f * d * sizeK // little dragon size (= the home dragon's size)
        val k = 128f * d * sizeK / 402f // screen px per native pixel of the wise pictures
        val smallLeft = 4f * d
        val above = (278f * k - s * 0.15f).coerceAtLeast(0f) // how far the wise dragon reaches above the little one's head
        val stageH = above + s
        val stageTop = ((height - stageH) * yFrac).coerceAtLeast(0f)
        val smallTop = stageTop + above
        // where the stick tip must land: on top of the little dragon's head
        val tipX = smallLeft + s * 0.50f
        val tipY = smallTop + s * 0.15f
        val frameLeft = tipX - (402f - 338f) * k // mirrored: the tip is (402 - 338) native px from the left
        val frameTop = tipY - 278f * k
        val frameBottom = frameTop + 312f * k
        val frameCx = frameLeft + 201f * k

        // ---- little dragon
        val smallBmp = bitmapFor(smallRes)
        val hidden = grab >= 1f
        if (smallBmp != null && !hidden) {
            val sh = s // hood width = the home dragon's hood width at the same size
            val sw = sh * smallBmp.width / smallBmp.height
            val slideX = -(1f - smallSlide) * (s + 16f * d)
            // being pulled in: moves toward the wise dragon's coils while shrinking
            val pullX = grab * (frameCx - (smallLeft + s / 2f))
            val pullY = grab * (frameBottom - 30f * d - (smallTop + sh / 2f))
            val cx = smallLeft + s / 2f + slideX + pullX
            val bottom = smallTop + s + pullY
            val sc = 1f - 0.85f * grab
            val sq = 1f - 0.20f * kotlin.math.sin(Math.PI.toFloat() * squash.coerceIn(0f, 1f))
            smallRect.set(cx - sw / 2f, bottom - sh, cx + sw / 2f, bottom)
            canvas.save()
            canvas.scale(sc, sc * sq, cx, bottom)
            canvas.drawBitmap(smallBmp, null, smallRect, bitmapPaint)
            canvas.restore()
        } else {
            smallRect.setEmpty()
        }

        // ---- wise dragon (mirrored)
        val wiseBmp = bitmapFor(wiseRes)
        cobraRect.setEmpty()
        if (wiseBmp != null) {
            val offset = (1f - wiseSlide) * (width - frameLeft + 8f * d) + exitT * (width - frameLeft + 8f * d)
            val ww = wiseBmp.width * k
            val wh = wiseBmp.height * k
            val cx = frameCx + offset
            cobraRect.set(cx - ww / 2f, frameBottom - wh, cx + ww / 2f, frameBottom)
            canvas.save()
            canvas.scale(-1f, 1f, cobraRect.centerX(), cobraRect.centerY())
            canvas.drawBitmap(wiseBmp, null, cobraRect, bitmapPaint)
            canvas.restore()
            if (!smallRect.isEmpty) cobraRect.union(smallRect)
        } else if (!smallRect.isEmpty) {
            cobraRect.set(smallRect)
        }

        // ---- speech bubble, to the right of whoever is talking
        if (bubbleAlpha > 0f && typedChars > 0 && exitT == 0f) {
            if (speaker == WISE) {
                drawBubble(canvas, frameLeft + 142f * d * sizeK, frameTop + 312f * k * 0.18f, false)
            } else {
                drawBubble(canvas, smallLeft + s * 0.9f, smallTop + s * 0.30f, false)
            }
        }
    }

    private fun buildLayout(content: CharSequence, widthPx: Int): StaticLayout =
        StaticLayout.Builder.obtain(content, 0, content.length, textPaint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .build()

    private fun drawBubble(canvas: Canvas, bx: Float, by: Float, fromRight: Boolean) {
        val pad = 12f * density
        val maxW = minOf(250f * density, (if (fromRight) bx else width - bx) - 12f * density)
        if (maxW < 110f * density) return

        // the full text decides the bubble size, so it does not grow while it is being typed
        if (fullLayout == null || cachedText != text || cachedWidth != width) {
            textW0 = (maxW - 2 * pad).toInt()
            fullLayout = buildLayout(text, textW0)
            cachedText = text
            cachedWidth = width
        }
        val full = fullLayout ?: return
        var lineMax = 0f
        for (i in 0 until full.lineCount) lineMax = maxOf(lineMax, full.getLineWidth(i))
        val bubbleW = ceil(lineMax) + 2 * pad
        val bubbleH = full.height + 2 * pad

        val shownText = SpannableString(text)
        if (typedChars < text.length) {
            shownText.setSpan(
                ForegroundColorSpan(Color.TRANSPARENT),
                typedChars,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        val layout = buildLayout(shownText, textW0)

        val a = (bubbleAlpha * 255).toInt().coerceIn(0, 255)
        bubbleFill.alpha = a
        bubbleStroke.alpha = a
        seamPaint.alpha = a
        textPaint.alpha = a

        val y = by.coerceIn(8f * density, (height - bubbleH - 8f * density).coerceAtLeast(8f * density))
        val x0 = if (fromRight) bx - bubbleW else bx
        val rect = RectF(x0, y, x0 + bubbleW, y + bubbleH)
        val r = 16f * density
        canvas.drawRoundRect(rect, r, r, bubbleFill)
        canvas.drawRoundRect(rect, r, r, bubbleStroke)

        // tail pointing at the cobra's mouth
        val y1 = y + 18f * density
        val y2 = y + 38f * density
        val tail = Path().apply {
            val d = if (fromRight) 14f else -14f
            moveTo(bx, y1)
            lineTo(bx + d * density, y + 30f * density)
            lineTo(bx, y2)
        }
        canvas.drawPath(tail, bubbleFill)
        canvas.drawPath(tail, bubbleStroke)
        canvas.drawLine(bx, y1 + 2f * density, bx, y2 - 2f * density, seamPaint)

        canvas.save()
        canvas.translate(x0 + pad - (textW0 - lineMax) / 2f, y + pad)
        layout.draw(canvas)
        canvas.restore()
    }

    companion object {
        private const val FIRST_MS = 45_000L
        private const val AFTER_RESUME_MS = 90_000L
        private const val RETRY_MS = 15_000L
        private const val QUICK_MS = 12_000L // gap between the little dragon's quick visits before the scene

        private const val WISE = 0
        private const val SMALL = 1

        /**
         * 0 wise slides in and scolds, 1 raises the stick, 2 the blow lands on the little dragon's head (no text),
         * 3 the little dragon complains, 4 the wise one pulls him in and carries him off.
         */
        private val SCENE = listOf(
            Step(R.drawable.r_stick_wise_walk, "إنت تاني؟! تلات مرات في دقيقة! مفيش أدب!", 900),
            Step(R.drawable.r_stick_wise_raise, "نصيحة من الحاج: اللي ما يسمعش الكلام... يتضرب!", 300, 1),
            Step(R.drawable.r_stick_wise_hit, "", 900, 2),
            Step(R.drawable.r_stick_wise_hit, "آآآي! دماغي!", 900, 0, SMALL),
            Step(R.drawable.r_stick_wise_walk_with_small, "يلا معايا. ونصيحة تانية: اشرب مية.", 700),
        )

        private val NORMAL = listOf(
            Line("الفصل ده شكله جامد، كمّل كمّل!", Pose.LAUGH),
            Line("إيه ده؟! هو فيه حد يقرا بالسرعة دي؟", Pose.LOOK),
            Line("أنا الكوبرا، جاي أطمّن عليك.", Pose.NORMAL),
            Line("ريّح عينيك شوية يا بطل، المانجا مش هتهرب.", Pose.POINT),
            Line("اشرب مية وارجع كمّل.", Pose.POINT),
            Line("بقالك فترة بتقرا... فخور بيك والله!", Pose.LAUGH),
            Line("ما تقلقش، مش هبوّظ عليك الأحداث.", Pose.NORMAL),
            Line("شد حيلك، فاضل شوية وتخلّص الفصل.", Pose.LOOK),
        )

        private val NIGHT = listOf(
            Line("إنت لسه صاحي؟! خش نام يا عم إنت!", Pose.YAWN),
            Line("الساعة بقت كده وإنت لسه بتقرا؟ نام وكمّل بكره.", Pose.BORED),
            Line("يا عم نام بقى، المانجا مش هتهرب منك!", Pose.RUB),
            Line("عينيك هتقفل لوحدها... خش نام وبكره نكمّل.", Pose.NOD),
            Line("لسه صاحي؟ ده أنا نفسي نعست!", Pose.YAWN),
        )
    }
}
