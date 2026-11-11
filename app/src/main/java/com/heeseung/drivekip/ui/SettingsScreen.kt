package com.heeseung.drivekip.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heeseung.drivekip.data.AppSettings
import com.heeseung.drivekip.data.AuthManager
import com.heeseung.drivekip.data.CameraPreference
import com.heeseung.drivekip.ui.theme.GreenPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authManager: AuthManager,
    appSettings: AppSettings,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onCheckUpdateClick: () -> Unit
) {
    val context = LocalContext.current
    var wifiOnly by remember { mutableStateOf(appSettings.isWifiOnly) }
    var defaultWatermark by remember { mutableStateOf(appSettings.isDefaultWatermark) }
    var currentCameraPref by remember { mutableStateOf(appSettings.cameraPreference) }
    var showCameraDropdown by remember { mutableStateOf(false) }

    var showTokenDialog by remember { mutableStateOf(false) }
    var manualTokenText by remember { mutableStateOf("") }

    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var customUrlInput by remember { mutableStateOf(appSettings.updateCheckUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
            // 1. Account Section
            Text("구글 드라이브 계정", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GreenPrimary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (authManager.isSignedIn) authManager.userEmail ?: "연동됨" else "로그인 필요",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "루트 폴더: Google Drive > work/",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (authManager.isSignedIn) {
                            OutlinedButton(onClick = onSignOutClick) {
                                Text("로그아웃", fontSize = 12.sp)
                            }
                        } else {
                            Button(onClick = onSignInClick, colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) {
                                Text("로그인", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showTokenDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("OAuth 토큰 직접 설정", fontSize = 12.sp)
                    }
                }
            }

            // 2. Camera Settings (User Requested)
            Text("촬영 카메라 설정", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GreenPrimary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("촬영 카메라 기본 모드", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("촬영 실행 시 열릴 카메라 방식을 지정합니다.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dropdown selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showCameraDropdown = true },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentCameraPref.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = GreenPrimary
                                    )
                                    Text(
                                        text = currentCameraPref.description,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = GreenPrimary)
                            }
                        }

                        DropdownMenu(
                            expanded = showCameraDropdown,
                            onDismissRequest = { showCameraDropdown = false }
                        ) {
                            CameraPreference.values().forEach { pref ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(pref.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(pref.description, fontSize = 11.sp, color = Color.Gray)
                                        }
                                    },
                                    onClick = {
                                        currentCameraPref = pref
                                        appSettings.cameraPreference = pref
                                        showCameraDropdown = false
                                        Toast.makeText(context, "${pref.label}(으)로 설정되었습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Sync & Upload Settings
            Text("업로드 및 워터마크 설정", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GreenPrimary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wi-Fi 환경에서만 업로드", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("LTE/5G 모바일 데이터 절약", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = {
                                wifiOnly = it
                                appSettings.isWifiOnly = it
                                Toast.makeText(context, if (it) "Wi-Fi 전용으로 설정되었습니다." else "모바일 데이터 포함 즉시 업로드됩니다.", Toast.LENGTH_SHORT).show()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("촬영 시 워터마크 기본 켜기", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("[프로젝트명 | 촬영일시] 기본 각인", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = defaultWatermark,
                            onCheckedChange = {
                                defaultWatermark = it
                                appSettings.isDefaultWatermark = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary)
                        )
                    }
                }
            }

            // 4. Storage Management
            Text("저장소 관리", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GreenPrimary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("임시 캐시 및 ZIP 정리", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("공유 시 생성된 임시 압축 파일 삭제", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                context.cacheDir.deleteRecursively()
                                Toast.makeText(context, "임시 캐시가 모두 정리되었습니다.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("정리", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 5. App Info & Auto Update
            Text("앱 정보 및 업데이트", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GreenPrimary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        val versionName = remember {
                            try {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.5.0"
                            } catch (e: Exception) {
                                "1.5.0"
                            }
                        }
                        Text("DriveKip v$versionName", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "개인 전용 구글 드라이브 업무용 카메라 & 아카이빙 앱\nGoogle Drive /work/_AppUpdate 폴더를 통한 원클릭 인앱 자동 업데이트를 지원합니다.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onCheckUpdateClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🔄 새 버전 업데이트 확인", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showCustomUrlDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (appSettings.updateCheckUrl.isBlank()) "업데이트 서버/URL 설정 (선택)" else "업데이트 서버: 설정됨",
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("인증 토큰 직접 설정") },
            text = {
                Column {
                    Text("직접 발급받은 Bearer Access Token을 입력하여 즉시 연결할 수 있습니다.", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = manualTokenText,
                        onValueChange = { manualTokenText = it },
                        label = { Text("Bearer Access Token") },
                        placeholder = { Text("ya29.a0...") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualTokenText.isNotBlank()) {
                            authManager.accessToken = manualTokenText.trim()
                            authManager.userEmail = "수동 토큰 연동됨"
                            showTokenDialog = false
                            Toast.makeText(context, "토큰이 적용되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("적용")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }

    if (showCustomUrlDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUrlDialog = false },
            title = { Text("업데이트 확인 서버/URL") },
            text = {
                Column {
                    Text("기본적으로 Google Drive의 /work/_AppUpdate 폴더를 검사합니다. 추가로 GitHub Releases API(예: https://api.github.com/repos/owner/repo/releases/latest)나 자체 서버 update.json URL을 지정할 수 있습니다.", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customUrlInput,
                        onValueChange = { customUrlInput = it },
                        label = { Text("업데이트 JSON / GitHub Releases URL") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        appSettings.updateCheckUrl = customUrlInput.trim()
                        showCustomUrlDialog = false
                        Toast.makeText(context, "업데이트 경로가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUrlDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}
