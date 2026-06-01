package ru.zona.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser

@Composable
actual fun rememberFilePicker(onPicked: (PickedFile?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    val chooser = JFileChooser().apply { dialogTitle = "Выберите документ" }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        val f = chooser.selectedFile
                        PickedFile(f.name, f.length())
                    } else {
                        null
                    }
                }
            onPicked(result)
        }
    }
}
