package ru.diploma.crossplatform

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import ru.diploma.crossplatform.zona.domain.usecase.ZonaUseCases

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var useCases: ZonaUseCases

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App(useCases = useCases)
        }
    }
}
