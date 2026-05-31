package ru.zona.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.zona.app.core.di.AppGraph
import ru.zona.app.core.network.AndroidAppContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAppContext.context = applicationContext
        val graph = AppGraph()
        enableEdgeToEdge()
        setContent { App(graph) }
    }
}
