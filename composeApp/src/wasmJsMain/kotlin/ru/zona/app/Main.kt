package ru.zona.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import ru.zona.app.core.di.AppGraph

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val graph = AppGraph()
    ComposeViewport(viewportContainerId = "root") {
        App(graph)
    }
}
