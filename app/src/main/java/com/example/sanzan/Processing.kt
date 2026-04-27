package com.example.sanzan

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.segmenter.ImageSegmenter
import org.tensorflow.lite.task.vision.segmenter.Segmentation
import java.io.File

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

    fun houghCirclesOnMask(mask: Bitmap): List<Circle> {
        val mat = Mat()
        Utils.bitmapToMat(mask, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)
        saveMatToFile(mat)

        val openKernel =
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(15.0, 15.0))
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_OPEN, openKernel)
        saveMatToFile(mat)

        val closeKernel =
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(25.0, 25.0))
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_CLOSE, closeKernel)
        saveMatToFile(mat)

//        val hull = applyConvexHull(mat)
//        saveMatToFile(hull)

        val blurred = Mat()
        Imgproc.GaussianBlur(mat, blurred, org.opencv.core.Size(5.0, 5.0), 0.0)
        saveMatToFile(blurred)

        val circles = Mat()
        Imgproc.HoughCircles(
            blurred,
            circles,
            Imgproc.HOUGH_GRADIENT,
            1.0,
            mask.width / 4.0,
            50.0,
            30.0,
            mask.width / 4,
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

    fun applyConvexHull(maskMat: Mat): Mat {
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(
            maskMat,
            contours,
            Mat(),
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        val hullMask = Mat.zeros(maskMat.size(), CvType.CV_8UC1)

        for (contour in contours) {
            if (Imgproc.contourArea(contour) < 100) continue

            val hullIndexes = MatOfInt()
            Imgproc.convexHull(contour, hullIndexes)

            val points = contour.toList()
            val indexList = hullIndexes.toList()
            val hullPoints = indexList.map { points[it] }

            val hullMatOfPoint = MatOfPoint()
            hullMatOfPoint.fromList(hullPoints)
            Imgproc.fillPoly(hullMask, listOf(hullMatOfPoint), Scalar(255.0))
        }
        return hullMask
    }

    fun minEnclosingCircleOnMask(mask: Bitmap): Circle? {
        val mat = Mat()
        Utils.bitmapToMat(mask, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)
        saveMatToFile(mat)

        val smallOpenKernel =
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(5.0, 5.0))
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_OPEN, smallOpenKernel)
        saveMatToFile(mat)

        val openKernel =
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(21.0, 21.0))
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_OPEN, openKernel)
        saveMatToFile(mat)

        val closeKernel =
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(31.0, 31.0))
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_CLOSE, closeKernel)
        saveMatToFile(mat)

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(
            mat,
            contours,
            Mat(),
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        val largestContour = contours.maxByOrNull { Imgproc.contourArea(it) } ?: return null

        val points = MatOfPoint2f()
        largestContour.convertTo(points, CvType.CV_32FC2)

        val center = org.opencv.core.Point()
        val radius = FloatArray(1)
        Imgproc.minEnclosingCircle(points, center, radius)

        return Circle(center.x.toFloat(), center.y.toFloat(), radius[0])
    }

    fun fitEllipseOnMask(mask: Bitmap): Circle? {
        val mat = Mat()
        Utils.bitmapToMat(mask, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)
        saveMatToFile(mat)

        val openKernel =
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(11.0, 11.0))
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_OPEN, openKernel)
        saveMatToFile(mat)

        val closeKernel =
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(19.0, 19.0))
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_CLOSE, closeKernel)
        saveMatToFile(mat)

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(
            mat,
            contours,
            Mat(),
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        val largestContour = contours.maxByOrNull { Imgproc.contourArea(it) } ?: return null

        val points2f = MatOfPoint2f()
        largestContour.convertTo(points2f, CvType.CV_32FC2)

        if (points2f.total() < 5) return null

        val ellipse = Imgproc.fitEllipse(points2f)

        val center = ellipse.center
        val radius = (ellipse.size.width + ellipse.size.height) / 4.0  // average of half‑axes

        return Circle(center.x.toFloat(), center.y.toFloat(), radius.toFloat())
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
    mask: TensorImage,
    colors: List<Int>,
): Bitmap {
    val buffer = mask.buffer
    val width = mask.width
    val height = mask.height

    val bitmap = createBitmap(width, height)
    val pixels = IntArray(width * height)

    buffer.rewind()

    for (i in 0 until width * height) {
        val category = buffer.get(i).toInt()

        if (category > 0 && category < colors.size) {
            pixels[i] = colors[category]
        } else {
            pixels[i] = Color.TRANSPARENT
        }
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}

fun createBinaryMaskBitmap(
    mask: TensorImage,
    foregroundCategory: Int,
): Bitmap {
    val buffer = mask.buffer
    val width = mask.width
    val height = mask.height

    val bitmap = createBitmap(width, height)
    val pixels = IntArray(width * height)

    buffer.rewind()

    for (i in 0 until width * height) {
        val category = buffer.get(i).toInt()

        if (category == foregroundCategory) {
//        if (category > 0) {
            pixels[i] = Color.WHITE
        } else {
            pixels[i] = Color.BLACK
        }
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}

var temp_context: Context? = null

fun saveBitmapToFile(bitmap: Bitmap) {
    val resolver = temp_context!!.contentResolver

    val contentValues = ContentValues().apply {
        val name = "sanzan_${System.currentTimeMillis()}.png"
        println("saving $name")

        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
//        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}")

        val picDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val file = File(picDir, name)
        put(MediaStore.MediaColumns.DATA, file.absolutePath)
    }

    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

//    uri?.let {
//        resolver.openOutputStream(it)?.use { outputStream ->
//            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
//            println("saved")
//        }
//    }
}

fun saveMatToFile(mat: Mat) {
    val bitmap = createBitmap(mat.cols(), mat.rows())
    Utils.matToBitmap(mat, bitmap)
    saveBitmapToFile(bitmap)
}