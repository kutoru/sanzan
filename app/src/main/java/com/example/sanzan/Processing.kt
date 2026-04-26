package com.example.sanzan

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.imgproc.Imgproc

typealias CheckUpdateCallback = (
        (pause: Boolean, bitmap: Bitmap?) -> Pair<List<DetectedBox>?, Bitmap?>,
) -> Unit

data class DetectedBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

private val objectDetector = ObjectDetection.getClient(
    ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .build()
)

@androidx.annotation.OptIn(ExperimentalGetImage::class)
fun processImageProxy(
    imageProxy: ImageProxy,
    checkUpdate: CheckUpdateCallback,
) {
    val mediaImage = imageProxy.image

    if (mediaImage != null) {
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        objectDetector.process(inputImage)
            .addOnSuccessListener { results ->
                val detections = results.map { obj ->
                    DetectedBox(
                        obj.boundingBox.left.toFloat(),
                        obj.boundingBox.top.toFloat(),
                        obj.boundingBox.width().toFloat(),
                        obj.boundingBox.height().toFloat()
                    )
                }

                checkUpdate { pause, bitmap ->
                    if (pause && bitmap != null) {
                        return@checkUpdate Pair(null, null)
                    }

                    if (pause) {
                        val bitmap = rotateBitmap(imageProxy.toBitmap(), 90f)
                        return@checkUpdate Pair(detections, bitmap)
                    }

                    return@checkUpdate Pair(detections, null)
                }
            }
            .addOnFailureListener { e ->
                println("Error detecting objects $e")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

fun findContours(bitmap: Bitmap?): List<Mat> {
    println("is bitmap null? ${bitmap === null}")
    if (bitmap == null) {
        return emptyList()
    }

    val mat = Mat()
    Utils.bitmapToMat(bitmap, mat)

    val gray = Mat()
    Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

    val blurred = Mat()
    Imgproc.GaussianBlur(gray, blurred, org.opencv.core.Size(9.0, 9.0), 0.0)

    val edges = Mat()
    Imgproc.Canny(blurred, edges, 50.0, 150.0)

    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(5.0, 5.0))
    val closed = Mat()
    Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)

    val contours = mutableListOf<MatOfPoint>()
    Imgproc.findContours(
        closed,
        contours,
        Mat(),
        Imgproc.RETR_EXTERNAL,
        Imgproc.CHAIN_APPROX_SIMPLE
    )

    println("found: ${contours.size} contours")

    val minArea = 1000.0
    val filteredContours = contours.filter { Imgproc.contourArea(it) >= minArea }

    println("after filter: ${filteredContours.size} contours")

    return filteredContours
}

fun contourToOffsets(contour: Mat, canvasSize: androidx.compose.ui.geometry.Size): List<Offset> {
    val points = mutableListOf<Offset>()
    val total = contour.rows()

    for (i in 0 until total) {
        val point = contour.get(i, 0) as DoubleArray
        val x = point[0].toFloat()
        val y = point[1].toFloat()

        val scaleX = canvasSize.width / IMAGE_WIDTH
        val scaleY = canvasSize.height / IMAGE_HEIGHT
        points.add(Offset(x * scaleX, y * scaleY))
    }

    return points
}

fun offsetsToPath(offsets: List<Offset>): Path {
    return Path().apply {
        if (offsets.isEmpty()) {
            return@apply
        }

        moveTo(offsets.first().x, offsets.first().y)

        for (i in 1 until offsets.size) {
            lineTo(offsets[i].x, offsets[i].y)
        }

        close()
    }
}

fun rotateBitmap(src: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle)
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}
