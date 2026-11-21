package eu.kanade.tachiyomi.ui.library

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import androidx.core.content.ContextCompat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import coil3.dispose
import coil3.size.Scale
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.dominantCoverColors
import eu.kanade.tachiyomi.databinding.MangaGridItemBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.util.lang.highlightText
import eu.kanade.tachiyomi.util.manga.MangaCoverMetadata
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.backgroundColor
import eu.kanade.tachiyomi.util.view.setCards
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import yokai.util.coil.loadManga

/**
 * Class used to hold the displayed data of a manga in the library, like the cover or the title.
 * All the elements from the layout file "item_catalogue_grid" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @constructor creates a new library holder.
 */
class LibraryGridHolder(
    private val view: View,
    adapter: LibraryCategoryAdapter,
    private val compact: Boolean,
    val fixedSize: Boolean,
) : LibraryHolder(view, adapter) {

    private val binding = MangaGridItemBinding.bind(view)
    private val rPrefs: eu.kanade.tachiyomi.data.preference.PreferencesHelper by uy.kohesive.injekt.injectLazy()
    private val defaultCardShape = binding.card.shapeAppearanceModel
    private var boundItem: LibraryMangaItem? = null

    init {
        binding.playLayout.setOnClickListener { playButtonClicked() }
        binding.playLayout.setOnLongClickListener { itemView.performLongClick() }
        if (compact) {
            binding.textLayout.isVisible = false
        } else {
            binding.compactTitle.isVisible = false
            binding.gradient.isVisible = false
            listOf(binding.playLayout, binding.playButton).forEach {
                it.updateLayoutParams<FrameLayout.LayoutParams> {
                    gravity = Gravity.BOTTOM or Gravity.END
                }
            }
        }
    }

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param item the manga item to bind.
     */
    override fun onSetValues(item: LibraryItem) {
        if (item !is LibraryMangaItem) throw IllegalStateException("Only LibraryMangaItem can use grid holder")
        // Update the title and subtitle of the manga.
        setCards(adapter.showOutline, binding.card, binding.unreadDownloadBadge.root)
        binding.playButton.transitionName = "library chapter $bindingAdapterPosition transition"
        binding.constraintLayout.isVisible = item.manga.manga.id != Long.MIN_VALUE
        binding.title.text = (PinnedManga.titlePrefix(item.manga.manga.id) + item.manga.manga.title).highlightText(item.filter, color)
        binding.behindTitle.text = item.manga.manga.title
        val mangaColor = item.manga.manga.dominantCoverColors
        binding.coverConstraint.backgroundColor = mangaColor?.first ?: itemView.context.getResourceColor(R.attr.background)
        binding.behindTitle.setTextColor(
            mangaColor?.second ?: itemView.context.getResourceColor(R.attr.colorOnBackground),
        )
        val authorArtist = if (item.manga.manga.author == item.manga.manga.artist || item.manga.manga.artist.isNullOrBlank()) {
            item.manga.manga.author?.trim() ?: ""
        } else {
            listOfNotNull(
                item.manga.manga.author?.trim()?.takeIf { it.isNotBlank() },
                item.manga.manga.artist?.trim()?.takeIf { it.isNotBlank() },
            ).joinToString(", ")
        }
        binding.subtitle.text = authorArtist.highlightText(item.filter, color)

        binding.compactTitle.text = binding.title.text?.toString()?.highlightText(item.filter, color)

        binding.title.post {
            val hasAuthorInFilter =
                item.filter.isNotBlank() && authorArtist.contains(item.filter, true)
            binding.subtitle.isVisible =
                (binding.title.lineCount <= 1 || hasAuthorInFilter) && authorArtist.isNotBlank()
            binding.title.maxLines = if (hasAuthorInFilter) 1 else 2
        }

        boundItem = item
        applyColorCard(item)
        setUnreadBadge(binding.unreadDownloadBadge.badgeView, item)
        setReadingButton(item)
        setSelected(adapter.isSelected(flexibleAdapterPosition))

        // Update the cover.
        binding.coverThumbnail.dispose()
        setCover(item.manga.manga)
    }

    /**
     * R: "colored card": in the comfortable grid the title strip under the cover takes the cover's dominant color
     * (text in the matching contrast color) and the cover's bottom corners become square so cover and strip look like one card.
     */
    private fun applyColorCard(item: LibraryMangaItem) {
        val ctx = itemView.context
        val on = !compact && rPrefs.rColorCards().get() && item.manga.manga.id != Long.MIN_VALUE
        val baseHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 31f, ctx.resources.displayMetrics).toInt()
        with(binding) {
            if (on) {
                val colors = item.manga.manga.dominantCoverColors
                val bg = colors?.first ?: ctx.getResourceColor(R.attr.background)
                val fg = colors?.second ?: ctx.getResourceColor(R.attr.colorOnBackground)
                val radius = 12.dpToPx.toFloat()
                textLayout.background = GradientDrawable().apply {
                    setColor(bg)
                    cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
                }
                textLayout.setPadding(8.dpToPx, 5.dpToPx, 8.dpToPx, 6.dpToPx)
                textLayout.updateLayoutParams<ViewGroup.LayoutParams> { height = baseHeight + 11.dpToPx }
                title.setTextColor(fg)
                subtitle.setTextColor((fg and 0x00FFFFFF) or (0xB3 shl 24))
                card.shapeAppearanceModel = defaultCardShape.toBuilder()
                    .setBottomLeftCornerSize(0f)
                    .setBottomRightCornerSize(0f)
                    .build()
            } else {
                textLayout.background = null
                textLayout.setPadding(0, 0, 0, 0)
                textLayout.updateLayoutParams<ViewGroup.LayoutParams> { height = baseHeight }
                title.setTextColor(ContextCompat.getColorStateList(ctx, R.color.library_comfortable_title_selector))
                subtitle.setTextColor(ContextCompat.getColorStateList(ctx, R.color.library_comfortable_subtitle_selector))
                card.shapeAppearanceModel = defaultCardShape
            }
        }
    }

    override fun toggleActivation() {
        super.toggleActivation()
        setSelected(adapter.isSelected(flexibleAdapterPosition))
    }

    fun setSelected(isSelected: Boolean) {
        with(binding) {
            card.strokeWidth = when {
                isSelected -> 3.dpToPx
                adapter.showOutline -> 1.dpToPx
                else -> 0
            }
            arrayOf(card, unreadDownloadBadge.root, title, subtitle).forEach {
                it.isSelected = isSelected
            }
        }
    }

    private fun setCover(manga: Manga) {
        if ((adapter.recyclerView.context as? Activity)?.isDestroyed == true) return
        binding.coverThumbnail.loadManga(manga) {
            val hasRatio = binding.coverThumbnail.layoutParams.height != ViewGroup.LayoutParams.WRAP_CONTENT
            if (!fixedSize && !hasRatio) {
                scale(Scale.FIT)
            }
            listener(
                onSuccess = { _, _ ->
                    boundItem?.takeIf { it.manga.manga.id == manga.id }?.let { applyColorCard(it) }
                    if (!fixedSize && !hasRatio && MangaCoverMetadata.getRatio(manga) != null) {
                        setFreeformCoverRatio(manga)
                    }
                },
            )
        }
    }

    fun setFreeformCoverRatio(manga: Manga, parent: AutofitRecyclerView? = null) {
        binding.setFreeformCoverRatio(manga, parent)
    }

    private fun playButtonClicked() {
        adapter.libraryListener?.startReading(flexibleAdapterPosition, binding.playButton)
    }

    override fun onActionStateChanged(position: Int, actionState: Int) {
        super.onActionStateChanged(position, actionState)
        if (actionState == 2) {
            binding.card.isDragged = true
            binding.unreadDownloadBadge.badgeView.isDragged = true
        }
    }

    override fun onItemReleased(position: Int) {
        super.onItemReleased(position)
        binding.card.isDragged = false
        binding.unreadDownloadBadge.badgeView.isDragged = false
    }
}

fun MangaGridItemBinding.setFreeformCoverRatio(manga: Manga?, parent: AutofitRecyclerView? = null) {
    val ratio = manga?.let { MangaCoverMetadata.getRatio(it) }
    val itemWidth = parent?.itemWidth ?: root.width
    if (ratio != null) {
        coverThumbnail.adjustViewBounds = false
        coverThumbnail.maxHeight = (itemWidth / 3f * 10f).toInt()
        coverThumbnail.minimumHeight = 56.dpToPx
        constraintLayout.minHeight = 56.dpToPx
    } else {
        val coverHeight = (itemWidth / 3f * 4f).toInt()
        constraintLayout.minHeight = coverHeight / 2
        coverThumbnail.minimumHeight =
            (itemWidth / 3f * 3.6f).toInt()
        coverThumbnail.maxHeight = (itemWidth / 3f * 6f).toInt()
        coverThumbnail.adjustViewBounds = true
    }
    coverThumbnail.updateLayoutParams<ConstraintLayout.LayoutParams> {
        if (ratio != null) {
            height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            matchConstraintMaxHeight = coverThumbnail.maxHeight
            matchConstraintMinHeight = coverThumbnail.minimumHeight
            dimensionRatio = "W,1:$ratio"
        } else {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            dimensionRatio = null
        }
    }
}

fun MangaGridItemBinding.setBGAndFG(libraryLayout: Int) {
    val bottom =
        if (libraryLayout == LibraryItem.LAYOUT_COMFORTABLE_GRID) {
            2.dpToPx
        } else {
            card.marginBottom - 2.dpToPx
        }
    val others =
        if (libraryLayout == LibraryItem.LAYOUT_COMPACT_GRID) {
            4.dpToPx
        } else {
            5.dpToPx
        }
    (constraintLayout.background as? RippleDrawable)?.apply {
        for (i in 0 until numberOfLayers) {
            setLayerInset(i, others, others, others, bottom)
        }
    }
    (constraintLayout.foreground as? RippleDrawable)?.apply {
        if (libraryLayout == LibraryItem.LAYOUT_COMFORTABLE_GRID) {
            setLayerSize(1, 0, 0)
        }
        for (i in 0 until numberOfLayers) {
            setLayerInset(i, others, others, others, bottom)
        }
    }
}
