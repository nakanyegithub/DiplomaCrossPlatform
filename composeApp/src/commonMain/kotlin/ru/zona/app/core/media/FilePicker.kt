package ru.zona.app.core.media

import androidx.compose.runtime.Composable

/** Выбранный файл: имя + размер в байтах. */
data class PickedFile(val name: String, val sizeBytes: Long)

/**
 * Кроссплатформенный выбор документа (pdf/изображения/документы).
 * [onPicked] получает [PickedFile] или null при отмене.
 * Возвращает функцию-триггер для вызова по клику.
 */
@Composable
expect fun rememberFilePicker(onPicked: (PickedFile?) -> Unit): () -> Unit
