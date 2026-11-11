package com.heeseung.drivekip.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.heeseung.drivekip.ui.theme.GreenPrimary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class MediaGridPosition(
    val id: String,
    val groupIndex: Int,
    val rowInGroup: Int,
    val colInRow: Int,
    val rowItemCount: Int,
    val globalRow: Int
)

@Composable
fun FastScroller(
    gridState: LazyGridState,
    totalItemCount: Int,
    modifier: Modifier = Modifier
) {
    if (totalItemCount <= 6) return // 항목이 적을 땐 스크롤바 불필요

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var isThumbDragging by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }

    val barWidth by animateDpAsState(
        targetValue = if (isThumbDragging) 28.dp else 4.dp,
        animationSpec = tween(durationMillis = 200),
        label = "fastScrollerWidth"
    )

    val thumbHeightDp = 52.dp
    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }

    // 현재 스크롤 위치 비율 계산
    val firstVisible = gridState.firstVisibleItemIndex
    val scrollProgress = if (totalItemCount > 1) {
        (firstVisible.toFloat() / (totalItemCount - 1)).coerceIn(0f, 1f)
    } else 0f

    val thumbOffsetPx = if (trackHeightPx > thumbHeightPx) {
        (trackHeightPx - thumbHeightPx) * scrollProgress
    } else 0f

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(if (isThumbDragging) 36.dp else 20.dp) // 터치 영역은 넉넉하게 확보
            .onGloballyPositioned { coordinates ->
                trackHeightPx = coordinates.size.height.toFloat()
            }
            .pointerInput(totalItemCount, trackHeightPx) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isThumbDragging = true
                        if (trackHeightPx > thumbHeightPx) {
                            val progress = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                            val targetItem = (progress * (totalItemCount - 1)).toInt()
                            coroutineScope.launch {
                                gridState.scrollToItem(targetItem)
                            }
                        }
                    },
                    onVerticalDrag = { change, _ ->
                        if (trackHeightPx > thumbHeightPx) {
                            val progress = (change.position.y / trackHeightPx).coerceIn(0f, 1f)
                            val targetItem = (progress * (totalItemCount - 1)).toInt()
                            coroutineScope.launch {
                                gridState.scrollToItem(targetItem)
                            }
                        }
                    },
                    onDragEnd = { isThumbDragging = false },
                    onDragCancel = { isThumbDragging = false }
                )
            },
        contentAlignment = Alignment.TopEnd
    ) {
        // Fast Scroller Thumb Bar
        Box(
            modifier = Modifier
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                .width(barWidth)
                .height(thumbHeightDp)
                .padding(vertical = 2.dp)
                .shadow(
                    elevation = if (isThumbDragging) 6.dp else 1.dp,
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                )
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .background(if (isThumbDragging) GreenPrimary else Color(0xFF4ADE80).copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            if (isThumbDragging) {
                Icon(
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = "빠른 스크롤",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
