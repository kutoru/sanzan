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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sanzan.ui.theme.三算Theme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.tensorflow.lite.task.vision.segmenter.Segmentation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
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
    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )

    var cameraOpen by remember { mutableStateOf(false) }

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

    var segmentations by remember { mutableStateOf<List<Segmentation>>(emptyList()) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    var pause by remember { mutableStateOf(false) }

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
                        val (newSegmentations, newBitmap) = checkCallback(pause, bitmap)

                        if (newSegmentations != null) {
                            segmentations = newSegmentations
                        }

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
                val segsize = segmentations.size
                val masksize = segmentations.getOrNull(0)?.masks?.size
                val maskwidth = segmentations.getOrNull(0)?.masks?.getOrNull(0)?.width
                val maskheight = segmentations.getOrNull(0)?.masks?.getOrNull(0)?.height

                println("SEGS $segsize MASKS $masksize MASK ${maskwidth}x$maskheight")

                val segmentation = segmentations.getOrNull(0) ?: return@Canvas
                val mask = segmentation.masks.getOrNull(0) ?: return@Canvas

                coloredLabels =
                    segmentation.coloredLabels.map { Pair(it.getlabel(), Color(it.argb)) }

                val colors =
                    segmentation.coloredLabels.map {
                        val c = Color(it.argb); c.copy(alpha = 0.5f).toArgb()
                    }

                val maskBitmap =
                    createColoredMaskBitmap(mask.buffer, mask.width, mask.height, colors)

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
                    segmentations = emptyList()
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


