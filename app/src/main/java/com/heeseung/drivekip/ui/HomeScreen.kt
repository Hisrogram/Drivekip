package com.heeseung.drivekip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.heeseung.drivekip.data.AuthManager
import com.heeseung.drivekip.data.DriveItem
import com.heeseung.drivekip.data.GoogleDriveService
import com.heeseung.drivekip.data.StorageQuotaInfo
import com.heeseung.drivekip.ui.theme.GreenPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    authManager: AuthManager,
    driveService: GoogleDriveService,
    onNavigateToCamera: (String) -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onOpenMediaViewer: (DriveItem, DriveItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var projectCount by remember { mutableStateOf(0) }
    var totalMediaCount by remember { mutableStateOf(0) }
    var quotaInfo by remember { mutableStateOf<StorageQuotaInfo?>(null) }
    var recentPhotos by remember { mutableStateOf<List<Pair<DriveItem, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    fun refreshDashboard() {
        if (!authManager.isSignedIn) return
        isLoading = true
        scope.launch {
            try {
                val projects = driveService.listProjects()
                projectCount = projects.size
                val allMedia = driveService.listAllWorkMediaFiles()
                totalMediaCount = allMedia.size
                recentPhotos = allMedia.take(6)

                // Fetch Google Drive Storage Quota
                quotaInfo = driveService.getStorageQuota()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(authManager.isSignedIn) {
        if (authManager.isSignedIn) refreshDashboard()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(32.dp).clip(CircleShape),
                            color = GreenPrimary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("DriveKip", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                actions = {
                    if (authManager.isSignedIn) {
                        IconButton(onClick = { refreshDashboard() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "구글 드라이브 동기화 상태",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(8.dp).clip(CircleShape),
                                    color = if (authManager.isSignedIn) GreenPrimary else Color.Red
                                ) {}
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (authManager.isSignedIn) "정상 연결됨 (work/)" else "로그인 필요",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        if (authManager.isSignedIn) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = authManager.userEmail?.substringBefore("@") ?: "연결됨",
                                    color = GreenPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats in a row (Projects, Media Count, Drive Remaining Storage)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("프로젝트", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$projectCount", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GreenPrimary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("보관 미디어", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$totalMediaCount", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GreenPrimary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("드라이브 잔여 용량", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = quotaInfo?.formattedFree ?: "조회 중...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = GreenPrimary
                            )
                        }
                    }

                    // Storage Progress Bar
                    quotaInfo?.let { quota ->
                        Spacer(modifier = Modifier.height(14.dp))
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "사용량: ${quota.formattedUsage} / ${quota.formattedTotal}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${quota.usagePercent}% 사용 중",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (quota.usagePercent > 90) Color.Red else GreenPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (quota.usagePercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = if (quota.usagePercent > 90) Color.Red else GreenPrimary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        }
                    }
                }
            }

            // Quick Camera Action Button
            Button(
                onClick = { onNavigateToCamera("") },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("업무 사진/동영상 촬영하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            // Quick Nav Row: Projects / Gallery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f).clickable { onNavigateToProjects() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("프로젝트 목록", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${projectCount}개 폴더", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).clickable { onNavigateToGallery() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("전체 갤러리", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${totalMediaCount}개 사진", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Recent Photos
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("최근 보관된 미디어", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (recentPhotos.isNotEmpty()) {
                        Text(
                            text = "전체보기 >",
                            fontSize = 12.sp,
                            color = GreenPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onNavigateToGallery() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GreenPrimary)
                    }
                } else if (recentPhotos.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("아직 저장된 미디어가 없습니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().height(210.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        userScrollEnabled = false
                    ) {
                        items(recentPhotos) { (item, projName) ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val parent = DriveItem(id = "", name = projName, mimeType = "application/vnd.google-apps.folder")
                                        onOpenMediaViewer(item, parent)
                                    }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(item.thumbnailLink?.replace("=s220", "=s400") ?: item.webViewLink)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}
