package com.example.sanzan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class CircleEdges(bitmap: Bitmap, circle: Circle) {
    private val edges: Mat
    val offsetX: Float
    val offsetY: Float

    init {
        val (circleBitmap, newOffsetX, newOffsetY) = applyCircleMaskWithResize(bitmap, circle)

        edges = findEdges(circleBitmap)
        offsetX = newOffsetX
        offsetY = newOffsetY
    }

    private fun applyCircleMaskWithResize(
        bitmap: Bitmap,
        circle: Circle,
    ): Triple<Bitmap, Float, Float> {
        val scale = bitmap.width.toFloat() / circle.srcSize
        val scaledCircle = Circle(
            circle.cx * scale,
            circle.cy * scale,
            circle.radius * scale,
            bitmap.width,
        )

        val size = scaledCircle.radius.toInt() * 2;
        val maskedBitmap = createBitmap(size, size)
        val canvas = Canvas(maskedBitmap)

        val circlePath = android.graphics.Path().apply {
            addCircle(size / 2f, size / 2f, size / 2f, android.graphics.Path.Direction.CW)
        }

        val offsetX = scaledCircle.cx - scaledCircle.radius
        val offsetY = scaledCircle.cy - scaledCircle.radius

        canvas.clipPath(circlePath)
        canvas.drawBitmap(
            bitmap,
            offsetX * -1,
            offsetY * -1,
            null
        )

        return Triple(maskedBitmap, offsetX, offsetY)
    }

    private fun findEdges(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
//        saveMatToFile(mat)

        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
//        saveMatToFile(gray)

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, org.opencv.core.Size(5.0, 5.0), 0.0)
//        saveMatToFile(blurred)

        val edges = Mat()
        Imgproc.Canny(blurred, edges, 50.0, 150.0)
//        saveMatToFile(edges)

//    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(7.0, 7.0))
//    val closed = Mat()
//    Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)
//    saveMatToFile(closed)

        return edges
    }

    fun toBitmap(): Bitmap {
        val width = edges.cols()
        val height = edges.rows()

        val bitmap = createBitmap(width, height)
        val pixels = IntArray(width * height)

        val buffer = ByteArray(width * height)
        edges.get(0, 0, buffer)

        var whites = 0
        var blacks = 0

        for (i in 0 until width * height) {
            val pixel = buffer[i].toInt() and 0xFF

            if (pixel > 0) {
                whites++
                pixels[i] = Color.GREEN
            } else {
                blacks++
                pixels[i] = Color.TRANSPARENT
            }
        }

        println("edge stats: ${width}x${height} ${width * height} $whites $blacks ${whites.toFloat() / (width * height) * 100}%")

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun countPerDegree(): IntArray {
        val width = edges.width()
        val height = edges.height()

        val cx = width / 2.0
        val cy = height / 2.0

        val offsets = mutableListOf<Pair<Double, Double>>()

        val buffer = ByteArray(width * height)
        edges.get(0, 0, buffer)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = buffer[y * width + x].toInt() and 0xFF

                if (pixel > 0) {
                    val dx = x - cx
                    val dy = y - cy

                    offsets.add(Pair(dx, dy))
                }
            }
        }

        val diameterLineWidth = (width + height / 2.0) / 10.0
        val perpendicularDistance = diameterLineWidth / 2.0

        val result = IntArray(360)

        for (theta in 0 until 360) {
            var count = 0

            val rad = theta * PI / 180
            val sinT = sin(rad)
            val cosT = cos(rad)

            for ((dx, dy) in offsets) {
                val distance = abs(dx * sinT - dy * cosT)
                if (distance <= perpendicularDistance) {
                    count++
                }
            }

            result[theta] = count
        }

        return result
    }
}