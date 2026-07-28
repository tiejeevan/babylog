package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.engine.MediaCompressor
import java.io.File

/**
 * Loads a memory photo with thumb fallback and decode validation.
 * Avoids showing garbled output when the full file cannot be decoded.
 */
@Composable
fun MemoryImage(
    path: String,
    fallbackPath: String = "",
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val primary = path.takeIf { it.isNotBlank() && MediaCompressor.isValidImageFile(File(it)) }
    val fallback = fallbackPath.takeIf {
        it.isNotBlank() && it != path && MediaCompressor.isValidImageFile(File(it))
    }
    var activePath by remember(path, fallbackPath) {
        mutableStateOf(primary ?: fallback)
    }

    if (activePath.isNullOrBlank()) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Photo unavailable",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(File(activePath!!))
            .crossfade(false)
            .listener(
                onError = { _, _ ->
                    if (activePath != fallback && fallback != null) {
                        activePath = fallback
                    }
                }
            )
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
