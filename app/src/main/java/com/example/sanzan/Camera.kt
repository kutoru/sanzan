package com.example.sanzan

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Size
import android.view.TextureView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.tensorflow.lite.task.vision.segmenter.ImageSegmenter
import java.util.concurrent.Executors

const val IMAGE_WIDTH = 1080
const val IMAGE_HEIGHT = 1080

@Composable
fun CameraPreview(
    checkUpdate: CheckUpdateCallback,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageSegmenter by remember { mutableStateOf<ImageSegmenter?>(null) }

    val textureView = remember {
        TextureView(context).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surfaceTexture: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) {
                    val surface = android.view.Surface(surfaceTexture)
                    imageSegmenter = ImageSegmenter.createFromFile(
                        context,
                        "mobile_food_segmenter_V1.tflite"
                    )
                    startCamera(surface, context, lifecycleOwner, imageSegmenter!!, checkUpdate)
                }

                override fun onSurfaceTextureSizeChanged(
                    surfaceTexture: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) {
                }

                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                    ProcessCameraProvider.getInstance(context).get().unbindAll()
                    imageSegmenter?.close()
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
    imageSegmenter: ImageSegmenter,
    checkUpdate: CheckUpdateCallback,
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val cameraProvider = cameraProviderFuture.get()

    val preview = androidx.camera.core.Preview.Builder()
        .setTargetResolution(Size(IMAGE_WIDTH, IMAGE_HEIGHT))
        .build()

    preview.setSurfaceProvider { surfaceRequest ->
        surfaceRequest.provideSurface(surface, ContextCompat.getMainExecutor(context)) {}
    }

    val imageAnalysis = ImageAnalysis.Builder()
        .setTargetResolution(Size(IMAGE_WIDTH, IMAGE_HEIGHT))
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

    imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
        processImageProxy(imageSegmenter, checkUpdate, imageProxy)
    }

    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
}
