package ru.zona.app.core.media

import androidx.compose.runtime.Composable

/**
 * Кроссплатформенный выбор изображения. [onPicked] получает data-URI
 * вида `data:image/png;base64,...` либо null, если выбор отменён.
 *
 * Возвращает функцию-триггер: вызовите её по клику, чтобы открыть системный выбор файла.
 */
@Composable
expect fun rememberImagePicker(onPicked: (String?) -> Unit): () -> Unit
