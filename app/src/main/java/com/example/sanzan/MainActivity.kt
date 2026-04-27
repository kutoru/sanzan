package com.example.sanzan

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sanzan.ui.theme.三算Theme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.drop
import org.tensorflow.lite.task.vision.segmenter.Segmentation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        System.loadLibrary("opencv_java4")  // opencv crash fix

        setContent {
            三算Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(modifier: Modifier) {
    temp_context = LocalContext.current

    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )
    val storagePermissionState = rememberPermissionState(
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    var cameraOpen by remember { mutableStateOf(false) }

    if (!storagePermissionState.status.isGranted) {
        Button(
            modifier = modifier,
            onClick = { storagePermissionState.launchPermissionRequest() },
        ) {
            Text(text = "storage perm")
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Text(
            text = "Hello there pleb!",
//            text = "Seems like the addict is back huh",
            modifier = Modifier
                .align(Alignment.TopCenter),
            fontSize = 24.sp,
            lineHeight = 32.sp,
            textAlign = TextAlign.Center,
        )

        if (cameraPermissionState.status.isGranted) {
            Button(
                onClick = { cameraOpen = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Text(text = "Start capturing", fontSize = 24.sp, modifier = Modifier.padding(8.dp))
            }
        } else {
            Button(
                onClick = { cameraPermissionState.launchPermissionRequest() },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Text(
                    text = "Allow camera",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (cameraOpen) {
        CameraScreen(modifier, onBack = { cameraOpen = false })
    }
}

@Composable
fun CameraScreen(modifier: Modifier, onBack: () -> Unit) {
    BackHandler { onBack() }

    var coloredLabels by remember { mutableStateOf<List<Pair<String, Color>>>(emptyList()) }
    var maskCircles by remember { mutableStateOf<List<Pair<Circle, Color>>>(emptyList()) }

    var segmentations by remember { mutableStateOf<List<Segmentation>>(emptyList()) }
    var circles by remember { mutableStateOf<List<Circle>>(emptyList()) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    var pause by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { segmentations }
            .drop(1)
            .collect { newSegmentations ->
                val segmentation = newSegmentations.getOrNull(0) ?: return@collect
                val mask = segmentation.masks.getOrNull(0) ?: return@collect

                val bitmap = createBinaryMaskBitmap(mask, 23)
                val newMaskCircles = mutableListOf<Pair<Circle, Color>>()

                CircleProcessor.houghCirclesOnMask(bitmap)
                    .forEach {
                        newMaskCircles.add(Pair(it, Color(0, 0, 255, 128)))
                    }

                CircleProcessor.minEnclosingCircleOnMask(bitmap)?.let {
                    newMaskCircles.add(Pair(it, Color(255, 255, 0, 128)))
                }

                CircleProcessor.fitEllipseOnMask(bitmap)?.let {
                    newMaskCircles.add(Pair(it, Color(0, 255, 0, 128)))
                }

                maskCircles = newMaskCircles
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitEachGesture { awaitPointerEvent() }
            },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            coloredLabels.forEachIndexed { index, label ->
                Text(text = "$index. ${label.first}", color = label.second)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap == null) {
                Text(
                    text = "Camera loading...",
                    fontSize = 24.sp,
                )

                CameraPreview(
                    checkUpdate = { checkCallback ->
                        val (newSegmentations, newBitmap, newCircles) = checkCallback(pause, bitmap)

                        if (newSegmentations != null) {
                            segmentations = newSegmentations
                        }

                        if (newBitmap != null) {
                            bitmap = newBitmap
                        }

                        circles = newCircles
                    },
                )
            } else {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                maskCircles.forEach {
                    val scale = size.width / 513

                    drawCircle(
                        color = it.second,
                        radius = it.first.radius * scale,
                        center = Offset(it.first.cx * scale, it.first.cy * scale),
                        style = Stroke(width = 8f),
                    )
                }

                circles.forEach {
                    val scale = size.width / CIRCLE_IMAGE_SIZE

                    drawCircle(
                        color = Color(255, 0, 0, 128),
                        radius = it.radius * scale,
                        center = Offset(it.cx * scale, it.cy * scale),
                        style = Stroke(width = 8f),
                    )
                }

                val segsize = segmentations.size
                val masksize = segmentations.getOrNull(0)?.masks?.size
                val maskwidth = segmentations.getOrNull(0)?.masks?.getOrNull(0)?.width
                val maskheight = segmentations.getOrNull(0)?.masks?.getOrNull(0)?.height

//                println("SEGS $segsize MASKS $masksize MASK ${maskwidth}x$maskheight CANVAS ${size.width}x${size.height}")

                val segmentation = segmentations.getOrNull(0) ?: return@Canvas
                val mask = segmentation.masks.getOrNull(0) ?: return@Canvas

                coloredLabels =
                    segmentation.coloredLabels.map { Pair(it.getlabel(), Color(it.argb)) }

                val colors =
                    segmentation.coloredLabels.map {
                        val c = Color(it.argb); c.copy(alpha = 0.5f).toArgb()
                    }

                val maskBitmap = createColoredMaskBitmap(mask, colors)

                drawImage(
                    image = maskBitmap.asImageBitmap(),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Button(onClick = {
                if (pause) {
                    maskCircles = emptyList()
                    segmentations = emptyList()
                    circles = emptyList()
                    bitmap = null
                }

                pause = !pause
            }) {
                Icon(
                    imageVector = if (pause) {
                        Icons.Default.Close
                    } else {
                        Icons.Default.AddCircle
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(32.dp)
                )
            }
        }
    }
}


