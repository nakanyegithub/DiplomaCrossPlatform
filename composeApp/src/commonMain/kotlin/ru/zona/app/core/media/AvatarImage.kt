package ru.zona.app.core.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Платформенное декодирование байтов картинки в [ImageBitmap]. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

/** Парсит data-URI/чистый base64 в байты. */
@OptIn(ExperimentalEncodingApi::class)
private fun base64ToBytes(data: String): ByteArray? {
    val payload = data.substringAfter("base64,", data)
    return runCatching { Base64.decode(payload) }.getOrNull()
}

/**
 * Аватар-кружок. Если есть base64-картинка — показывает её, иначе — инициал на цветном фоне.
 */
@Composable
fun Avatar(
    base64: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val bitmap: ImageBitmap? =
        remember(base64) {
            base64?.let { base64ToBytes(it)?.let(::decodeImageBitmap) }
        }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Аватар",
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
