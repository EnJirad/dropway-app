package com.pocketdev.dropway.ui.screens.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pocketdev.dropway.R
import com.pocketdev.dropway.core.scanner.AutoLabelDetector
import com.pocketdev.dropway.core.scanner.BarcodeDecoder
import com.pocketdev.dropway.core.scanner.BarcodeResult
import com.pocketdev.dropway.core.scanner.DetectionStatus
import com.pocketdev.dropway.core.util.AudioFeedback
import com.pocketdev.dropway.ui.theme.*
import java.util.concurrent.Executors

@Composable
fun Camera2ScannerView(
    modifier: Modifier = Modifier,
    isContinuousMode: Boolean = false,
    enableAutoLabelDetect: Boolean = true,
    onBarcodeDetected: (BarcodeResult) -> Unit,
    onPhotoCaptured: (Bitmap) -> Unit,
    overlayGuideText: String = "วางบาร์โค้ดหรือฉลากพัสดุให้อยู่ในกรอบ"
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DropwayNavy),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_camera),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "ต้องการสิทธิ์การใช้งานกล้องถ่ายรูป",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "เพื่อสแกนบาร์โค้ดและอ่านฉลากพัสดุแบบออฟไลน์บนตัวเครื่อง",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("อนุญาตการใช้งานกล้อง", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    var isFlashOn by remember { mutableStateOf(false) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }
    var activeImageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var detectionStatus by remember { mutableStateOf(DetectionStatus.SEARCHING) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }

    fun capturePhoto() {
        val capture = activeImageCapture ?: return
        capture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val rawBitmap = imageProxy.toBitmap()
                        val finalBitmap = if (rotation != 0) {
                            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                            val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                            if (rotated != rawBitmap) {
                                rawBitmap.recycle()
                            }
                            rotated
                        } else {
                            rawBitmap
                        }
                        mainHandler.post {
                            onPhotoCaptured(finalBitmap)
                        }
                    } catch (_: Exception) {
                    } finally {
                        imageProxy.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    activeImageCapture = imageCapture

                    var prevGray: ByteArray? = null
                    var holdStart = 0L
                    var stillSince = 0L
                    var cooldownUntil = 0L

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(Size(1280, 720))
                        .build()

                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        val now = System.currentTimeMillis()
                        if (now < cooldownUntil) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        try {
                            // 1. Direct zero-copy barcode analysis on raw Y plane
                            val barcodeResult = BarcodeDecoder.decodeImageProxy(imageProxy)
                            if (barcodeResult != null) {
                                cooldownUntil = now + 1500L
                                mainHandler.post {
                                    AudioFeedback.beepCapture(ctx)
                                    onBarcodeDetected(barcodeResult)
                                }
                                return@setAnalyzer
                            }

                            // 2. Auto-label detection for single mode
                            if (enableAutoLabelDetect && !isContinuousMode) {
                                val yBytes = BarcodeDecoder.extractYBytes(imageProxy)
                                val rotation = imageProxy.imageInfo.rotationDegrees
                                val rotatedY = BarcodeDecoder.rotateY(yBytes, imageProxy.width, imageProxy.height, rotation)
                                val finalW = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
                                val finalH = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

                                val analysis = AutoLabelDetector.analyzeYPlane(rotatedY, finalW, finalH)
                                if (analysis != null) {
                                    val isLabel = AutoLabelDetector.looksLikeLabel(analysis)
                                    val tooClose = !isLabel && AutoLabelDetector.looksTooClose(analysis)
                                    val diff = if (prevGray != null) {
                                        AutoLabelDetector.frameDifference(prevGray!!, analysis.gray)
                                    } else 999.0
                                    val moving = diff > 9.0
                                    prevGray = analysis.gray

                                    val currentTime = System.currentTimeMillis()
                                    if (!isLabel) {
                                        holdStart = 0L
                                        stillSince = 0L
                                        val nextStatus = if (tooClose) DetectionStatus.STEP_BACK else DetectionStatus.SEARCHING
                                        if (detectionStatus != nextStatus) {
                                            mainHandler.post { detectionStatus = nextStatus }
                                        }
                                    } else {
                                        if (holdStart == 0L) holdStart = currentTime
                                        if (moving) {
                                            stillSince = 0L
                                        } else if (stillSince == 0L) {
                                            stillSince = currentTime
                                        }

                                        val stillFor = if (stillSince > 0L) currentTime - stillSince else 0L
                                        if (stillFor >= AutoLabelDetector.AUTO_STILL_MS && (currentTime - holdStart) >= AutoLabelDetector.AUTO_HOLD_MS) {
                                            holdStart = 0L
                                            stillSince = 0L
                                            cooldownUntil = currentTime + 2500L
                                            mainHandler.post {
                                                detectionStatus = DetectionStatus.CAPTURING
                                                AudioFeedback.beepCapture(ctx)
                                                capturePhoto()
                                            }
                                        } else {
                                            val nextStatus = if (stillFor >= AutoLabelDetector.AUTO_STILL_MS) DetectionStatus.HOLDING else DetectionStatus.SEARCHING
                                            if (detectionStatus != nextStatus) {
                                                mainHandler.post { detectionStatus = nextStatus }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis,
                            imageCapture
                        )
                        activeCamera = camera
                    } catch (_: Exception) {
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Scanner Reticle Overlay
        ScannerReticleOverlay(
            modifier = Modifier.fillMaxSize(),
            guideText = overlayGuideText
        )

        // Top Control Bar (Flash and Live Status Indicator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Status Badge
            Surface(
                color = DropwayNavy.copy(alpha = 0.88f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dotColor = when (detectionStatus) {
                        DetectionStatus.CAPTURING -> StatusDelivered
                        DetectionStatus.HOLDING -> DropwayBlueLight
                        DetectionStatus.STEP_BACK -> StatusFailed
                        else -> DropwayPrimary
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isContinuousMode) "โหมดสแกนต่อเนื่อง (Continuous)" else detectionStatus.message,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Flashlight Toggle
            IconButton(
                onClick = {
                    val next = !isFlashOn
                    isFlashOn = next
                    activeCamera?.cameraControl?.enableTorch(next)
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isFlashOn) Color(0xFFFBBF24) else DropwayNavy.copy(alpha = 0.85f))
                    .size(42.dp)
            ) {
                Icon(
                    painter = painterResource(id = if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off),
                    contentDescription = "ไฟฉาย",
                    tint = if (isFlashOn) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Bottom Capture Shutter Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .clickable {
                        AudioFeedback.beepCapture(context)
                        capturePhoto()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            captureExecutor.shutdown()
        }
    }
}

@Composable
fun ScannerReticleOverlay(
    modifier: Modifier = Modifier,
    guideText: String
) {
    val infiniteTransition = rememberInfiniteTransition()
    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val frameWidth = canvasWidth * 0.82f
            val frameHeight = canvasHeight * 0.45f
            val left = (canvasWidth - frameWidth) / 2f
            val top = (canvasHeight - frameHeight) / 2.3f
            val right = left + frameWidth
            val bottom = top + frameHeight

            // Dark semi-transparent scrim around frame
            drawRect(Color(0x77000000), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(canvasWidth, top))
            drawRect(Color(0x77000000), topLeft = Offset(0f, bottom), size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight - bottom))
            drawRect(Color(0x77000000), topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(left, frameHeight))
            drawRect(Color(0x77000000), topLeft = Offset(right, top), size = androidx.compose.ui.geometry.Size(canvasWidth - right, frameHeight))

            // Rounded frame border
            drawRoundRect(
                color = Color.White.copy(alpha = 0.4f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(frameWidth, frameHeight),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // Four corner accent brackets
            val cornerLength = 26.dp.toPx()
            val cornerStroke = 4.dp.toPx()
            val bracketColor = Color(0xFF38BDF8)

            // Top-left
            drawLine(bracketColor, Offset(left - 2, top + cornerLength), Offset(left - 2, top), cornerStroke)
            drawLine(bracketColor, Offset(left - 2, top), Offset(left + cornerLength, top), cornerStroke)

            // Top-right
            drawLine(bracketColor, Offset(right + 2 - cornerLength, top), Offset(right + 2, top), cornerStroke)
            drawLine(bracketColor, Offset(right + 2, top), Offset(right + 2, top + cornerLength), cornerStroke)

            // Bottom-left
            drawLine(bracketColor, Offset(left - 2, bottom - cornerLength), Offset(left - 2, bottom), cornerStroke)
            drawLine(bracketColor, Offset(left - 2, bottom), Offset(left + cornerLength, bottom), cornerStroke)

            // Bottom-right
            drawLine(bracketColor, Offset(right + 2 - cornerLength, bottom), Offset(right + 2, bottom), cornerStroke)
            drawLine(bracketColor, Offset(right + 2, bottom), Offset(right + 2, bottom - cornerLength), cornerStroke)

            // Animated Laser Scan Line
            val scanLineY = top + (frameHeight * scanLineProgress)
            drawLine(
                color = Color(0xFF0284C7),
                start = Offset(left + 8.dp.toPx(), scanLineY),
                end = Offset(right - 8.dp.toPx(), scanLineY),
                strokeWidth = 2.5.dp.toPx()
            )
        }

        // Centered Guide Text
        Text(
            text = guideText,
            color = Color(0xFFF8FAFC),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 130.dp)
                .background(Color(0x99000000), shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
