package ru.zona.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.zona.app.core.design.LoadingState
import ru.zona.app.core.design.MessageState
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.design.ZonaTheme
import ru.zona.app.core.di.AppGraph
import ru.zona.app.core.mvi.collectState
import ru.zona.app.core.mvi.rememberStore
import ru.zona.app.feature.health.presentation.ConnectionStatus
import ru.zona.app.feature.health.presentation.HealthEffect
import ru.zona.app.feature.health.presentation.HealthIntent
import ru.zona.app.feature.health.presentation.HealthStore

@Composable
fun App(graph: AppGraph) {
    ZonaTheme {
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        val store = rememberStore { HealthStore(graph.healthRepository, scope) }
        val state by store.collectState { effect ->
            when (effect) {
                is HealthEffect.Message -> scope.launch { snackbar.showSnackbar(effect.text) }
            }
        }

        LaunchedEffect(Unit) { store.dispatch(HealthIntent.Check) }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
        ) { pad ->
            Column(
                Modifier.fillMaxSize().padding(pad).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Zona", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Обучающая платформа",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                when (state.status) {
                    ConnectionStatus.Checking ->
                        LoadingState(Modifier.height(160.dp))
                    ConnectionStatus.Connected ->
                        Text(
                            "Сервер на связи: ${state.serverName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    ConnectionStatus.Failed ->
                        MessageState(
                            title = "Нет связи с сервером",
                            message = state.error,
                            actionText = "Повторить",
                            onAction = { store.dispatch(HealthIntent.Check) },
                            modifier = Modifier.height(260.dp),
                        )
                    ConnectionStatus.Idle ->
                        ZonaPrimaryButton("Проверить соединение") { store.dispatch(HealthIntent.Check) }
                }
            }
        }
    }
}
