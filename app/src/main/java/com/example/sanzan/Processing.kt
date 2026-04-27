package com.example.sanzan

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.segmenter.ImageSegmenter
import org.tensorflow.lite.task.vision.segmenter.Segmentation
import java.nio.ByteBuffer

const val CIRCLE_IMAGE_SIZE = 360

typealias CheckUpdateCallback = (
        (pause: Boolean, bitmap: Bitmap?) -> Triple<List<Segmentation>?, Bitmap?, List<Circle>>,
) -> Unit

data class Circle(
    val cx: Float,
    val cy: Float,
    val radius: Float,
)

object CircleProcessor {
    private const val SKIP_COUNT = 3
    private var count = 0
    private var circles: List<Circle> = emptyList()

    fun findWithDebounce(imageProxy: ImageProxy): List<Circle> {
        println("debounce $count")

        count++

        if (count >= SKIP_COUNT) {
            count = 0
            circles = houghCircles(imageProxy)
        }

        return circles
    }

    fun find(imageProxy: ImageProxy): List<Circle> {
        count = 0
        circles = houghCircles(imageProxy)

        return circles
    }

    private fun houghCircles(imageProxy: ImageProxy): List<Circle> {
        val smallBitmap = imageProxy.toBitmap().scale(CIRCLE_IMAGE_SIZE, CIRCLE_IMAGE_SIZE)
        val bitmap =
            rotateBitmap(smallBitmap, imageProxy.imageInfo.rotationDegrees.toFloat())

        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, org.opencv.core.Size(5.0, 5.0), 0.0)

        val circles = Mat()
        Imgproc.HoughCircles(
            blurred,
            circles,
            Imgproc.HOUGH_GRADIENT,
            1.0,
            CIRCLE_IMAGE_SIZE / 1.0,
            100.0,
            50.0,
            CIRCLE_IMAGE_SIZE / 4,
        )

        val circleList = mutableListOf<Circle>()
        for (col in 0 until circles.cols()) {
            val circleData = circles.get(0, col)
            val centerX = circleData[0].toFloat()
            val centerY = circleData[1].toFloat()
            val radius = circleData[2].toFloat()

            circleList.add(Circle(centerX, centerY, radius))
        }

        return circleList
    }
}

fun processImageProxy(
    imageSegmenter: ImageSegmenter,
    checkUpdate: CheckUpdateCallback,
    imageProxy: ImageProxy,
) {
    checkUpdate { pause, bitmap ->
        if (!pause || bitmap != null) {
            val circles = CircleProcessor.findWithDebounce(imageProxy)
            imageProxy.close()

            return@checkUpdate Triple(null, null, circles)
        }

        val circles = CircleProcessor.find(imageProxy)

        val newBitmap =
            rotateBitmap(imageProxy.toBitmap(), imageProxy.imageInfo.rotationDegrees.toFloat())

        val tensorImage = TensorImage.fromBitmap(newBitmap)
        val newSegmentations = imageSegmenter.segment(tensorImage)

        imageProxy.close()

        return@checkUpdate Triple(newSegmentations, newBitmap, circles)
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