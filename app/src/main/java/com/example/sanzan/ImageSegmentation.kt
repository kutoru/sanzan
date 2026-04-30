package com.example.sanzan

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.segmenter.ImageSegmenter
import org.tensorflow.lite.task.vision.segmenter.Segmentation

const val PRIMARY_CATEGORY = 23

fun createMaskBitmap(
    mask: TensorImage,
    getPixelColor: (category: Int) -> Int,
): Bitmap {
    val buffer = mask.buffer
    val width = mask.width
    val height = mask.height

    val bitmap = createBitmap(width, height)
    val pixels = IntArray(width * height)

    buffer.rewind()

    for (i in 0 until width * height) {
        val category = buffer.get(i).toInt()
        pixels[i] = getPixelColor(category)
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}

class ImageSegmentation(imageSegmenter: ImageSegmenter, bitmap: Bitmap) {
    private val segmentations: List<Segmentation>
    private val segmentation: Segmentation?
    private val mask: TensorImage?

    val coloredLabels: List<Pair<String, Color>>?

    init {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        segmentations = imageSegmenter.segment(tensorImage)

        val segsize = segmentations.size
        val masksize = segmentations.getOrNull(0)?.masks?.size
        val maskwidth = segmentations.getOrNull(0)?.masks?.getOrNull(0)?.width
        val maskheight = segmentations.getOrNull(0)?.masks?.getOrNull(0)?.height

        println("SEGS $segsize MASKS $masksize MASK ${maskwidth}x$maskheight")

        segmentation = segmentations.getOrNull(0)
        mask = segmentation?.masks?.getOrNull(0)

        coloredLabels = segmentation?.coloredLabels?.map {
            Pair(it.getlabel(), Color(it.argb))
        }
    }

    fun createColoredMaskBitmap(): Bitmap? {
        if (mask == null || coloredLabels == null) {
            return null
        }

        val colors = coloredLabels.map {
            it.second.copy(alpha = 0.5f).toArgb()
        }

        val bitmap = createMaskBitmap(mask) { category ->
            if (category == 0) {
                android.graphics.Color.TRANSPARENT
            } else if (category < colors.size) {
                colors[category]
            } else {
                android.graphics.Color.BLACK
            }
        }

        return bitmap
    }

    fun createBinaryMaskBitmap(): Bitmap? {
        if (mask == null) {
            return null
        }

        val bitmap = createMaskBitmap(mask) { category ->
            if (category > 0) {
                android.graphics.Color.WHITE
            } else {
                android.graphics.Color.BLACK
            }
        }

        return bitmap
    }

    fun createFilteredBinaryMaskBitmap(foregroundCategory: Int): Bitmap? {
        if (mask == null) {
            return null
        }

        val bitmap = createMaskBitmap(mask) { category ->
            if (category == foregroundCategory) {
                android.graphics.Color.WHITE
            } else {
                android.graphics.Color.BLACK
            }
        }

        return bitmap
    }
}