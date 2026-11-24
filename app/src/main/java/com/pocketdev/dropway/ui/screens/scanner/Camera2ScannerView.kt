package com.pocketdev.dropway.ui.screens.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

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
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var cameraController by remember { mutableStateOf<Camera2Controller?>(null) }
    var detectionStatus by remember { mutableStateOf(DetectionStatus.SEARCHING) }

    // Live frame analysis for continuous barcode detection and auto-label detection
    LaunchedEffect(textureViewRef, isContinuousMode, enableAutoLabelDetect) {
        val tv = textureViewRef ?: return@LaunchedEffect
        var prevGray: ByteArray? = null
        var holdStart = 0L
        var stillSince = 0L
        var lastCooldownUntil = 0L

        while (true) {
            delay(if (isContinuousMode) 120L else 180L)
            try {
                if (!tv.isAvailable) continue
                val now = System.currentTimeMillis()
                if (now < lastCooldownUntil) continue

                val ratio = cameraController?.previewAspectRatio ?: (9f / 16f)
                val targetW = 360
                val targetH = (targetW / ratio).toInt().coerceIn(360, 640)
                val bitmap = tv.getBitmap(targetW, targetH) ?: continue

                // Execute all image analysis on background worker thread to keep camera preview silky smooth at 60fps
                var detectedBarcode: BarcodeResult? = null
                var shouldCapture = false
                var updatedStatus: DetectionStatus? = null

                withContext(Dispatchers.Default) {
                    try {
                        // 1. Fast barcode check without heavy multi-orientation retry
                        val barcodeRes = BarcodeDecoder.decodeBitmapFast(bitmap)
                        if (barcodeRes != null) {
                            detectedBarcode = barcodeRes
                            return@withContext
                        }

                        // 2. Auto-label detection for single mode
                        if (enableAutoLabelDetect && !isContinuousMode) {
                            val cropW = (bitmap.width * 0.70f).toInt().coerceAtLeast(10)
                            val cropH = (bitmap.height * 0.70f).toInt().coerceAtLeast(10)
                            val cropX = ((bitmap.width - cropW) / 2).coerceAtLeast(0)
                            val cropY = ((bitmap.height - cropH) / 2).coerceAtLeast(0)
                            val centerCrop = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)

                            val scaleW = 120
                            val scaleH = (scaleW * (cropH.toFloat() / cropW)).toInt().coerceIn(80, 200)
                            val smallBm = Bitmap.createScaledBitmap(centerCrop, scaleW, scaleH, false)
                            centerCrop.recycle()

                            val analysis = AutoLabelDetector.analyzeBitmap(smallBm)
                            smallBm.recycle()

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
                                    updatedStatus = if (tooClose) DetectionStatus.STEP_BACK else DetectionStatus.SEARCHING
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
                                        shouldCapture = true
                                        updatedStatus = DetectionStatus.CAPTURING
                                    } else {
                                        updatedStatus = if (stillFor >= AutoLabelDetector.AUTO_STILL_MS) DetectionStatus.HOLDING else DetectionStatus.SEARCHING
                                    }
                                }
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }

                // Handle results back on Main thread
                if (detectedBarcode != null) {
                    AudioFeedback.beepCapture(context)
                    onBarcodeDetected(detectedBarcode!!)
                    lastCooldownUntil = System.currentTimeMillis() + 1500L
                    continue
                }

                if (shouldCapture) {
                    detectionStatus = DetectionStatus.CAPTURING
                    AudioFeedback.beepCapture(context)
                    val fullBitmap = tv.bitmap
                    if (fullBitmap != null) {
                        onPhotoCaptured(fullBitmap)
                    }
                    lastCooldownUntil = System.currentTimeMillis() + 2000L
                    continue
                }

                if (updatedStatus != null && detectionStatus != updatedStatus) {
                    detectionStatus = updatedStatus!!
                }
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    textureViewRef = this
                    val controller = Camera2Controller(ctx, this)
                    cameraController = controller
                    surfaceTextureListener = controller
                    addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                        val w = right - left
                        val h = bottom - top
                        if (w > 0 && h > 0) {
                            controller.configureTransform(w, h)
                        }
                    }
                }
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
                    cameraController?.setTorchMode(next)
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
                        val tv = textureViewRef
                        if (tv != null && tv.isAvailable) {
                            val captured = tv.bitmap
                            if (captured != null) {
                                AudioFeedback.beepCapture(context)
                                onPhotoCaptured(captured)
                            }
                        }
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
            cameraController?.release()
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

class Camera2Controller(
    private val context: Context,
    private val textureView: TextureView
) : TextureView.SurfaceTextureListener {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    var previewAspectRatio: Float = 9f / 16f
        private set

    private var cameraId: String? = null
    private var previewSize: Size? = null
    private var isTorchEnabled: Boolean = false

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        startBackgroundThread()
        openCamera(width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        configureTransform(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopCamera()
        stopBackgroundThread()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("Camera2Background").apply { start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (_: Exception) {}
    }

    private fun openCamera(viewWidth: Int, viewHeight: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val choices = map?.getOutputSizes(SurfaceTexture::class.java)
                    if (!choices.isNullOrEmpty()) {
                        previewSize = choices.firstOrNull { it.width in 1280..1920 && it.height in 720..1080 }
                            ?: choices.first()
                        previewAspectRatio = previewSize!!.height.toFloat() / previewSize!!.width.toFloat()
                    }
                    break
                }
            }

            val targetId = cameraId ?: return
            cameraManager.openCamera(targetId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreview()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                }
            }, backgroundHandler)
        } catch (_: Exception) {}
    }

    fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val size = previewSize ?: return
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, size.height.toFloat(), size.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = maxOf(
                viewHeight.toFloat() / size.height,
                viewWidth.toFloat() / size.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

    private fun startPreview() {
        val device = cameraDevice ?: return
        val size = previewSize ?: return
        val texture = textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(size.width, size.height)
        val surface = Surface(texture)

        try {
            val previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                if (isTorchEnabled) {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }
            }

            @Suppress("DEPRECATION")
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        session.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
                    } catch (_: Exception) {}
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, backgroundHandler)
        } catch (_: Exception) {}
    }

    fun setTorchMode(enabled: Boolean) {
        isTorchEnabled = enabled
        try {
            val device = cameraDevice ?: return
            val session = captureSession ?: return
            val texture = textureView.surfaceTexture ?: return
            val size = previewSize ?: return
            texture.setDefaultBufferSize(size.width, size.height)
            val surface = Surface(texture)

            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                if (enabled) {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                } else {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
            }.build()

            session.setRepeatingRequest(request, null, backgroundHandler)
        } catch (_: Exception) {}
    }

    fun release() {
        stopCamera()
        stopBackgroundThread()
    }

    private fun stopCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (_: Exception) {}
    }
}
