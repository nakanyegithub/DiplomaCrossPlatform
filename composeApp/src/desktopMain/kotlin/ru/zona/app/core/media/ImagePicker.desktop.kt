package ru.zona.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberImagePicker(onPicked: (String?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Выберите изображение"
                        fileFilter = FileNameExtensionFilter("Изображения", "png", "jpg", "jpeg", "gif", "webp")
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        val file: File = chooser.selectedFile
                        runCatching {
                            val bytes = file.readBytes()
                            val mime = when (file.extension.lowercase()) {
                                "png" -> "image/png"
                                "gif" -> "image/gif"
                                "webp" -> "image/webp"
                                else -> "image/jpeg"
                            }
                            "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
                        }.getOrNull()
                    } else {
                        null
                    }
                }
            onPicked(result)
        }
    }
}
