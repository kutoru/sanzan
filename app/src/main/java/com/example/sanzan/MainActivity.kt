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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sanzan.ui.theme.三算Theme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.drop
import org.opencv.core.Mat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("opencv_java4")  // opencv crash fix

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

    var detections by remember { mutableStateOf<List<DetectedBox>>(emptyList()) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var contours by remember { mutableStateOf<List<Mat>>(emptyList()) }

    var pause by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { bitmap }
            .drop(1)
            .collect { newBitmap ->
                if (newBitmap != null) {
                    contours = findContours(bitmap)
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 32.dp)
            .pointerInput(Unit) {
                awaitEachGesture { awaitPointerEvent() }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            Text(
                text = "Camera loading...",
                fontSize = 24.sp,
            )

            CameraPreview(
                checkUpdate = { checkCallback ->
                    val (newDetections, newBitmap) = checkCallback(pause, bitmap)

                    if (newDetections != null) {
                        detections = newDetections
                    }

                    if (newBitmap != null) {
                        bitmap = newBitmap
                    }
                },
            )
        } else if (bitmap != null) {
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
            detections.forEach { box ->
                val scaleX = size.width / IMAGE_WIDTH
                val scaleY = size.height / IMAGE_HEIGHT

                drawRect(
                    color = Color.Green,
                    topLeft = Offset(box.left * scaleX, box.top * scaleY),
                    size = androidx.compose.ui.geometry.Size(
                        box.width * scaleX,
                        box.height * scaleY,
                    ),
                    style = Stroke(width = 4f)
                )
            }

            contours.forEach { contour ->
                val offsets = contourToOffsets(contour, size)
                val path = offsetsToPath(offsets)

                drawPath(
                    path = path,
                    color = Color.Red,
                    style = Stroke(width = 1f)
                )

                drawPath(path, Color.Red.copy(alpha = 0.3f), style = Fill)
            }
        }

        Button(onClick = {
            if (pause) {
                detections = emptyList()
                bitmap = null
                contours = emptyList()
            }

            pause = !pause
        }, modifier = Modifier.align(Alignment.BottomCenter)) {
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


