package com.heeseung.drivekip

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.heeseung.drivekip.data.AppSettings
import com.heeseung.drivekip.data.AppUpdateInfo
import com.heeseung.drivekip.data.AppUpdateManager
import com.heeseung.drivekip.data.AuthManager
import com.heeseung.drivekip.data.CameraPreference
import com.heeseung.drivekip.data.DriveItem
import com.heeseung.drivekip.data.GoogleDriveService
import com.heeseung.drivekip.ui.CameraChoiceBottomSheet
import com.heeseung.drivekip.ui.CameraScreen
import com.heeseung.drivekip.ui.GalleryAllScreen
import com.heeseung.drivekip.ui.HomeScreen
import com.heeseung.drivekip.ui.MediaViewerScreen
import com.heeseung.drivekip.ui.ProjectGalleryScreen
import com.heeseung.drivekip.ui.ProjectListScreen
import com.heeseung.drivekip.ui.SettingsScreen
import com.heeseung.drivekip.ui.theme.DriveKipTheme
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

enum class BottomTab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Default.Home),
    CAMERA("촬영", Icons.Default.CameraAlt),
    PROJECTS("프로젝트", Icons.Default.Folder),
    GALLERY("갤러리", Icons.Default.PhotoLibrary),
    SETTINGS("설정", Icons.Default.Settings)
}

sealed class SubScreen {
    data object None : SubScreen()
    data class ProjectGallery(val project: DriveItem) : SubScreen()
    data class MediaViewer(val items: List<DriveItem>, val initialIndex: Int, val parent: DriveItem? = null) : SubScreen()
}

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var driveService: GoogleDriveService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = AuthManager(this)
        driveService = GoogleDriveService(
            tokenProvider = { authManager.accessToken },
            tokenRefresher = { authManager.refreshCurrentToken() }
        )

        setContent {
            DriveKipTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DriveKipApp(
                        authManager = authManager,
                        driveService = driveService
                    )
                }
            }
        }
    }
}

@Composable
fun DriveKipApp(
    authManager: AuthManager,
    driveService: GoogleDriveService
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val appSettings = remember { AppSettings(context) }
    val appUpdateManager = remember { AppUpdateManager() }

    var currentTab by remember { mutableStateOf(BottomTab.HOME) }
    var subScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }

    var selectedCameraProject by remember { mutableStateOf("") }
    var realProjectNames by remember { mutableStateOf<List<String>>(emptyList()) }

    // Camera routing states
    var showCameraChoiceBottomSheet by remember { mutableStateOf(false) }
    var pendingCameraProject by remember { mutableStateOf("") }

    // Global Native Camera Capture states
    var globalNativeTempFile by remember { mutableStateOf<File?>(null) }
    var isGlobalUploading by remember { mutableStateOf(false) }
    var globalUploadProgress by remember { mutableFloatStateOf(0f) }
    var globalUploadMessage by remember { mutableStateOf("") }

    // In-App Auto Update states
    var updateInfoToPrompt by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadUpdateProgress by remember { mutableFloatStateOf(0f) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var showPermissionPromptDialog by remember { mutableStateOf(false) }

    val globalNativeCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = globalNativeTempFile
        val projName = pendingCameraProject.ifBlank { selectedCameraProject }
        if (success && file != null && file.exists()) {
            scope.launch {
                isGlobalUploading = true
                globalUploadProgress = 0.25f
                globalUploadMessage = "기본 카메라 촬영 사진 처리 중..."
                withContext(Dispatchers.IO) {
                    try {
                        if (appSettings.isDefaultWatermark) {
                            val bmp = BitmapFactory.decodeFile(file.absolutePath)
                            if (bmp != null) {
                                val watermarked = WatermarkUtil.addWatermark(bmp, projName)
                                FileOutputStream(file).use { out ->
                                    watermarked.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                }
                            }
                        }

                        val folderId = driveService.getOrCreateProjectFolder(projName)
                        withContext(Dispatchers.Main) {
                            globalUploadProgress = 0.65f
                            globalUploadMessage = "구글 드라이브 업로드 중..."
                        }
                        driveService.uploadMediaFile(folderId, file, "image/jpeg")

                        if (file.exists()) file.delete()

                        withContext(Dispatchers.Main) {
                            isGlobalUploading = false
                            globalUploadProgress = 1.0f
                            Toast.makeText(context, "✓ [$projName] 기본 카메라 사진 업로드 완료! (로컬 정리됨)", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            isGlobalUploading = false
                            Toast.makeText(context, "업로드 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    fun launchGlobalNativeCamera(projectName: String) {
        if (projectName.isBlank()) {
            Toast.makeText(context, "프로젝트를 먼저 생성하거나 선택해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())
        val fName = "IMG_${projectName}_${timeStamp}.jpg"
        val temp = File(context.cacheDir, fName)
        globalNativeTempFile = temp
        val uri = FileProvider.getUriForFile(
            context,
            "com.heeseung.drivekip.fileprovider",
            temp
        )
        globalNativeCameraLauncher.launch(uri)
    }

    fun triggerCameraAction(projectName: String) {
        val targetProject = projectName.ifBlank { selectedCameraProject }
        if (targetProject.isNotBlank()) selectedCameraProject = targetProject
        pendingCameraProject = selectedCameraProject

        when (appSettings.cameraPreference) {
            CameraPreference.NATIVE_APP -> {
                launchGlobalNativeCamera(selectedCameraProject)
            }
            CameraPreference.CAMERAX -> {
                subScreen = SubScreen.None
                currentTab = BottomTab.CAMERA
            }
            CameraPreference.ASK_EVERY_TIME -> {
                showCameraChoiceBottomSheet = true
            }
        }
    }

    fun refreshProjectsList() {
        if (!authManager.isSignedIn) return
        scope.launch {
            try {
                val list = driveService.listProjects()
                realProjectNames = list.map { it.name }
                if (selectedCameraProject.isBlank() && realProjectNames.isNotEmpty()) {
                    selectedCameraProject = realProjectNames.first()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun performUpdateCheck(isManual: Boolean = false) {
        if (!authManager.isSignedIn) {
            if (isManual) Toast.makeText(context, "구글 로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            try {
                val info = appUpdateManager.checkForUpdate(
                    driveService = driveService,
                    appSettings = appSettings,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    currentVersionName = BuildConfig.VERSION_NAME
                )
                if (info.hasUpdate) {
                    updateInfoToPrompt = info
                } else if (isManual) {
                    Toast.makeText(context, "현재 최신 버전(v${BuildConfig.VERSION_NAME})을 사용 중입니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (isManual) Toast.makeText(context, "업데이트 확인 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(authManager.isSignedIn) {
        if (authManager.isSignedIn) {
            refreshProjectsList()
            performUpdateCheck(isManual = false)
        }
    }

    // Auto-resume installation if returned from unknown app sources permission setting
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val apk = downloadedApkFile
                if (apk != null && apk.exists() && appUpdateManager.canInstallPackages(context)) {
                    downloadedApkFile = null
                    appUpdateManager.triggerApkInstallation(context, apk)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.result
            if (account != null) {
                scope.launch {
                    val token = authManager.handleSignInResult(account)
                    if (token != null) {
                        Toast.makeText(context, "구글 드라이브 연동에 성공했습니다!", Toast.LENGTH_SHORT).show()
                        refreshProjectsList()
                        performUpdateCheck(isManual = false)
                    } else {
                        Toast.makeText(context, "로그인 토큰 획득에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "로그인 오류: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val cam = perms[Manifest.permission.CAMERA] ?: false
        if (!cam) {
            Toast.makeText(context, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    if (subScreen !is SubScreen.None) {
        BackHandler {
            subScreen = when (val s = subScreen) {
                is SubScreen.MediaViewer -> s.parent?.let { SubScreen.ProjectGallery(it) } ?: SubScreen.None
                else -> SubScreen.None
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (subScreen is SubScreen.None && currentTab != BottomTab.CAMERA) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    BottomTab.values().forEach { tab ->
                        val selected = currentTab == tab
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 11.sp) },
                            selected = selected,
                            onClick = {
                                subScreen = SubScreen.None
                                if (tab == BottomTab.CAMERA) {
                                    triggerCameraAction(selectedCameraProject)
                                } else {
                                    currentTab = tab
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GreenPrimary,
                                selectedTextColor = GreenPrimary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val contentModifier = if (currentTab == BottomTab.CAMERA || subScreen !is SubScreen.None) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxSize().padding(innerPadding)
        }

        Box(modifier = contentModifier) {
            when (val s = subScreen) {
                is SubScreen.ProjectGallery -> {
                    ProjectGalleryScreen(
                        project = s.project,
                        driveService = driveService,
                        onNavigateBack = { subScreen = SubScreen.None },
                        onOpenCamera = { projName ->
                            triggerCameraAction(projName)
                        },
                        onOpenMediaViewer = { list, idx ->
                            subScreen = SubScreen.MediaViewer(list, idx, s.project)
                        }
                    )
                }

                is SubScreen.MediaViewer -> {
                    MediaViewerScreen(
                        items = s.items,
                        initialIndex = s.initialIndex,
                        driveService = driveService,
                        onNavigateBack = {
                            subScreen = s.parent?.let { SubScreen.ProjectGallery(it) } ?: SubScreen.None
                        },
                        onDeleted = {
                            subScreen = s.parent?.let { SubScreen.ProjectGallery(it) } ?: SubScreen.None
                        }
                    )
                }

                is SubScreen.None -> {
                    when (currentTab) {
                        BottomTab.HOME -> {
                            HomeScreen(
                                authManager = authManager,
                                driveService = driveService,
                                onNavigateToCamera = { proj ->
                                    triggerCameraAction(proj)
                                },
                                onNavigateToProjects = { currentTab = BottomTab.PROJECTS },
                                onNavigateToGallery = { currentTab = BottomTab.GALLERY },
                                onOpenMediaViewer = { item, parent ->
                                    subScreen = SubScreen.MediaViewer(listOf(item), 0, parent)
                                }
                            )
                        }

                        BottomTab.CAMERA -> {
                            BackHandler {
                                currentTab = BottomTab.HOME
                            }
                            CameraScreen(
                                currentProjectName = selectedCameraProject,
                                projectList = realProjectNames,
                                onSelectProject = { selectedCameraProject = it },
                                onCreateProject = { newName ->
                                    scope.launch {
                                        try {
                                            driveService.getOrCreateProjectFolder(newName)
                                            refreshProjectsList()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "생성 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                driveService = driveService,
                                onNavigateToGallery = { projectItem ->
                                    subScreen = SubScreen.ProjectGallery(projectItem)
                                }
                            )
                        }

                        BottomTab.PROJECTS -> {
                            ProjectListScreen(
                                driveService = driveService,
                                isSignedIn = authManager.isSignedIn,
                                onOpenProjectGallery = { projectItem ->
                                    subScreen = SubScreen.ProjectGallery(projectItem)
                                },
                                onOpenCameraForProject = { projName ->
                                    triggerCameraAction(projName)
                                }
                            )
                        }

                        BottomTab.GALLERY -> {
                            GalleryAllScreen(
                                driveService = driveService,
                                isSignedIn = authManager.isSignedIn,
                                onOpenMediaViewer = { list, idx ->
                                    subScreen = SubScreen.MediaViewer(list, idx, null)
                                }
                            )
                        }

                        BottomTab.SETTINGS -> {
                            SettingsScreen(
                                authManager = authManager,
                                appSettings = appSettings,
                                onSignInClick = {
                                    googleSignInLauncher.launch(authManager.googleSignInClient.signInIntent)
                                },
                                onSignOutClick = {
                                    authManager.signOut {
                                        Toast.makeText(context, "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()
                                        realProjectNames = emptyList()
                                    }
                                },
                                onCheckUpdateClick = {
                                    performUpdateCheck(isManual = true)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Camera Choice BottomSheet (User Requested)
        if (showCameraChoiceBottomSheet) {
            CameraChoiceBottomSheet(
                projectName = pendingCameraProject.ifBlank { selectedCameraProject },
                onDismissRequest = { showCameraChoiceBottomSheet = false },
                onSelectNativeCamera = {
                    showCameraChoiceBottomSheet = false
                    launchGlobalNativeCamera(pendingCameraProject.ifBlank { selectedCameraProject })
                },
                onSelectInAppCamera = {
                    showCameraChoiceBottomSheet = false
                    subScreen = SubScreen.None
                    currentTab = BottomTab.CAMERA
                }
            )
        }

        // Global Native Camera Uploading Dialog
        if (isGlobalUploading) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("구글 드라이브 업로드 중") },
                text = {
                    Column {
                        Text(globalUploadMessage, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { globalUploadProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = GreenPrimary
                        )
                    }
                },
                confirmButton = {}
            )
        }

        // In-App Auto Update Prompt Dialog
        updateInfoToPrompt?.let { update ->
            AlertDialog(
                onDismissRequest = { updateInfoToPrompt = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("새 버전 업데이트 안내")
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "DriveKip v${update.latestVersionName} 버전이 출시되었습니다!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = update.releaseNotes,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "지금 원클릭으로 다운로드하고 업데이트하시겠습니까?",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                isDownloadingUpdate = true
                                downloadUpdateProgress = 0f
                                try {
                                    val apk = appUpdateManager.downloadUpdate(
                                        context = context,
                                        updateInfo = update,
                                        driveService = driveService,
                                        onProgress = { p -> downloadUpdateProgress = p }
                                    )
                                    isDownloadingUpdate = false
                                    updateInfoToPrompt = null
                                    downloadedApkFile = apk

                                    if (!appUpdateManager.canInstallPackages(context)) {
                                        showPermissionPromptDialog = true
                                    } else {
                                        appUpdateManager.triggerApkInstallation(context, apk)
                                    }
                                } catch (e: Exception) {
                                    isDownloadingUpdate = false
                                    Toast.makeText(context, "다운로드 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                    ) {
                        Text("지금 업데이트")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { updateInfoToPrompt = null }) {
                        Text("나중에")
                    }
                }
            )
        }

        // Update Downloading Dialog
        if (isDownloadingUpdate) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("업데이트 다운로드 중...") },
                text = {
                    Column {
                        Text(
                            text = "${(downloadUpdateProgress * 100).toInt()}% 다운로드 완료",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { downloadUpdateProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = GreenPrimary
                        )
                    }
                },
                confirmButton = {}
            )
        }

        // Install Unknown App Permission Prompt
        if (showPermissionPromptDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionPromptDialog = false },
                title = { Text("출처를 알 수 없는 앱 설치 권한") },
                text = {
                    Text(
                        "앱 자동 업데이트 설치를 위해 '출처를 알 수 없는 앱 설치' 권한 허용이 필요합니다. 설정 화면으로 이동하시겠습니까?",
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionPromptDialog = false
                            appUpdateManager.requestInstallPermission(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                    ) {
                        Text("설정으로 이동")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionPromptDialog = false }) {
                        Text("취소")
                    }
                }
            )
        }
    }
}
