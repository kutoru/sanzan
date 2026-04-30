package com.example.sanzan

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.createBitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.File

fun rotateBitmap(src: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle)
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
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

    uri?.let {
        resolver.openOutputStream(it)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            println("saved")
        }
    }
}

fun saveMatToFile(mat: Mat) {
    val bitmap = createBitmap(mat.cols(), mat.rows())
    Utils.matToBitmap(mat, bitmap)
    saveBitmapToFile(bitmap)
}
