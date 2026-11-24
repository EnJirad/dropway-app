package com.pocketdev.dropway.ui.screens.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pocketdev.dropway.R
import com.pocketdev.dropway.core.scanner.BarcodeDecoder
import com.pocketdev.dropway.core.scanner.BarcodeResult
import com.pocketdev.dropway.ui.theme.DropwayPrimary
import com.pocketdev.dropway.ui.theme.StatusDelivered
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Camera2ScannerView(
    modifier: Modifier = Modifier,
    isContinuousMode: Boolean = false,
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
                .background(Color(0xFF0F172A)),
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

    // Live frame analysis for continuous or quick barcode detection
    LaunchedEffect(textureViewRef, isContinuousMode) {
        val tv = textureViewRef ?: return@LaunchedEffect
        while (true) {
            delay(if (isContinuousMode) 220L else 350L)
            try {
                if (tv.isAvailable) {
                    val bitmap = tv.getBitmap(480, 640)
                    if (bitmap != null) {
                        val result = BarcodeDecoder.decodeBitmap(bitmap)
                        if (result != null) {
                            onBarcodeDetected(result)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    textureViewRef = this
                    val controller = Camera2Controller(ctx, this)
                    cameraController = controller
                    surfaceTextureListener = controller
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Scanner Reticle Overlay
        ScannerReticleOverlay(
            modifier = Modifier.fillMaxSize(),
            guideText = overlayGuideText
        )

        // Top Control Bar (Flash and Mode Badge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Badge
            Surface(
                color = if (isContinuousMode) Color(0xFF0284C7) else Color(0xFF1E293B).copy(alpha = 0.85f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isContinuousMode) Color(0xFF38BDF8) else StatusDelivered)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isContinuousMode) "โหมดสแกนต่อเนื่อง (Continuous)" else "โหมดตรวจทีละชิ้น (Single)",
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
                    .background(if (isFlashOn) Color(0xFFFBBF24) else Color(0xFF1E293B).copy(alpha = 0.85f))
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
            // Top rect
            drawRect(Color(0x77000000), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(canvasWidth, top))
            // Bottom rect
            drawRect(Color(0x77000000), topLeft = Offset(0f, bottom), size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight - bottom))
            // Left rect
            drawRect(Color(0x77000000), topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(left, frameHeight))
            // Right rect
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
            val cornerLen = 28.dp.toPx()
            val strokeW = 4.dp.toPx()
            val bracketColor = Color(0xFF38BDF8)

            // Top-left
            drawLine(bracketColor, Offset(left - 2, top), Offset(left + cornerLen, top), strokeW)
            drawLine(bracketColor, Offset(left, top - 2), Offset(left, top + cornerLen), strokeW)

            // Top-right
            drawLine(bracketColor, Offset(right + 2, top), Offset(right - cornerLen, top), strokeW)
            drawLine(bracketColor, Offset(right, top - 2), Offset(right, top + cornerLen), strokeW)

            // Bottom-left
            drawLine(bracketColor, Offset(left - 2, bottom), Offset(left + cornerLen, bottom), strokeW)
            drawLine(bracketColor, Offset(left, bottom + 2), Offset(left, bottom - cornerLen), strokeW)

            // Bottom-right
            drawLine(bracketColor, Offset(right + 2, bottom), Offset(right - cornerLen, bottom), strokeW)
            drawLine(bracketColor, Offset(right, bottom + 2), Offset(right, bottom - cornerLen), strokeW)

            // Animated Laser Scanning Line
            val scanY = top + (frameHeight * scanLineProgress)
            drawLine(
                color = Color(0xFF06B6D4),
                start = Offset(left + 12.dp.toPx(), scanY),
                end = Offset(right - 12.dp.toPx(), scanY),
                strokeWidth = 2.5.dp.toPx()
            )
        }

        // Guide text below scan frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .offset(y = 140.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = guideText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Controller class managing Android Camera2 API lifecycle and operations.
 */
class Camera2Controller(
    private val context: Context,
    private val textureView: TextureView
) : TextureView.SurfaceTextureListener {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var isTorchEnabled = false

    init {
        startBackgroundThread()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("Camera2Background").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (_: InterruptedException) {}
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        openCamera()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            val cameraId = getBackCameraId() ?: return
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreviewSession()
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
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
    }

    private fun createCameraPreviewSession() {
        val device = cameraDevice ?: return
        val texture = textureView.surfaceTexture ?: return

        texture.setDefaultBufferSize(1280, 720)
        val surface = Surface(texture)

        try {
            val previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                if (isTorchEnabled) {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }
            }

            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
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
        val session = captureSession ?: return
        val device = cameraDevice ?: return
        val texture = textureView.surfaceTexture ?: return

        val surface = Surface(texture)
        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.FLASH_MODE, if (enabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
            }
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (_: Exception) {}
    }

    private fun getBackCameraId(): String? {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    fun release() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        stopBackgroundThread()
    }
}
