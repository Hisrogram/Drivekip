package com.heeseung.drivekip.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    items: List<DriveItem>,
    initialIndex: Int,
    driveService: GoogleDriveService,
    onNavigateBack: () -> Unit,
    onDeleted: (DriveItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onNavigateBack() }
        return
    }

    val safeInitial = initialIndex.coerceIn(0, items.size - 1)
    val pagerState = rememberPagerState(initialPage = safeInitial) { items.size }

    val currentItem = items.getOrNull(pagerState.currentPage) ?: items.first()

    var isProcessing by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var lastToastTime by remember { mutableStateOf(0L) }
    fun showBoundaryToast(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastToastTime > 1800L) {
            lastToastTime = now
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // NestedScrollConnection detects overscroll attempts at boundaries reliably!
    val nestedScrollConnection = remember(pagerState, items.size) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pagerState.currentPage == 0 && available.x > 12f) {
                    showBoundaryToast("맨 첫 사진입니다")
                } else if (pagerState.currentPage >= items.size - 1 && available.x < -12f) {
                    showBoundaryToast("마지막 사진입니다")
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (pagerState.currentPage == 0 && available.x > 8f) {
                    showBoundaryToast("맨 첫 사진입니다")
                } else if (pagerState.currentPage >= items.size - 1 && available.x < -8f) {
                    showBoundaryToast("마지막 사진입니다")
                }
                return Offset.Zero
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentItem.name,
                            maxLines = 1,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${pagerState.currentPage + 1} / ${items.size}",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기", tint = Color.White)
                    }
                },
                actions = {
                    currentItem.webViewLink?.let { link ->
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "드라이브에서 열기", tint = Color.White)
                        }
                    }

                    IconButton(onClick = {
                        isProcessing = true
                        scope.launch {
                            try {
                                val tempFile = File(context.cacheDir, currentItem.name)
                                driveService.downloadFileTo(currentItem.id, tempFile)
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    ShareUtil.shareSingleFile(
                                        context = context,
                                        file = tempFile,
                                        mimeType = currentItem.mimeType,
                                        title = "파일 공유"
                                    )
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    Toast.makeText(context, "공유 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "공유", tint = Color.White)
                    }

                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color.Red)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.85f),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
                .nestedScroll(nestedScrollConnection)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageItem = items[page]
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(pageItem.thumbnailLink?.replace("=s220", "=s1600") ?: pageItem.webViewLink)
                            .crossfade(true)
                            .build(),
                        contentDescription = pageItem.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (pageItem.isVideo) {
                        Surface(
                            modifier = Modifier.padding(16.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.75f)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.PlayCircle,
                                    contentDescription = "재생",
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("동영상 재생 (구글 드라이브 연동)", color = Color.White, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        pageItem.webViewLink?.let { link ->
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                            context.startActivity(intent)
                                        } ?: Toast.makeText(context, "재생 링크를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                                ) {
                                    Text("드라이브 플레이어로 열기")
                                }
                            }
                        }
                    }
                }
            }

            // Left Chevron (Previous)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable {
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        } else {
                            showBoundaryToast("맨 첫 사진입니다")
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "이전 사진", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            // Right Chevron (Next)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable {
                        if (pagerState.currentPage < items.size - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            showBoundaryToast("마지막 사진입니다")
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "다음 사진", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            // Bottom metadata bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "촬영일자: ${currentItem.formattedDate}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        currentItem.size?.let { s ->
                            val mb = s / (1024.0 * 1024.0)
                            Text(
                                text = String.format(Locale.KOREA, "용량: %.2f MB", mb),
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            if (isProcessing) {
                CircularProgressIndicator(color = GreenPrimary, modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("구글 드라이브에서 삭제") },
            text = { Text("'${currentItem.name}' 파일을 구글 드라이브 휴지통으로 이동하시겠습니까?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        isProcessing = true
                        scope.launch {
                            try {
                                driveService.trashFile(currentItem.id)
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    Toast.makeText(context, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                    onDeleted(currentItem)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    Toast.makeText(context, "삭제 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }
}
