package ru.zona.app.feature.health.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.zona.app.core.mvi.MviStore
import ru.zona.app.core.result.Outcome
import ru.zona.app.feature.health.domain.HealthRepository

enum class ConnectionStatus { Idle, Checking, Connected, Failed }

data class HealthState(
    val status: ConnectionStatus = ConnectionStatus.Idle,
    val serverName: String = "",
    val error: String = "",
)

sealed interface HealthIntent {
    data object Check : HealthIntent
}

sealed interface HealthEffect {
    data class Message(val text: String) : HealthEffect
}

class HealthStore(
    private val repository: HealthRepository,
    scope: CoroutineScope,
) : MviStore<HealthState, HealthIntent, HealthEffect>(HealthState(), scope) {

    override fun onIntent(intent: HealthIntent) {
        when (intent) {
            HealthIntent.Check -> check()
        }
    }

    private fun check() {
        setState { it.copy(status = ConnectionStatus.Checking, error = "") }
        scope.launch {
            when (val result = repository.ping()) {
                is Outcome.Success ->
                    setState { it.copy(status = ConnectionStatus.Connected, serverName = result.data) }
                is Outcome.Failure -> {
                    setState { it.copy(status = ConnectionStatus.Failed, error = result.message) }
                    emit(HealthEffect.Message(result.message))
                }
            }
        }
    }
}
