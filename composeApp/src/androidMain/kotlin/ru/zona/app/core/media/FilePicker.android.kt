package ru.zona.app.core.media

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFilePicker(onPicked: (PickedFile?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) {
                onPicked(null)
                return@rememberLauncherForActivityResult
            }
            var name = "документ"
            var size = 0L
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (c.moveToFirst()) {
                        if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                        if (sizeIdx >= 0) size = c.getLong(sizeIdx)
                    }
                }
            }
            onPicked(PickedFile(name, size))
        }
    return { launcher.launch("*/*") }
}
