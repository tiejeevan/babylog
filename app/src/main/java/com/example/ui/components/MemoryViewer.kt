package com.example.ui.components

import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.components.MemoryImage
import com.example.data.model.MediaTypes
import com.example.data.model.MemoryItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MemoryViewerOverlay(
    memories: List<MemoryItem>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onUpdateCaption: (MemoryItem, String) -> Unit,
    onDelete: (MemoryItem) -> Unit
) {
    if (memories.isEmpty()) return

    val safeStart = startIndex.coerceIn(0, memories.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeStart) { memories.size }
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }

    var zoomed by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCaptionSheet by remember { mutableStateOf(false) }

    val currentMemory = memories.getOrNull(pagerState.currentPage)

    BackHandler(onBack = onDismiss)

    LaunchedEffect(pagerState.currentPage) {
        zoomed = false
    }

    LaunchedEffect(memories.size) {
        if (memories.isEmpty()) {
            onDismiss()
        } else if (pagerState.currentPage > memories.lastIndex) {
            pagerState.scrollToPage(memories.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("memory_viewer")
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (currentMemory != null) 120.dp else 0.dp)
        ) { page ->
            val memory = memories[page]
            MemoryPageContent(
                memory = memory,
                onZoomChanged = { isZoomed -> zoomed = isZoomed }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("memory_viewer_close")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            Text(
                text = "${pagerState.currentPage + 1} / ${memories.size}",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.testTag("memory_viewer_delete")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
        }

        if (memories.size > 1 && !zoomed) {
            Text(
                text = "Swipe left or right",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 56.dp)
            )
        }

        currentMemory?.let { memory ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Text(
                    text = dateFmt.format(Date(memory.capturedAtMillis)),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium
                )
                if (memory.caption.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = memory.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (memory.caregiverName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "By ${memory.caregiverName}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showCaptionSheet = true },
                    modifier = Modifier.testTag("memory_viewer_edit_caption")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Edit caption", color = Color.White)
                }
            }
        }
    }

    if (showDeleteConfirm && currentMemory != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete memory?") },
            text = { Text("This photo or video will be removed from your memories. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(currentMemory)
                        if (memories.size <= 1) {
                            onDismiss()
                        }
                    },
                    modifier = Modifier.testTag("memory_delete_confirm")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCaptionSheet && currentMemory != null) {
        MemoryCaptionSheet(
            memory = currentMemory,
            onDismiss = { showCaptionSheet = false },
            onSave = { caption ->
                onUpdateCaption(currentMemory, caption)
                showCaptionSheet = false
            }
        )
    }
}

@Composable
private fun MemoryPageContent(
    memory: MemoryItem,
    onZoomChanged: (Boolean) -> Unit
) {
    val path = memory.localPath.ifBlank { memory.thumbPath }
    val fileExists = path.isNotBlank() && File(path).exists()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            !fileExists -> {
                Text(
                    "Media unavailable",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            memory.mediaType == MediaTypes.VIDEO -> {
                MemoryVideoPlayer(path = path, modifier = Modifier.fillMaxSize())
            }
            else -> {
                ZoomablePhoto(
                    path = memory.localPath,
                    fallbackPath = memory.thumbPath,
                    contentDescription = memory.caption.ifBlank { "Memory photo" },
                    modifier = Modifier.fillMaxSize(),
                    onZoomChanged = onZoomChanged
                )
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(
    path: String,
    fallbackPath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onZoomChanged: (Boolean) -> Unit = {}
) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1.01f)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .pointerInput(path) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(path, scale) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val isMultiTouch = pressedCount >= 2
                        val isZoomed = scale > 1.01f

                        if (isMultiTouch || (isZoomed && zoomChange == 1f)) {
                            event.changes.forEach { it.consume() }
                            scale = (scale * zoomChange).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                offset += panChange
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        MemoryImage(
            path = path,
            fallbackPath = fallbackPath,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun MemoryVideoPlayer(path: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoPath(path)
                setMediaController(MediaController(ctx).also { controller ->
                    controller.setAnchorView(this)
                })
                setOnPreparedListener { player -> player.start() }
            }
        },
        modifier = modifier,
        onRelease = { videoView -> videoView.stopPlayback() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryCaptionSheet(
    memory: MemoryItem,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var caption by remember(memory.syncId) { mutableStateOf(memory.caption) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                "Edit caption",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Caption") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    onClick = { onSave(caption) },
                    modifier = Modifier.testTag("memory_caption_save")
                ) {
                    Text("Save")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
