package eu.kanade.tachiyomi.ui.reader

import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.progressindicator.CircularProgressIndicator
import eu.kanade.tachiyomi.databinding.ReaderNavBinding
import eu.kanade.tachiyomi.databinding.ReaderNavVerticalBinding

/**
 * The reader's page bar. There are two layouts with the same views: the original horizontal bar
 * and the vertical bar on the right edge (R switch "r_vertical_nav"). The rest of the reader code
 * only talks to this class, so it does not care which one is shown.
 */
class ReaderNavViews(
    val root: ReaderNavView,
    val leftChapter: ImageButton,
    val rightChapter: ImageButton,
    val leftProgress: CircularProgressIndicator,
    val rightProgress: CircularProgressIndicator,
    val leftPageText: TextView,
    val rightPageText: TextView,
    val pageSeekbar: ReaderSlider,
    val autoScroll: ImageButton,
) {
    companion object {
        fun of(b: ReaderNavBinding) = ReaderNavViews(
            b.root, b.leftChapter, b.rightChapter, b.leftProgress, b.rightProgress,
            b.leftPageText, b.rightPageText, b.pageSeekbar, b.autoScroll,
        )

        fun of(b: ReaderNavVerticalBinding) = ReaderNavViews(
            b.root, b.leftChapter, b.rightChapter, b.leftProgress, b.rightProgress,
            b.leftPageText, b.rightPageText, b.pageSeekbar, b.autoScroll,
        )
    }
}
