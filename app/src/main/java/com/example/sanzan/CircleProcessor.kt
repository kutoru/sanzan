package com.example.sanzan

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.core.graphics.scale
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc

data class Circle(
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val srcSize: Int,
)

object CircleProcessor {
    private const val CIRCLE_IMAGE_SIZE = 360
    private const val SKIP_COUNT = 3

    private var count = 0
    private var circles: List<Circle> = emptyList()

    fun houghCirclesOnImageWithDebounce(imageProxy: ImageProxy): List<Circle> {
        count++

        if (count >= SKIP_COUNT) {
            count = 0

            val scaledBitmap = imageProxy.toBitmap().scale(CIRCLE_IMAGE_SIZE, CIRCLE_IMAGE_SIZE)
            val rotatedBitmap = rotateBitmap(
                scaledBitmap,
                imageProxy.imageInfo.rotationDegrees.toFloat(),
            )

            circles = houghCircles(rotatedBitmap)
        }

        return circles
    }

    fun houghCirclesOnImage(bitmap: Bitmap): List<Circle> {
        val scaledBitmap = bitmap.scale(CIRCLE_IMAGE_SIZE, CIRCLE_IMAGE_SIZE)
        return houghCircles(scaledBitmap)
    }

    private fun houghCircles(bitmap: Bitmap): List<Circle> {
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

            circleList.add(
                Circle(
                    centerX,
                    centerY,
                    radius,
                    bitmap.width,
                )
            )
        }

        return circleList
    }

    fun houghCirclesOnMask(mask: Bitmap): List<Circle> {
        val mat = Mat()
        Utils.bitmapToMat(mask, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)
//        saveMatToFile(mat)

        val openKernel =
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(11.0, 11.0))
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_OPEN, openKernel)
//        saveMatToFile(mat)

        val closeKernel =
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(15.0, 15.0))
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_CLOSE, closeKernel)
//        saveMatToFile(mat)

        val blurred = Mat()
        Imgproc.GaussianBlur(mat, blurred, org.opencv.core.Size(5.0, 5.0), 0.0)
//        saveMatToFile(blurred)

        val circles = Mat()
        Imgproc.HoughCircles(
            blurred,
            circles,
            Imgproc.HOUGH_GRADIENT,
            1.0,
            mask.width / 1.0,
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

            circleList.add(
                Circle(
                    centerX,
                    centerY,
                    radius,
                    mask.width,
                )
            )
        }

        return circleList
    }

    fun fitEllipseOnMask(mask: Bitmap): Circle? {
        val mat = Mat()
        Utils.bitmapToMat(mask, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)
//        saveMatToFile(mat)

        val openKernel =
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(11.0, 11.0))
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_OPEN, openKernel)
//        saveMatToFile(mat)

        val closeKernel =
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(15.0, 15.0))
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_CLOSE, closeKernel)
//        saveMatToFile(mat)

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
        val radius = (ellipse.size.width + ellipse.size.height) / 4.0

        return Circle(
            center.x.toFloat(),
            center.y.toFloat(),
            radius.toFloat(),
            mask.width,
        )
    }
}