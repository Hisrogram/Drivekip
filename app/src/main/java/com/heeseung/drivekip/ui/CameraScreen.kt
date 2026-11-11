package com.heeseung.drivekip.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.heeseung.drivekip.data.DriveItem
import com.heeseung.drivekip.data.GoogleDriveService
import com.heeseung.drivekip.ui.theme.GreenPrimary
import com.heeseung.drivekip.util.WatermarkUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

enum class CameraMode {
    PHOTO, VIDEO
}

enum class CameraRatio(val label: String, val aspectRatio: Int) {
    RATIO_4_3("4:3", AspectRatio.RATIO_4_3),
    RATIO_16_9("16:9", AspectRatio.RATIO_16_9)
}

enum class PhotoQuality(val label: String, val jpegQuality: Int) {
    ORIGINAL("최고화질 (100%)", 100),
    HIGH("고화질 (90%)", 90),
    STANDARD("표준 (80%)", 80),
    SAVER("용량절약 (65%)", 65)
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun CameraScreen(
    currentProjectName: String,
    projectList: List<String>,
    onSelectProject: (String) -> Unit,
    onCreateProject: (String) -> Unit,
    driveService: GoogleDriveService,
    onNavigateToGallery: (DriveItem) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var selectedMode by remember { mutableStateOf(CameraMode.PHOTO) }
    var isWatermarkEnabled by remember { mutableStateOf(true) }
    var isGridEnabled by remember { mutableStateOf(true) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var currentRatio by remember { mutableStateOf(CameraRatio.RATIO_4_3) }
    var currentQuality by remember { mutableStateOf(PhotoQuality.HIGH) }

    var currentZoom by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(10f) }
    var minZoomRatio by remember { mutableFloatStateOf(1f) }

    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableIntStateOf(0) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    // Upload & Progress states
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var uploadStatusText by remember { mutableStateOf("") }

    var projectDropdownExpanded by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var newProjectNameInput by remember { mutableStateOf("") }
    var showQualityDialog by remember { mutableStateOf(false) }

    // Galaxy Native Camera Capture Launcher
    var nativeTempFile by remember { mutableStateOf<File?>(null) }
    val nativeCameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val file = nativeTempFile
        if (success && file != null && file.exists()) {
            scope.launch {
                isUploading = true
                uploadProgress = 0.3f
                uploadStatusText = "기본 카메라 촬영 사진 처리 중..."
                withContext(Dispatchers.IO) {
                    try {
                        if (isWatermarkEnabled) {
                            val bmp = BitmapFactory.decodeFile(file.absolutePath)
                            if (bmp != null) {
                                val watermarked = WatermarkUtil.addWatermark(bmp, currentProjectName)
                                FileOutputStream(file).use { out ->
                                    watermarked.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                }
                            }
                        }

                        val folderId = driveService.getOrCreateProjectFolder(currentProjectName)
                        withContext(Dispatchers.Main) {
                            uploadProgress = 0.7f
                            uploadStatusText = "구글 드라이브 업로드 중..."
                        }
                        driveService.uploadMediaFile(folderId, file, "image/jpeg")

                        if (file.exists()) {
                            file.delete()
                        }

                        withContext(Dispatchers.Main) {
                            isUploading = false
                            uploadProgress = 1.0f
                            Toast.makeText(context, "✓ [$currentProjectName] 기본 카메라 사진 업로드 완료! (로컬 정리됨)", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            isUploading = false
                            Toast.makeText(context, "업로드 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // CameraX references
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var videoCapture: VideoCapture<Recorder>? by remember { mutableStateOf(null) }
    var camera: Camera? by remember { mutableStateOf(null) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }

    fun rebindCamera(provider: ProcessCameraProvider, pView: PreviewView) {
        val preview = Preview.Builder()
            .setTargetAspectRatio(currentRatio.aspectRatio)
            .build().also {
                it.setSurfaceProvider(pView.surfaceProvider)
            }

        val capture = ImageCapture.Builder()
            .setFlashMode(flashMode)
            .setTargetAspectRatio(currentRatio.aspectRatio)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        imageCapture = capture

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
        val vCapture = VideoCapture.withOutput(recorder)
        videoCapture = vCapture

        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            provider.unbindAll()
            val cam = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                capture,
                vCapture
            )
            camera = cam

            cam.cameraInfo.zoomState.observe(lifecycleOwner) { state ->
                state?.let {
                    minZoomRatio = it.minZoomRatio
                    maxZoomRatio = it.maxZoomRatio
                    currentZoom = it.zoomRatio
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "카메라 초기화 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // Dynamic rebind on camera flip or ratio switch
    LaunchedEffect(lensFacing, currentRatio) {
        val provider = cameraProvider
        val pv = previewView
        if (provider != null && pv != null) {
            rebindCamera(provider, pv)
        }
    }

    // Video recording timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordingDuration++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        previewView?.let { pv ->
                            val factory = pv.meteringPointFactory
                            val point = factory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(point).build()
                            camera?.cameraControl?.startFocusAndMetering(action)
                        }
                    }
                },
            factory = { ctx ->
                PreviewView(ctx).apply {
                    previewView = this
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider
                        rebindCamera(provider, this)

                        // Smooth pinch-to-zoom
                        val scaleGestureDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScale(detector: ScaleGestureDetector): Boolean {
                                val cam = camera ?: return false
                                val zoomState = cam.cameraInfo.zoomState.value ?: return false
                                val current = zoomState.zoomRatio
                                val delta = detector.scaleFactor
                                val target = (current * delta).coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                                cam.cameraControl.setZoomRatio(target)
                                currentZoom = target
                                return true
                            }
                        })

                        this.setOnTouchListener { _, event ->
                            scaleGestureDetector.onTouchEvent(event)
                            false
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            }
        )

        // 3x3 Grid Overlay
        if (isGridEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 1.5f
                val color = Color.White.copy(alpha = 0.35f)
                val w = size.width
                val h = size.height

                drawLine(color, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth)
                drawLine(color, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth)
                drawLine(color, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth)
                drawLine(color, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth)
            }
        }

        // Top Control Bar (Single-line, horizontally scrollable)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = Color.Black.copy(alpha = 0.65f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Project Selector Chip (Only real projects, or "+신규프로젝트 생성")
                Box {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { projectDropdownExpanded = true },
                        color = GreenPrimary.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (currentProjectName.isNotBlank()) "📁 $currentProjectName ▼" else "📁 프로젝트 선택 ▼",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = projectDropdownExpanded,
                        onDismissRequest = { projectDropdownExpanded = false }
                    ) {
                        if (projectList.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("+ 신규 프로젝트 생성", color = GreenPrimary, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    projectDropdownExpanded = false
                                    newProjectNameInput = ""
                                    showNewProjectDialog = true
                                }
                            )
                        } else {
                            projectList.forEach { proj ->
                                DropdownMenuItem(
                                    text = { Text(proj) },
                                    onClick = {
                                        onSelectProject(proj)
                                        projectDropdownExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("+ 신규 프로젝트 생성", color = GreenPrimary, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    projectDropdownExpanded = false
                                    newProjectNameInput = ""
                                    showNewProjectDialog = true
                                }
                            )
                        }
                    }
                }

                // 2. Compact Galaxy Native Camera Button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (currentProjectName.isBlank()) {
                                Toast.makeText(context, "프로젝트를 먼저 선택해 주세요.", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())
                            val fName = "IMG_${currentProjectName}_${timeStamp}.jpg"
                            val temp = File(context.cacheDir, fName)
                            nativeTempFile = temp
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "com.heeseung.drivekip.fileprovider",
                                temp
                            )
                            nativeCameraLauncher.launch(uri)
                        },
                    color = Color(0xFF2E7D32).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            Icons.Default.ElectricBolt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "기본카메라",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 3. Flash Toggle
                IconButton(
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                            else -> ImageCapture.FLASH_MODE_AUTO
                        }
                        imageCapture?.flashMode = flashMode
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    val icon = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    }
                    Icon(icon, contentDescription = "플래시", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                // 4. Aspect Ratio Toggle (4:3 / 16:9)
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            currentRatio = if (currentRatio == CameraRatio.RATIO_4_3) {
                                CameraRatio.RATIO_16_9
                            } else {
                                CameraRatio.RATIO_4_3
                            }
                        },
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = currentRatio.label,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }

                // 5. Quality Selector
                IconButton(
                    onClick = { showQualityDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.HighQuality, contentDescription = "화질", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                // 6. Watermark Toggle
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isWatermarkEnabled = !isWatermarkEnabled },
                    color = if (isWatermarkEnabled) GreenPrimary else Color.Gray.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isWatermarkEnabled) "워터마크 ON" else "워터마크 OFF",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                // 7. Grid Toggle
                IconButton(
                    onClick = { isGridEnabled = !isGridEnabled },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.GridOn,
                        contentDescription = "격자",
                        tint = if (isGridEnabled) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Uploading Progress Indicator
        if (isUploading) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 20.dp, end = 20.dp)
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = uploadStatusText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = GreenPrimary,
                        trackColor = Color.DarkGray
                    )
                }
            }
        }

        // Zoom Controls (Full Galaxy Range support up to 100x)
        val zoomLevels = remember(minZoomRatio, maxZoomRatio) {
            val list = mutableListOf<Float>()
            if (minZoomRatio <= 0.65f) list.add(0.6f)
            list.add(1.0f)
            if (maxZoomRatio >= 2.0f) list.add(2.0f)
            if (maxZoomRatio >= 5.0f) list.add(5.0f)
            if (maxZoomRatio >= 10.0f) list.add(10.0f)
            if (maxZoomRatio >= 30.0f) list.add(30.0f)
            if (maxZoomRatio >= 100.0f) list.add(100.0f)
            else if (maxZoomRatio > 30.0f) list.add(maxZoomRatio)
            list
        }

        // Bottom Controls (Consolidated: Zoom presets, Mode selector, Shutter vertically stacked without overlapping)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(bottom = 24.dp, top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRecording) {
                val mins = recordingDuration / 60
                val secs = recordingDuration % 60
                Text(
                    text = String.format(Locale.KOREA, "● REC %02d:%02d", mins, secs),
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Quick Preset Zoom Buttons (Clean & Centered, No Slider)
            val closestZoom = zoomLevels.minByOrNull { kotlin.math.abs(it - currentZoom) }
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                zoomLevels.forEach { zoom ->
                    val isSelected = (zoom == closestZoom) && kotlin.math.abs(currentZoom - zoom) < (if (zoom >= 30f) 5f else 0.2f)
                    Surface(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                camera?.cameraControl?.setZoomRatio(zoom)
                                currentZoom = zoom
                            },
                        color = if (isSelected) GreenPrimary else Color.Transparent,
                        shape = CircleShape
                    ) {
                        Text(
                            text = if (zoom == 0.6f) "0.6x" else "${zoom.toInt()}x",
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode Selector: [사진 | 동영상]
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = "사진",
                    color = if (selectedMode == CameraMode.PHOTO) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable {
                        if (!isRecording) selectedMode = CameraMode.PHOTO
                    }
                )
                Text(
                    text = "동영상",
                    color = if (selectedMode == CameraMode.VIDEO) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable {
                        if (!isRecording) selectedMode = CameraMode.VIDEO
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Shutter & Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Storage / Gallery Button
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray.copy(alpha = 0.8f))
                        .clickable {
                            if (currentProjectName.isBlank()) {
                                Toast.makeText(context, "선택된 프로젝트가 없습니다.", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            scope.launch {
                                try {
                                    val folderId = driveService.getOrCreateProjectFolder(currentProjectName)
                                    val item = DriveItem(
                                        id = folderId,
                                        name = currentProjectName,
                                        mimeType = "application/vnd.google-apps.folder"
                                    )
                                    onNavigateToGallery(item)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "보관함 이동 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "보관함", tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("보관함", color = Color.White, fontSize = 9.sp)
                    }
                }

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Color.Red else Color.White)
                        .clickable {
                            if (currentProjectName.isBlank()) {
                                Toast.makeText(context, "프로젝트를 먼저 생성하거나 선택해 주세요.", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }

                            if (selectedMode == CameraMode.PHOTO) {
                                takePhoto(
                                    context = context,
                                    imageCapture = imageCapture,
                                    cameraExecutor = cameraExecutor,
                                    currentProjectName = currentProjectName,
                                    isWatermarkEnabled = isWatermarkEnabled,
                                    quality = currentQuality,
                                    driveService = driveService,
                                    onUploadProgress = { prog, msg ->
                                        isUploading = true
                                        uploadProgress = prog
                                        uploadStatusText = msg
                                    },
                                    onUploadFinish = { success, msg ->
                                        isUploading = false
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                if (isRecording) {
                                    activeRecording?.stop()
                                    activeRecording = null
                                    isRecording = false
                                } else {
                                    startVideoRecording(
                                        context = context,
                                        videoCapture = videoCapture,
                                        currentProjectName = currentProjectName,
                                        driveService = driveService,
                                        onRecordStarted = { rec ->
                                            activeRecording = rec
                                            isRecording = true
                                        },
                                        onUploadProgress = { prog, msg ->
                                            isUploading = true
                                            uploadProgress = prog
                                            uploadStatusText = msg
                                        },
                                        onUploadFinish = { success, msg ->
                                            isUploading = false
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.White, RoundedCornerShape(4.dp))
                        )
                    }
                }

                // Lens Flip Button (Front / Back switch)
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        Icons.Default.FlipCameraAndroid,
                        contentDescription = "카메라 전환",
                        tint = Color.White
                    )
                }
            }
        }
    }

    // New Project Dialog (Cleaned up: No "(예: 강남현장)")
    if (showNewProjectDialog) {
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = { Text("새 프로젝트 생성") },
            text = {
                Column {
                    Text(
                        "구글 드라이브 work/ 아래에 프로젝트 폴더를 생성합니다.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newProjectNameInput,
                        onValueChange = { newProjectNameInput = it },
                        label = { Text("프로젝트명") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newProjectNameInput.trim()
                        if (name.isNotEmpty()) {
                            showNewProjectDialog = false
                            onCreateProject(name)
                            onSelectProject(name)
                            Toast.makeText(context, "새 프로젝트 '$name' 생성 중...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("생성")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // Quality Selection Dialog
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("사진 촬영 화질 선택") },
            text = {
                Column {
                    PhotoQuality.values().forEach { q ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentQuality = q
                                    showQualityDialog = false
                                    Toast.makeText(context, "화질이 ${q.label}로 설정되었습니다.", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = q.label,
                                fontWeight = if (currentQuality == q) FontWeight.Bold else FontWeight.Normal,
                                color = if (currentQuality == q) GreenPrimary else Color.Unspecified,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    cameraExecutor: java.util.concurrent.Executor,
    currentProjectName: String,
    isWatermarkEnabled: Boolean,
    quality: PhotoQuality,
    driveService: GoogleDriveService,
    onUploadProgress: (Float, String) -> Unit,
    onUploadFinish: (Boolean, String) -> Unit
) {
    if (imageCapture == null) return

    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())
    val fileName = "IMG_${currentProjectName}_${timeStamp}.jpg"
    val tempFile = File(context.cacheDir, fileName)

    imageCapture.takePicture(
        cameraExecutor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                var bitmap = imageProxyToBitmap(image)
                image.close()

                if (isWatermarkEnabled) {
                    bitmap = WatermarkUtil.addWatermark(bitmap, currentProjectName)
                }

                FileOutputStream(tempFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, out)
                }

                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    onUploadProgress(0.3f, "구글 드라이브 업로드 준비 중...")
                    withContext(Dispatchers.IO) {
                        try {
                            val folderId = driveService.getOrCreateProjectFolder(currentProjectName)
                            withContext(Dispatchers.Main) {
                                onUploadProgress(0.6f, "구글 드라이브 전송 중...")
                            }
                            driveService.uploadMediaFile(folderId, tempFile, "image/jpeg")

                            // Delete local file to save storage
                            if (tempFile.exists()) {
                                tempFile.delete()
                            }

                            withContext(Dispatchers.Main) {
                                onUploadProgress(1.0f, "업로드 완료!")
                                onUploadFinish(true, "✓ [$currentProjectName] 구글 드라이브 저장 완료! (로컬 원본 정리됨)")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                onUploadFinish(false, "업로드 실패: ${e.localizedMessage}")
                            }
                        }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
                ContextCompat.getMainExecutor(context).execute {
                    onUploadFinish(false, "촬영 오류: ${exception.localizedMessage}")
                }
            }
        }
    )
}

private fun startVideoRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>?,
    currentProjectName: String,
    driveService: GoogleDriveService,
    onRecordStarted: (Recording) -> Unit,
    onUploadProgress: (Float, String) -> Unit,
    onUploadFinish: (Boolean, String) -> Unit
) {
    if (videoCapture == null) return

    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())
    val fileName = "VID_${currentProjectName}_${timeStamp}.mp4"
    val tempFile = File(context.cacheDir, fileName)

    val outputOptions = FileOutputOptions.Builder(tempFile).build()

    @SuppressLint("MissingPermission")
    val recording = videoCapture.output
        .prepareRecording(context, outputOptions)
        .withAudioEnabled()
        .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
            when (recordEvent) {
                is VideoRecordEvent.Finalize -> {
                    if (!recordEvent.hasError()) {
                        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                            onUploadProgress(0.2f, "동영상 업로드 준비 중...")
                            withContext(Dispatchers.IO) {
                                try {
                                    val folderId = driveService.getOrCreateProjectFolder(currentProjectName)
                                    withContext(Dispatchers.Main) {
                                        onUploadProgress(0.6f, "동영상 구글 드라이브 전송 중...")
                                    }
                                    driveService.uploadMediaFile(folderId, tempFile, "video/mp4")

                                    if (tempFile.exists()) {
                                        tempFile.delete()
                                    }

                                    withContext(Dispatchers.Main) {
                                        onUploadProgress(1.0f, "업로드 완료!")
                                        onUploadFinish(true, "✓ [$currentProjectName] 동영상 저장 완료! (로컬 정리됨)")
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        onUploadFinish(false, "동영상 업로드 실패: ${e.localizedMessage}")
                                    }
                                }
                            }
                        }
                    } else {
                        onUploadFinish(false, "녹화 에러: ${recordEvent.error}")
                    }
                }
            }
        }

    onRecordStarted(recording)
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    val rotation = image.imageInfo.rotationDegrees
    return if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    } else {
        original
    }
}
