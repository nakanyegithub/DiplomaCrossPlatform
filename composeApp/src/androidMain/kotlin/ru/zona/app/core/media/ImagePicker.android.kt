package ru.zona.app.core.media

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberImagePicker(onPicked: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) {
                onPicked(null)
                return@rememberLauncherForActivityResult
            }
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null) {
                onPicked(null)
            } else {
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                onPicked("data:$mime;base64,$b64")
            }
        }
    return { launcher.launch("image/*") }
}
