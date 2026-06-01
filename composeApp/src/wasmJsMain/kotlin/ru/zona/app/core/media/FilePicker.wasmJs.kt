package ru.zona.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.files.get

@Composable
actual fun rememberFilePicker(onPicked: (PickedFile?) -> Unit): () -> Unit {
    val cb by rememberUpdatedState(onPicked)
    return {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.onchange = {
            val file = input.files?.get(0)
            if (file == null) cb(null) else cb(PickedFile(file.name, 0L))
        }
        input.click()
    }
}
