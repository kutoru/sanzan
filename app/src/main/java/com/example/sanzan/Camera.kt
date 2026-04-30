package com.example.sanzan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.util.Size
import android.view.TextureView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

typealias CheckUpdateCallback = (
        (pause: Boolean, bitmapPresent: Boolean) -> Pair<List<Circle>, Bitmap?>,
) -> Unit

@Composable
fun CameraPreview(
    checkUpdate: CheckUpdateCallback,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val textureView = remember {
        TextureView(context).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surfaceTexture: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) {
                    val surface = android.view.Surface(surfaceTexture)
                    startCamera(surface, context, lifecycleOwner, checkUpdate)
                }

                override fun onSurfaceTextureSizeChanged(
                    surfaceTexture: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) {
                }

                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                    ProcessCameraProvider.getInstance(context).get().unbindAll()
                    surfaceTexture.release()
                    return true
                }

                override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
            }
        }
    }

    AndroidView(
        factory = { textureView },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun startCamera(
    surface: android.view.Surface,
    context: Context,
    lifecycleOwner: LifecycleOwner,
    checkUpdate: CheckUpdateCallback,
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val cameraProvider = cameraProviderFuture.get()

    val preview = androidx.camera.core.Preview.Builder()
        .setTargetResolution(Size(1080, 1080))
        .build()

    preview.setSurfaceProvider { surfaceRequest ->
        surfaceRequest.provideSurface(surface, ContextCompat.getMainExecutor(context)) {}
    }

    val imageAnalysis = ImageAnalysis.Builder()
        .setTargetResolution(Size(1080, 1080))
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

    imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
        processImageProxy(checkUpdate, imageProxy)
    }

    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
}

fun processImageProxy(
    checkUpdate: CheckUpdateCallback,
    imageProxy: ImageProxy,
) {
    checkUpdate { pause, bitmapPresent ->
        if (pause && bitmapPresent) {
            imageProxy.close()
            return@checkUpdate Pair(emptyList(), null)
        }

        if (imageProxy.width != imageProxy.height) {
            imageProxy.close()
            throw Error("imageProxy isn't square")
        }

        if (pause) {
            val bitmap = rotateBitmap(
                imageProxy.toBitmap(),
                imageProxy.imageInfo.rotationDegrees.toFloat(),
            )

            imageProxy.close()
            return@checkUpdate Pair(emptyList(), bitmap)
        }

        val circles = CircleProcessor.houghCirclesOnImageWithDebounce(imageProxy)

        imageProxy.close()
        return@checkUpdate Pair(circles, null)
    }
}