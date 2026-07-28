package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MediaTypes
import com.example.data.model.MemoryItem
import com.example.ui.components.MemoryImage
import com.example.ui.components.MemoryViewerOverlay
import com.example.ui.viewmodel.BabyCareViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(
    viewModel: BabyCareViewModel,
    onNavigateBack: () -> Unit
) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val mediaBusy by viewModel.mediaBusy.collectAsStateWithLifecycle()
    val mediaError by viewModel.mediaError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAddSheet by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }
    var captionDraft by remember { mutableStateOf("") }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri).orEmpty()
        val isVideo = mime.startsWith("video")
        viewModel.addMemoryFromUri(uri, isVideo = isVideo, caption = captionDraft)
        captionDraft = ""
        showAddSheet = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            // proceed after grant via UI retry
        }
    }

    LaunchedEffect(mediaError) {
        mediaError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMediaError()
        }
    }

    if (showCamera) {
        InAppCameraCapture(
            onCaptured = { path ->
                showCamera = false
                viewModel.addMemoryFromCameraFile(path, captionDraft)
                captionDraft = ""
            },
            onCancel = { showCamera = false }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Memories") },
                    actions = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("memories_dismiss")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    modifier = Modifier.testTag("memories_add_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add memory")
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("memories_screen")
            ) {
                if (memories.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Capture daily moments",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Photos compress for quality. Videos under 50MB sync nearby.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(memories, key = { _, memory -> memory.syncId }) { index, memory ->
                            MemoryThumb(
                                memory = memory,
                                onClick = { viewerStartIndex = index }
                            )
                        }
                    }
                }

                if (mediaBusy) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        viewerStartIndex?.let { startIndex ->
            MemoryViewerOverlay(
                memories = memories,
                startIndex = startIndex,
                onDismiss = { viewerStartIndex = null },
                onUpdateCaption = { memory, caption ->
                    viewModel.updateMemoryCaption(memory, caption)
                },
                onDelete = { memory ->
                    viewModel.deleteMemory(memory)
                }
            )
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Add a memory", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = captionDraft,
                    onValueChange = { captionDraft = it },
                    label = { Text("Caption (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = {
                        val needed = mutableListOf(Manifest.permission.CAMERA)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            needed += Manifest.permission.READ_MEDIA_IMAGES
                        }
                        val missing = needed.filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isNotEmpty()) {
                            permissionLauncher.launch(missing.toTypedArray())
                        } else {
                            showAddSheet = false
                            showCamera = true
                        }
                    }) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Camera")
                    }
                    TextButton(onClick = {
                        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                        } else {
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        val missing = perms.filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isNotEmpty()) {
                            permissionLauncher.launch(missing.toTypedArray())
                        } else {
                            pickMedia.launch("*/*")
                        }
                    }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Gallery")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MemoryThumb(memory: MemoryItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag("memory_thumb_${memory.syncId}")
    ) {
        val path = memory.localPath.ifBlank { memory.thumbPath }
        if (path.isNotBlank() && File(path).exists()) {
            MemoryImage(
                path = memory.localPath,
                fallbackPath = memory.thumbPath,
                contentDescription = memory.caption.ifBlank { "Memory" },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (memory.mediaType == MediaTypes.VIDEO) {
            Icon(
                Icons.Default.Videocam,
                contentDescription = "Video",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), CircleShape)
                    .padding(2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InAppCameraCapture(
    onCaptured: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    var capturing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (capturing) return@FloatingActionButton
                    capturing = true
                    val cacheDir = File(context.cacheDir, "camera").also { it.mkdirs() }
                    val photoFile = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                    val output = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    imageCapture.takePicture(
                        output,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                capturing = false
                                onCaptured(photoFile.absolutePath)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                capturing = false
                                Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "Shutter")
            }
        }
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
