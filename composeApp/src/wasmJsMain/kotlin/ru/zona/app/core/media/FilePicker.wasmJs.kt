package ru.zona.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/** JS-хелпер выбора файла: возвращает имя файла строкой (или null при отмене). */
@JsFun(
    """(onResult) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.onchange = () => {
            const file = input.files && input.files[0];
            onResult(file ? file.name : null);
        };
        input.click();
    }""",
)
private external fun pickFileJs(onResult: (String?) -> Unit)

@Composable
actual fun rememberFilePicker(onPicked: (PickedFile?) -> Unit): () -> Unit {
    val cb by rememberUpdatedState(onPicked)
    return {
        pickFileJs { name -> cb(if (name == null) null else PickedFile(name, 0L)) }
    }
}
