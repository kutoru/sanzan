package com.example.sanzan

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.segmenter.ImageSegmenter
import org.tensorflow.lite.task.vision.segmenter.Segmentation
import java.nio.ByteBuffer

typealias CheckUpdateCallback = (
        (pause: Boolean, bitmap: Bitmap?) -> Pair<List<Segmentation>?, Bitmap?>,
) -> Unit

fun processImageProxy(
    imageSegmenter: ImageSegmenter,
    checkUpdate: CheckUpdateCallback,
    imageProxy: ImageProxy,
) {
    checkUpdate { pause, bitmap ->
        if (!pause || bitmap != null) {
            imageProxy.close()
            return@checkUpdate Pair(null, null)
        }

        val newBitmap =
            rotateBitmap(imageProxy.toBitmap(), imageProxy.imageInfo.rotationDegrees.toFloat())

        val tensorImage = TensorImage.fromBitmap(newBitmap)
        val newSegmentations = imageSegmenter.segment(tensorImage)

        imageProxy.close()

        return@checkUpdate Pair(newSegmentations, newBitmap)
    }
}

fun rotateBitmap(src: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle)
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}

fun createColoredMaskBitmap(
    maskBuffer: ByteBuffer,
    width: Int,
    height: Int,
    colors: List<Int>,
): Bitmap {
    val bitmap = createBitmap(width, height)
    val pixels = IntArray(width * height)

    maskBuffer.rewind()

    for (i in 0 until width * height) {
        val category = maskBuffer.get(i).toInt()
        val color = if (category > 0 && category < colors.size) {
            colors[category]
        } else {
            Color.TRANSPARENT
        }
        pixels[i] = color
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}