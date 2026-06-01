package ru.zona.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get

@Composable
actual fun rememberImagePicker(onPicked: (String?) -> Unit): () -> Unit {
    val cb by rememberUpdatedState(onPicked)
    return {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = "image/*"
        input.onchange = {
            val file = input.files?.get(0)
            if (file == null) {
                cb(null)
            } else {
                val reader = FileReader()
                reader.onload = {
                    // result уже data:<mime>;base64,...
                    cb(reader.result as? String)
                }
                reader.onerror = { cb(null) }
                reader.readAsDataURL(file)
            }
        }
        input.click()
    }
}
