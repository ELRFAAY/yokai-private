package eu.kanade.tachiyomi.ui.more.stats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.getResourceColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.google.android.material.R as MR

/**
 * R: the profile at the top of the statistics: a small round picture, a big banner next to it (several banners flip by
 * themselves every few seconds, with dots below and swipe), and the name. Pictures are copied into the app's own
 * folder (files/r_profile) when picked.
 */
class RStatsProfileView(
    context: Context,
    private val prefs: PreferencesHelper,
    private val scope: CoroutineScope,
    private val onPickAvatar: () -> Unit,
    private val onPickBanners: () -> Unit,
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val accent = context.getResourceColor(MR.attr.colorSecondary)
    private val onSurface = context.getResourceColor(MR.attr.colorOnSurface)

    private val avatar = ImageView(context)
    private val pager = RecyclerView(context)
    private val snap = PagerSnapHelper()
    private var current = 0
    private val bannerHint = TextView(context)
    private val dots = LinearLayout(context)
    private val nameView = TextView(context)
    private var flipJob: Job? = null
    private var banners: List<String> = emptyList()

    private val adapter = BannerAdapter()

    init {
        orientation = VERTICAL
        val pad = (4 * density).toInt()
        setPadding(0, pad, 0, (14 * density).toInt())

        // one frame: the banner takes the whole width, the round picture sits on its bottom edge, the name goes below
        val row = FrameLayout(context).apply { clipChildren = false }

        // small round profile picture
        val size = (76 * density).toInt()
        avatar.apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(context.getResourceColor(MR.attr.colorSurface))
                setStroke((3 * density).toInt(), accent)
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
            setImageResource(R.drawable.ic_r_mood)
            imageTintList = android.content.res.ColorStateList.valueOf(accent)
            setPadding((18 * density).toInt(), (18 * density).toInt(), (18 * density).toInt(), (18 * density).toInt())
            contentDescription = "صورة البروفايل"
            setOnClickListener { showAvatarMenu() }
        }

        // big banner next to it
        val bannerCard = MaterialCardView(context).apply {
            radius = 20 * density
            cardElevation = 0f
            setCardBackgroundColor(ColorUtils.setAlphaComponent(accent, 50))
            setOnClickListener { showBannerMenu() }
        }
        // a RecyclerView swallows touches, so the card's own click never fired: detect the tap on the pager itself
        val tapDetector = android.view.GestureDetector(
            context,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                    showBannerMenu()
                    return true
                }
            },
        )
        pager.apply {
            addOnItemTouchListener(
                object : RecyclerView.SimpleOnItemTouchListener() {
                    override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                        tapDetector.onTouchEvent(e)
                        return false
                    }
                },
            )
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = this@RStatsProfileView.adapter
            snap.attachToRecyclerView(this)
            overScrollMode = OVER_SCROLL_NEVER
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                    val view = snap.findSnapView(layoutManager) ?: return
                    current = recyclerView.getChildAdapterPosition(view).coerceAtLeast(0)
                    updateDots(current)
                }
            })
        }
        bannerCard.addView(pager, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        bannerHint.apply {
            text = "اضغط لإضافة بانر"
            setTextColor(onSurface)
            alpha = 0.8f
            gravity = Gravity.CENTER
            setOnClickListener { showBannerMenu() }
        }
        bannerCard.addView(bannerHint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val bannerH = (150 * density).toInt()
        row.addView(bannerCard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bannerH))
        // dots on the top of the banner
        dots.apply { gravity = Gravity.CENTER }
        row.addView(
            dots,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
                topMargin = (8 * density).toInt()
            },
        )
        // the round picture half over the banner's bottom edge
        row.addView(
            avatar,
            FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            },
        )
        addView(
            row,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bannerH + size / 2),
        )

        // name
        nameView.apply {
            setTextColor(onSurface)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setOnClickListener { editName() }
        }
        addView(
            nameView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (6 * density).toInt()
            },
        )
        refresh()
    }

    /** Reloads everything from the preferences (after picking, deleting or renaming). */
    fun refresh() {
        val name = prefs.rProfileName().get()
        nameView.text = name.ifBlank { "اضغط لكتابة اسمك" }
        nameView.alpha = if (name.isBlank()) 0.6f else 1f

        val avatarPath = prefs.rProfileAvatar().get()
        if (avatarPath.isNotBlank() && File(avatarPath).exists()) {
            scope.launch {
                val bmp = decode(avatarPath, 400)
                if (bmp != null) {
                    avatar.imageTintList = null
                    avatar.setPadding(0, 0, 0, 0)
                    avatar.setImageBitmap(bmp)
                }
            }
        } else {
            val p = (18 * density).toInt()
            avatar.setImageResource(R.drawable.ic_r_mood)
            avatar.imageTintList = android.content.res.ColorStateList.valueOf(accent)
            avatar.setPadding(p, p, p, p)
        }

        banners =prefs.rProfileBanners().get().split('\n').filter { it.isNotBlank() && File(it).exists() }
        adapter.submit(banners)
        bannerHint.isVisible = banners.isEmpty()
        pager.isVisible = banners.isNotEmpty()
        buildDots()
        current = current.coerceIn(0, (banners.size - 1).coerceAtLeast(0))
        updateDots(current)
        restartFlipping()
    }

    private fun buildDots() {
        dots.removeAllViews()
        if (banners.size < 2) return
        val s = (8 * density).toInt()
        repeat(banners.size) {
            val dot = View(context)
            dots.addView(
                dot,
                LayoutParams(s, s).apply {
                    marginStart = (4 * density).toInt()
                    marginEnd = (4 * density).toInt()
                },
            )
        }
    }

    private fun updateDots(position: Int) {
        for (i in 0 until dots.childCount) {
            dots.getChildAt(i).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (i == position) accent else ColorUtils.setAlphaComponent(onSurface, 70))
            }
        }
    }

    private fun restartFlipping() {
        flipJob?.cancel()
        if (banners.size < 2) return
        flipJob = scope.launch {
            while (true) {
                delay(prefs.rProfileInterval().get().coerceIn(2, 30) * 1000L)
                if (isAttachedToWindow && banners.size > 1) {
                    current = (current + 1) % banners.size
                    pager.smoothScrollToPosition(current)
                    updateDots(current)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        flipJob?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartFlipping()
    }

    /** A tap on the banner only adds the first picture; every other banner option lives in the 3-dot menu of the statistics. */
    private fun showBannerMenu() {
        if (banners.isEmpty()) onPickBanners()
    }

    private fun avatarFile(): File? = prefs.rProfileAvatar().get().takeIf { it.isNotBlank() && File(it).exists() }?.let { File(it) }

    /** A tap on the round picture (or the menu entry): choose a new one, edit / crop the current one, or remove it. */
    fun showAvatarMenu() {
        val has = avatarFile() != null
        if (!has) {
            onPickAvatar()
            return
        }
        val items = arrayOf("اختيار صورة جديدة", "تعديل / قص / تدوير الصورة الحالية", "حذف الصورة")
        MaterialAlertDialogBuilder(context)
            .setTitle("الصورة الشخصية")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> onPickAvatar()
                    1 -> editAvatar()
                    2 -> {
                        prefs.rProfileAvatar().set("")
                        refresh()
                    }
                }
            }
            .show()
    }

    private fun editAvatar() {
        val path = avatarFile()?.absolutePath ?: return
        scope.launch {
            val bmp = decode(path, 2048) ?: return@launch
            val crop = RCropView(context, bmp, 1f, true)
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(crop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (300 * density).toInt()))
                addView(
                    com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = "تدوير ٩٠°"
                        setOnClickListener { crop.rotate() }
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        topMargin = (8 * density).toInt()
                    },
                )
            }
            MaterialAlertDialogBuilder(context)
                .setTitle("تعديل وقص")
                .setView(box)
                .setPositiveButton("حفظ") { _, _ ->
                    scope.launch {
                        val newPath = withContext(Dispatchers.IO) {
                            try {
                                val out = File(File(context.filesDir, "r_profile").apply { mkdirs() }, "img_${System.currentTimeMillis()}_a.jpg")
                                out.outputStream().use { crop.render(600).compress(Bitmap.CompressFormat.JPEG, 92, it) }
                                out.absolutePath.takeIf { out.length() > 0 }
                            } catch (_: Exception) {
                                null
                            }
                        } ?: return@launch
                        prefs.rProfileAvatar().set(newPath)
                        refresh()
                    }
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    fun editCurrentBanner() {
        if (banners.isNotEmpty()) editBanner(current.coerceIn(0, banners.size - 1))
    }

    fun deleteCurrentBanner() {
        if (banners.isEmpty()) return
        val idx = current.coerceIn(0, banners.size - 1)
        prefs.rProfileBanners().set(banners.filterIndexed { i, _ -> i != idx }.joinToString("\n"))
        refresh()
    }

    fun deleteAllBanners() {
        prefs.rProfileBanners().set("")
        refresh()
    }

    /** A list of the banners: press a picture and drag it to its new place, then save. */
    fun showReorder() {
        if (banners.size < 2) return
        val list = banners.toMutableList()
        val thumbs = HashMap<String, Bitmap?>()
        val rv = RecyclerView(context)
        lateinit var helper: androidx.recyclerview.widget.ItemTouchHelper
        val ad = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = list.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val img = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(0, (56 * density).toInt(), 1f)
                }
                val handle = ImageView(context).apply {
                    setImageResource(R.drawable.ic_drag_handle_24dp)
                    alpha = 0.7f
                    setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
                    layoutParams = LinearLayout.LayoutParams((48 * density).toInt(), (56 * density).toInt())
                }
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, (3 * density).toInt(), 0, (3 * density).toInt())
                    }
                    addView(img)
                    addView(handle)
                }
                val h = object : RecyclerView.ViewHolder(row) {}
                // the handle starts the drag on touch; a long press on the picture does the same
                handle.setOnTouchListener { _, e ->
                    if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) helper.startDrag(h)
                    false
                }
                return h
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val path = list[position]
                val img = (holder.itemView as LinearLayout).getChildAt(0) as ImageView
                img.setImageDrawable(null)
                img.tag = path
                scope.launch {
                    val bmp = thumbs.getOrPut(path) { decode(path, 400) }
                    if (img.tag == path) img.setImageBitmap(bmp)
                }
            }
        }
        helper = androidx.recyclerview.widget.ItemTouchHelper(
            object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN,
                0,
            ) {
                override fun isLongPressDragEnabled() = true
                override fun onMove(r: RecyclerView, a: RecyclerView.ViewHolder, b: RecyclerView.ViewHolder): Boolean {
                    val from = a.bindingAdapterPosition
                    val to = b.bindingAdapterPosition
                    if (from < 0 || to < 0) return false
                    list.add(to, list.removeAt(from))
                    ad.notifyItemMoved(from, to)
                    return true
                }
                override fun onSwiped(h: RecyclerView.ViewHolder, direction: Int) {}
            },
        )
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = ad
        helper.attachToRecyclerView(rv)
        val hint = TextView(context).apply {
            text = "اضغط ضغطة طويلة على الصورة (أو امسك المقبض على الجنب) وحرّكها لفوق أو لتحت"
            alpha = 0.7f
            setPadding((20 * density).toInt(), (8 * density).toInt(), (20 * density).toInt(), (8 * density).toInt())
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(hint)
            addView(rv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (360 * density).toInt()).apply {
                marginStart = (16 * density).toInt()
                marginEnd = (16 * density).toInt()
            })
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("ترتيب الصور")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                prefs.rProfileBanners().set(list.joinToString("\n"))
                refresh()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    /** Crop / rotate a banner that is already saved (the shown one). */
    private fun editBanner(idx: Int) {
        val path = banners.getOrNull(idx) ?: return
        scope.launch {
            val bmp = decode(path, 2048) ?: return@launch
            val crop = RCropView(context, bmp, 2.2f, false)
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(crop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (300 * density).toInt()))
                addView(
                    com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = "تدوير ٩٠°"
                        setOnClickListener { crop.rotate() }
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        topMargin = (8 * density).toInt()
                    },
                )
            }
            MaterialAlertDialogBuilder(context)
                .setTitle("تعديل وقص")
                .setView(box)
                .setPositiveButton("حفظ") { _, _ ->
                    scope.launch {
                        val newPath = withContext(Dispatchers.IO) {
                            try {
                                val out = java.io.File(java.io.File(context.filesDir, "r_profile").apply { mkdirs() }, "img_${System.currentTimeMillis()}_e.jpg")
                                out.outputStream().use { crop.render(1080).compress(Bitmap.CompressFormat.JPEG, 92, it) }
                                out.absolutePath.takeIf { out.length() > 0 }
                            } catch (_: Exception) {
                                null
                            }
                        } ?: return@launch
                        val list = banners.toMutableList()
                        if (idx in list.indices) list[idx] = newPath
                        prefs.rProfileBanners().set(list.joinToString("\n"))
                        refresh()
                    }
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    private fun editName() {
        val input = EditText(context).apply {
            setText(prefs.rProfileName().get())
            hint = "اسمك"
            setSingleLine()
        }
        val box = FrameLayout(context).apply {
            val m = (20 * density).toInt()
            setPadding(m, (8 * density).toInt(), m, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("الاسم")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                prefs.rProfileName().set(input.text.toString().trim())
                refresh()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private suspend fun decode(path: String, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Throwable) {
            null
        }
    }

    private inner class BannerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var items: List<String> = emptyList()

        fun submit(list: List<String>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val image = ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.TRANSPARENT)
            }
            return object : RecyclerView.ViewHolder(image) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val image = holder.itemView as ImageView
            image.setImageDrawable(null)
            val path = items[position]
            scope.launch {
                val bmp = decode(path, 1200)
                if (holder.bindingAdapterPosition == position) image.setImageBitmap(bmp)
            }
        }
    }
}
