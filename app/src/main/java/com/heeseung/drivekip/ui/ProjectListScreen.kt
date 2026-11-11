package com.heeseung.drivekip.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heeseung.drivekip.data.DriveItem
import com.heeseung.drivekip.data.GoogleDriveService
import com.heeseung.drivekip.ui.theme.GreenPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    driveService: GoogleDriveService,
    isSignedIn: Boolean,
    onOpenProjectGallery: (DriveItem) -> Unit,
    onOpenCameraForProject: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<DriveItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }

    var editingProject by remember { mutableStateOf<DriveItem?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    var deletingProject by remember { mutableStateOf<DriveItem?>(null) }

    fun refreshProjects() {
        if (!isSignedIn) return
        isLoading = true
        scope.launch {
            try {
                val list = driveService.listProjects()
                projects = list
            } catch (e: Exception) {
                Toast.makeText(context, "프로젝트 로딩 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(isSignedIn) {
        if (isSignedIn) refreshProjects()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("프로젝트 관리", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("(${projects.size}개)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshProjects() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        floatingActionButton = {
            if (isSignedIn) {
                FloatingActionButton(
                    onClick = {
                        newProjectName = ""
                        showCreateDialog = true
                    },
                    containerColor = GreenPrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "새 프로젝트 추가")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GreenPrimary)
                }
            } else if (!isSignedIn) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("구글 계정에 로그인하면 프로젝트 목록이 표시됩니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (projects.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("등록된 프로젝트가 없습니다.", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "우측 하단의 [+] 버튼을 눌러 프로젝트를 추가하세요.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(projects) { project ->
                        var menuExpanded by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenProjectGallery(project) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(2.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Folder,
                                                contentDescription = "폴더",
                                                tint = GreenPrimary,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = project.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "Google Drive: work/${project.name}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onOpenCameraForProject(project.name) }) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = "촬영", tint = GreenPrimary)
                                    }

                                    Box {
                                        IconButton(onClick = { menuExpanded = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "옵션", tint = Color.Gray)
                                        }

                                        DropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("이름 변경") },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                                onClick = {
                                                    menuExpanded = false
                                                    editingProject = project
                                                    renameInputText = project.name
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("프로젝트 삭제", color = Color.Red) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp)) },
                                                onClick = {
                                                    menuExpanded = false
                                                    deletingProject = project
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }

    // Create New Project Dialog (No "(예: 강남현장)")
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
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
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("프로젝트명") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newProjectName.trim()
                        if (name.isNotEmpty()) {
                            showCreateDialog = false
                            isLoading = true
                            scope.launch {
                                try {
                                    driveService.getOrCreateProjectFolder(name)
                                    Toast.makeText(context, "'$name' 프로젝트가 생성되었습니다.", Toast.LENGTH_SHORT).show()
                                    refreshProjects()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "생성 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    isLoading = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("생성")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // Rename Project Dialog
    editingProject?.let { proj ->
        AlertDialog(
            onDismissRequest = { editingProject = null },
            title = { Text("프로젝트 이름 변경") },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        label = { Text("새 프로젝트명") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = renameInputText.trim()
                        if (newName.isNotEmpty() && newName != proj.name) {
                            val id = proj.id
                            editingProject = null
                            isLoading = true
                            scope.launch {
                                try {
                                    driveService.renameProjectFolder(id, newName)
                                    Toast.makeText(context, "프로젝트 이름이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                                    refreshProjects()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "이름 변경 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    isLoading = false
                                }
                            }
                        } else {
                            editingProject = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("변경")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProject = null }) {
                    Text("취소")
                }
            }
        )
    }

    // Delete Project Dialog
    deletingProject?.let { proj ->
        AlertDialog(
            onDismissRequest = { deletingProject = null },
            title = { Text("프로젝트 삭제") },
            text = {
                Text(
                    "'${proj.name}' 프로젝트 폴더와 내부 파일들을 구글 드라이브 휴지통으로 이동하시겠습니까?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = proj.id
                        deletingProject = null
                        isLoading = true
                        scope.launch {
                            try {
                                driveService.trashFile(id)
                                Toast.makeText(context, "'${proj.name}' 프로젝트가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                refreshProjects()
                            } catch (e: Exception) {
                                Toast.makeText(context, "삭제 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProject = null }) {
                    Text("취소")
                }
            }
        )
    }
}
