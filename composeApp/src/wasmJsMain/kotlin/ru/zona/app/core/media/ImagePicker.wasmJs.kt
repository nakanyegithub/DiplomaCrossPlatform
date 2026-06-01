package ru.zona.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement

/**
 * JS-хелпер: открывает выбор изображения, читает его как data-URL (base64)
 * и вызывает Kotlin-колбэк строкой. Вся работа с JS-значениями — на стороне JS,
 * чтобы избежать некорректных wasm-кастов externref.
 */
@JsFun(
    """(onResult) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = () => {
            const file = input.files && input.files[0];
            if (!file) { onResult(null); return; }
            const reader = new FileReader();
            reader.onload = () => onResult(typeof reader.result === 'string' ? reader.result : null);
            reader.onerror = () => onResult(null);
            reader.readAsDataURL(file);
        };
        input.click();
    }""",
)
private external fun pickImageJs(onResult: (String?) -> Unit)

@Composable
actual fun rememberImagePicker(onPicked: (String?) -> Unit): () -> Unit {
    val cb by rememberUpdatedState(onPicked)
    return { pickImageJs { result -> cb(result) } }
}
