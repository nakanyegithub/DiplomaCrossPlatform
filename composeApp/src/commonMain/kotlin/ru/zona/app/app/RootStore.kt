package ru.zona.app.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.zona.app.core.model.User
import ru.zona.app.core.mvi.MviStore
import ru.zona.app.core.result.Outcome
import ru.zona.app.feature.auth.domain.AuthRepository

enum class AppPhase { Splash, Auth, Home }

data class RootState(
    val phase: AppPhase = AppPhase.Splash,
    val user: User? = null,
    val busy: Boolean = false,
)

sealed interface RootIntent {
    data object Bootstrap : RootIntent
    data class Login(val email: String, val password: String) : RootIntent
    data class Register(val email: String, val password: String, val displayName: String) : RootIntent
    data object Logout : RootIntent
    data class UpdateUser(val user: User) : RootIntent
}

sealed interface RootEffect {
    data class Message(val text: String) : RootEffect
}

class RootStore(
    private val authRepository: AuthRepository,
    scope: CoroutineScope,
) : MviStore<RootState, RootIntent, RootEffect>(RootState(), scope) {

    override fun onIntent(intent: RootIntent) {
        when (intent) {
            RootIntent.Bootstrap -> bootstrap()
            is RootIntent.Login -> authenticate { authRepository.login(intent.email, intent.password) }
            is RootIntent.Register ->
                authenticate { authRepository.register(intent.email, intent.password, intent.displayName) }
            RootIntent.Logout -> logout()
            is RootIntent.UpdateUser -> setState { it.copy(user = intent.user) }
        }
    }

    private fun bootstrap() {
        scope.launch {
            val r =
                withTimeoutOrNull(BOOTSTRAP_TIMEOUT_MS) {
                    authRepository.restoreSession()
                }
            when (r) {
                is Outcome.Success ->
                    setState {
                        it.copy(
                            user = r.data,
                            phase = if (r.data != null) AppPhase.Home else AppPhase.Auth,
                        )
                    }
                is Outcome.Failure -> setState { it.copy(phase = AppPhase.Auth) }
                null -> setState { it.copy(phase = AppPhase.Auth) }
            }
        }
    }

    companion object {
        private const val BOOTSTRAP_TIMEOUT_MS = 8_000L
    }

    private fun authenticate(call: suspend () -> Outcome<User>) {
        if (currentState.busy) return
        setState { it.copy(busy = true) }
        scope.launch {
            when (val r = call()) {
                is Outcome.Success ->
                    setState { it.copy(busy = false, user = r.data, phase = AppPhase.Home) }
                is Outcome.Failure -> {
                    setState { it.copy(busy = false) }
                    emit(RootEffect.Message(r.message))
                }
            }
        }
    }

    private fun logout() {
        authRepository.logout()
        setState { RootState(phase = AppPhase.Auth) }
    }
}
