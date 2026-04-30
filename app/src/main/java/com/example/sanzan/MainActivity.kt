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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sanzan.ui.theme.三算Theme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.tensorflow.lite.task.vision.segmenter.ImageSegmenter

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

    val context = LocalContext.current

    val imageSegmenter = remember {
        ImageSegmenter.createFromFile(
            context,
            "mobile_food_segmenter_V1.tflite"
        )
    }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    var segmentation by remember { mutableStateOf<ImageSegmentation?>(null) }
    var circles by remember { mutableStateOf<List<Pair<Circle, Color>>>(emptyList()) }
    var edges by remember { mutableStateOf<CircleEdges?>(null) }

    var realTimeCircles by remember { mutableStateOf<List<Circle>>(emptyList()) }
    var pause by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { bitmap }
            .collect { newBitmap ->
                val (newSegmentation, newCircles, newEdges) = onBitmapUpdate(
                    newBitmap,
                    imageSegmenter
                )

                segmentation = newSegmentation
                circles = newCircles
                edges = newEdges
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
            segmentation?.coloredLabels?.forEachIndexed { index, label ->
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
                        val (newRealTimeCircles, newBitmap) = checkCallback(pause, bitmap != null)

                        realTimeCircles = newRealTimeCircles

                        if (newBitmap != null) {
                            bitmap = newBitmap
                        }
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
                realTimeCircles.forEach {
                    val scale = size.width / it.srcSize

                    drawCircle(
                        color = Color(255, 0, 0, 127),
                        radius = it.radius * scale,
                        center = Offset(it.cx * scale, it.cy * scale),
                        style = Stroke(width = 8f),
                    )
                }

                if (segmentation != null) {
                    val maskBitmap = segmentation!!.createColoredMaskBitmap()

                    if (maskBitmap != null) {
                        drawImage(
                            image = maskBitmap.asImageBitmap(),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt())
                        )
                    }
                }

                circles.forEach {
                    val scale = size.width / it.first.srcSize

                    drawCircle(
                        color = it.second,
                        radius = it.first.radius * scale,
                        center = Offset(it.first.cx * scale, it.first.cy * scale),
                        style = Stroke(width = 8f),
                    )
                }

                if (bitmap != null && edges != null) {
                    val edgeBitmap = edges!!.toBitmap()

                    val scale = size.width / bitmap!!.width

                    val x = edges!!.offsetX * scale
                    val y = edges!!.offsetY * scale
                    val w = edgeBitmap.width * scale
                    val h = edgeBitmap.height * scale

                    drawImage(
                        image = edgeBitmap.asImageBitmap(),
                        dstSize = IntSize(w.toInt(), h.toInt()),
                        dstOffset = IntOffset(x.toInt(), y.toInt()),
                    )
                }
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

fun onBitmapUpdate(
    bitmap: Bitmap?,
    imageSegmenter: ImageSegmenter,
): Triple<ImageSegmentation?, List<Pair<Circle, Color>>, CircleEdges?> {
    if (bitmap == null) {
        return Triple(null, emptyList(), null)
    }

    val segmentation = ImageSegmentation(imageSegmenter, bitmap)
    val segBitmap = segmentation.createFilteredBinaryMaskBitmap(PRIMARY_CATEGORY)

    val circles = mutableListOf<Pair<Circle, Color>>()

    CircleProcessor.houghCirclesOnImage(bitmap).forEach {
        circles.add(Pair(it, Color(255, 0, 0, 127)))
    }

    if (segBitmap != null) {
        CircleProcessor.houghCirclesOnMask(segBitmap)
            .forEach {
                circles.add(Pair(it, Color(0, 0, 255, 127)))
            }

        CircleProcessor.fitEllipseOnMask(segBitmap)?.let {
            circles.add(Pair(it, Color(0, 255, 0, 127)))
        }
    }

    val edges = CircleEdges(bitmap, circles[0].first)

    return Triple(segmentation, circles, edges)
}