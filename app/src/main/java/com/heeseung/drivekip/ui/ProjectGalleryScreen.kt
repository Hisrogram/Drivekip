package com.heeseung.drivekip.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.heeseung.drivekip.data.DriveItem
import com.heeseung.drivekip.data.GoogleDriveService
import com.heeseung.drivekip.ui.theme.GreenPrimary
import com.heeseung.drivekip.util.ShareUtil
import com.heeseung.drivekip.util.ZipUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectGalleryScreen(
    project: DriveItem,
    driveService: GoogleDriveService,
    onNavigateBack: () -> Unit,
    onOpenCamera: (String) -> Unit,
    onOpenMediaViewer: (List<DriveItem>, Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mediaList by remember { mutableStateOf<List<DriveItem>>(emptyList()) }
    var actualFolderId by remember { mutableStateOf(project.id) }
    var isLoading by remember { mutableStateOf(false) }

    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItemIds = remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var isOperating by remember { mutableStateOf(false) }
    var operationProgress by remember { mutableFloatStateOf(0f) }
    var operationMessage by remember { mutableStateOf("") }

    val itemBounds = remember { mutableMapOf<String, Rect>() }
    var gridOffsetInRoot by remember { mutableStateOf(Offset.Zero) }

    fun refreshMedia() {
        isLoading = true
        scope.launch {
            try {
                val folderId = if (actualFolderId.isBlank()) {
                    driveService.getOrCreateProjectFolder(project.name).also { actualFolderId = it }
                } else {
                    actualFolderId
                }
                val list = driveService.listMediaFiles(folderId)
                mediaList = list
            } catch (e: Exception) {
                Toast.makeText(context, "목록 로딩 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(project.name) {
        refreshMedia()
    }

    val groupedMedia = remember(mediaList) {
        mediaList.groupBy { it.formattedDate }
    }

    val gridState = rememberLazyGridState()
    var gridHeightPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var currentDragY by remember { mutableStateOf<Float?>(null) }
    var lastDragOffset by remember { mutableStateOf<Offset?>(null) }
    var dragStartId by remember { mutableStateOf<String?>(null) }
    var dragInitialSelection by remember { mutableStateOf(setOf<String>()) }

    val density = LocalDensity.current
    val edgeThresholdPx = with(density) { 90.dp.toPx() }

    val mediaPositionMap = remember(groupedMedia) {
        val map = mutableMapOf<String, MediaGridPosition>()
        var currentGlobalRow = 0
        groupedMedia.values.forEachIndexed { groupIdx, itemsInGroup ->
            val chunks = itemsInGroup.chunked(3)
            chunks.forEachIndexed { rowIdx, rowItems ->
                val rowCount = rowItems.size
                rowItems.forEachIndexed { colIdx, item ->
                    map[item.id] = MediaGridPosition(
                        id = item.id,
                        groupIndex = groupIdx,
                        rowInGroup = rowIdx,
                        colInRow = colIdx,
                        rowItemCount = rowCount,
                        globalRow = currentGlobalRow
                    )
                }
                currentGlobalRow++
            }
        }
        map
    }

    fun updateDragSelection(currentRootPos: Offset) {
        val startId = dragStartId ?: return
        val posA = mediaPositionMap[startId] ?: return

        val targetId = itemBounds.entries.firstOrNull { it.value.contains(currentRootPos) }?.key
            ?: itemBounds.entries.minByOrNull { entry ->
                val rect = entry.value
                val cx = (rect.left + rect.right) / 2
                val cy = (rect.top + rect.bottom) / 2
                (cx - currentRootPos.x) * (cx - currentRootPos.x) + (cy - currentRootPos.y) * (cy - currentRootPos.y)
            }?.takeIf { entry ->
                val rect = entry.value
                currentRootPos.y in (rect.top - 150f)..(rect.bottom + 150f)
            }?.key
            ?: return

        val posB = mediaPositionMap[targetId] ?: return

        val minRow = minOf(posA.globalRow, posB.globalRow)
        val maxRow = maxOf(posA.globalRow, posB.globalRow)

        val isTargetAtRowEnd = (posB.colInRow == posB.rowItemCount - 1)
        val isStartAtRowEnd = (posA.colInRow == posA.rowItemCount - 1)

        val newSelection = mutableSetOf<String>()

        mediaPositionMap.values.forEach { pos ->
            val r = pos.globalRow
            if (r < minRow || r > maxRow) return@forEach

            if (posA.globalRow == posB.globalRow) {
                // 같은 행 내에서 드래그
                if (isTargetAtRowEnd) {
                    // 맨 끝 파일에 닿았을 때만 행 전체 연속선택
                    newSelection.add(pos.id)
                } else if (isStartAtRowEnd) {
                    if (pos.colInRow in posB.colInRow..posA.colInRow) newSelection.add(pos.id)
                } else {
                    val minCol = minOf(posA.colInRow, posB.colInRow)
                    val maxCol = maxOf(posA.colInRow, posB.colInRow)
                    if (pos.colInRow in minCol..maxCol) newSelection.add(pos.id)
                }
            } else if (posA.globalRow < posB.globalRow) {
                // 아래 방향으로 드래그
                val colRange = minOf(posA.colInRow, posB.colInRow)..maxOf(posA.colInRow, posB.colInRow)
                if (r == posB.globalRow) {
                    // 현재 도달한 행: 맨 끝 파일에 닿았을 때만 행 전체 선택
                    if (isTargetAtRowEnd) {
                        newSelection.add(pos.id)
                    } else {
                        if (pos.colInRow in colRange) newSelection.add(pos.id)
                    }
                } else if (r == posA.globalRow) {
                    // 시작 행
                    if (isTargetAtRowEnd || isStartAtRowEnd) {
                        if (pos.colInRow >= posA.colInRow) newSelection.add(pos.id)
                    } else {
                        if (pos.colInRow in colRange) newSelection.add(pos.id)
                    }
                } else {
                    // 중간 행들
                    if (isTargetAtRowEnd || isStartAtRowEnd) {
                        newSelection.add(pos.id)
                    } else {
                        if (pos.colInRow in colRange) newSelection.add(pos.id)
                    }
                }
            } else {
                // 위 방향으로 드래그
                val colRange = minOf(posA.colInRow, posB.colInRow)..maxOf(posA.colInRow, posB.colInRow)
                if (r == posB.globalRow) {
                    if (isTargetAtRowEnd) {
                        newSelection.add(pos.id)
                    } else {
                        if (pos.colInRow in colRange) newSelection.add(pos.id)
                    }
                } else if (r == posA.globalRow) {
                    if (isTargetAtRowEnd || isStartAtRowEnd) {
                        if (pos.colInRow <= posA.colInRow) newSelection.add(pos.id)
                    } else {
                        if (pos.colInRow in colRange) newSelection.add(pos.id)
                    }
                } else {
                    if (isTargetAtRowEnd || isStartAtRowEnd) {
                        newSelection.add(pos.id)
                    } else {
                        if (pos.colInRow in colRange) newSelection.add(pos.id)
                    }
                }
            }
        }

        selectedItemIds.value = dragInitialSelection + newSelection
    }

    LaunchedEffect(isDragging, currentDragY, gridHeightPx) {
        if (isDragging && currentDragY != null && gridHeightPx > 0f) {
            while (isDragging) {
                val y = currentDragY ?: break
                if (y > gridHeightPx - edgeThresholdPx) {
                    val ratio = ((y - (gridHeightPx - edgeThresholdPx)) / edgeThresholdPx).coerceIn(0.2f, 2.5f)
                    gridState.scrollBy(ratio * 28f)
                    lastDragOffset?.let { updateDragSelection(gridOffsetInRoot + it) }
                } else if (y < edgeThresholdPx) {
                    val ratio = ((edgeThresholdPx - y) / edgeThresholdPx).coerceIn(0.2f, 2.5f)
                    gridState.scrollBy(-ratio * 28f)
                    lastDragOffset?.let { updateDragSelection(gridOffsetInRoot + it) }
                }
                kotlinx.coroutines.delay(16)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(project.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            text = if (isSelectionMode) "${selectedItemIds.value.size}개 선택됨" else "총 ${mediaList.size}개 미디어",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedItemIds.value = emptySet()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    if (mediaList.isNotEmpty()) {
                        if (isSelectionMode) {
                            TextButton(onClick = {
                                if (selectedItemIds.value.size == mediaList.size) {
                                    selectedItemIds.value = emptySet()
                                } else {
                                    selectedItemIds.value = mediaList.map { it.id }.toSet()
                                }
                            }) {
                                Text(
                                    text = if (selectedItemIds.value.size == mediaList.size) "전체해제" else "전체선택",
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary
                                )
                            }
                        }

                        TextButton(onClick = {
                            isSelectionMode = !isSelectionMode
                            if (!isSelectionMode) selectedItemIds.value = emptySet()
                        }) {
                            Text(
                                text = if (isSelectionMode) "완료" else "선택",
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                        }
                    }

                    if (!isSelectionMode) {
                        IconButton(onClick = { refreshMedia() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { onOpenCamera(project.name) },
                    containerColor = GreenPrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "추가 촬영")
                }
            }
        },
        bottomBar = {
            if (isSelectionMode && selectedItemIds.value.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 12.dp,
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${selectedItemIds.value.size}개 파일 선택됨",
                            fontWeight = FontWeight.Bold,
                            color = GreenPrimary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val selected = mediaList.filter { selectedItemIds.value.contains(it.id) }
                                    executeProjectShare(
                                        context = context,
                                        driveService = driveService,
                                        scope = scope,
                                        items = selected,
                                        asZip = false,
                                        projectName = project.name,
                                        onProgress = { p, msg ->
                                            isOperating = true
                                            operationProgress = p
                                            operationMessage = msg
                                        },
                                        onComplete = { isOperating = false }
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("선택공유", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    val selected = mediaList.filter { selectedItemIds.value.contains(it.id) }
                                    executeProjectShare(
                                        context = context,
                                        driveService = driveService,
                                        scope = scope,
                                        items = selected,
                                        asZip = true,
                                        projectName = project.name,
                                        onProgress = { p, msg ->
                                            isOperating = true
                                            operationProgress = p
                                            operationMessage = msg
                                        },
                                        onComplete = { isOperating = false }
                                    )
                                },
                                modifier = Modifier.weight(1.2f),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                            ) {
                                Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Zip 압축공유", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier.weight(0.9f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("삭제", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .onGloballyPositioned { coordinates ->
                    gridOffsetInRoot = coordinates.positionInRoot()
                    gridHeightPx = coordinates.size.height.toFloat()
                }
                .pointerInput(mediaList.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { localOffset ->
                            val rootPos = gridOffsetInRoot + localOffset
                            val hitId = itemBounds.entries.firstOrNull { it.value.contains(rootPos) }?.key
                                ?: itemBounds.entries.minByOrNull { entry ->
                                    val rect = entry.value
                                    val cx = (rect.left + rect.right) / 2
                                    val cy = (rect.top + rect.bottom) / 2
                                    (cx - rootPos.x) * (cx - rootPos.x) + (cy - rootPos.y) * (cy - rootPos.y)
                                }?.takeIf { entry ->
                                    val rect = entry.value
                                    rootPos.y in (rect.top - 120f)..(rect.bottom + 120f)
                                }?.key

                            if (hitId != null) {
                                isSelectionMode = true
                                dragStartId = hitId
                                dragInitialSelection = selectedItemIds.value
                                isDragging = true
                                currentDragY = localOffset.y
                                lastDragOffset = localOffset
                                updateDragSelection(rootPos)
                            }
                        },
                        onDrag = { change, _ ->
                            currentDragY = change.position.y
                            lastDragOffset = change.position
                            updateDragSelection(gridOffsetInRoot + change.position)
                        },
                        onDragEnd = {
                            isDragging = false
                            currentDragY = null
                            lastDragOffset = null
                            dragStartId = null
                        },
                        onDragCancel = {
                            isDragging = false
                            currentDragY = null
                            lastDragOffset = null
                            dragStartId = null
                        }
                    )
                }
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GreenPrimary)
                }
            } else if (mediaList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("보관된 사진/동영상이 없습니다.", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "카메라 버튼을 눌러 업무 사진을 촬영해보세요.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    groupedMedia.forEach { (dateStr, itemsForDate) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "📅 $dateStr", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(text = "${itemsForDate.size}장", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        items(itemsForDate) { item ->
                            val isSelected = selectedItemIds.value.contains(item.id)
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        width = if (isSelected) 3.dp else 0.5.dp,
                                        color = if (isSelected) GreenPrimary else Color.LightGray,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .onGloballyPositioned { coords ->
                                        itemBounds[item.id] = coords.boundsInRoot()
                                    }
                                    .clickable {
                                        if (isSelectionMode) {
                                            selectedItemIds.value = if (isSelected) {
                                                selectedItemIds.value - item.id
                                            } else {
                                                selectedItemIds.value + item.id
                                            }
                                        } else {
                                            val clickedIdx = mediaList.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                                            onOpenMediaViewer(mediaList, clickedIdx)
                                        }
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

                                if (item.isVideo) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                                    }
                                }

                                if (isSelectionMode) {
                                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                                        Icon(
                                            if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                            contentDescription = null,
                                            tint = if (isSelected) GreenPrimary else Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(120.dp))
                    }
                }

                FastScroller(
                    gridState = gridState,
                    totalItemCount = mediaList.size + groupedMedia.size,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            if (isOperating) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 12.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = operationMessage, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { operationProgress }, modifier = Modifier.fillMaxWidth(), color = GreenPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "${(operationProgress * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("구글 드라이브에서 삭제") },
            text = { Text("선택한 ${selectedItemIds.value.size}개 파일을 구글 드라이브 휴지통으로 이동하시겠습니까?", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        val toDelete = selectedItemIds.value.toList()
                        isSelectionMode = false
                        selectedItemIds.value = emptySet()
                        isLoading = true

                        scope.launch {
                            try {
                                for (fileId in toDelete) {
                                    driveService.trashFile(fileId)
                                }
                                Toast.makeText(context, "삭제 완료되었습니다.", Toast.LENGTH_SHORT).show()
                                refreshMedia()
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
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

private fun executeProjectShare(
    context: Context,
    driveService: GoogleDriveService,
    scope: kotlinx.coroutines.CoroutineScope,
    items: List<DriveItem>,
    asZip: Boolean,
    projectName: String,
    onProgress: (Float, String) -> Unit,
    onComplete: () -> Unit
) {
    scope.launch {
        try {
            val downloadedFiles = mutableListOf<File>()
            val cacheDir = File(context.cacheDir, "project_share_temp").apply { mkdirs() }

            val total = items.size
            for ((idx, item) in items.withIndex()) {
                val prog = (idx.toFloat() / total) * 0.75f
                onProgress(prog, "클라우드 다운로드 중 (${idx + 1}/$total)")
                val destFile = File(cacheDir, item.name)
                driveService.downloadFileTo(item.id, destFile)
                downloadedFiles.add(destFile)
            }

            if (asZip) {
                onProgress(0.85f, "ZIP 압축 생성 중...")
                val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.KOREA).format(Date())
                val zipFile = File(context.cacheDir, "${projectName}_${dateStr}.zip")
                ZipUtil.createZip(downloadedFiles, zipFile)

                withContext(Dispatchers.Main) {
                    onProgress(1.0f, "압축 완료!")
                    onComplete()
                    ShareUtil.shareSingleFile(
                        context = context,
                        file = zipFile,
                        mimeType = "application/zip",
                        title = "${projectName} 사진 묶음 공유"
                    )
                }
            } else {
                withContext(Dispatchers.Main) {
                    onProgress(1.0f, "공유 준비 완료!")
                    onComplete()
                    ShareUtil.shareMultipleFiles(
                        context = context,
                        files = downloadedFiles,
                        title = "${projectName} 사진 공유"
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onComplete()
                Toast.makeText(context, "공유 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
