package ru.zona.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ru.zona.app.core.di.AppGraph

fun main() = application {
    val graph = AppGraph()
    Window(
        onCloseRequest = ::exitApplication,
        title = "Zona",
        state = rememberWindowState(width = 420.dp, height = 760.dp),
    ) {
        App(graph)
    }
}
