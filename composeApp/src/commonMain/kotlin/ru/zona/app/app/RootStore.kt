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
    // Состояние формы входа/регистрации — источник правды в сторе, не в экране.
    val registerMode: Boolean = false,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && (!registerMode || displayName.isNotBlank()) && !busy
}

sealed interface RootIntent {
    data object Bootstrap : RootIntent
    data class SetEmail(val value: String) : RootIntent
    data class SetPassword(val value: String) : RootIntent
    data class SetDisplayName(val value: String) : RootIntent
    data object ToggleRegisterMode : RootIntent
    data object Submit : RootIntent
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
            is RootIntent.SetEmail -> setState { it.copy(email = intent.value) }
            is RootIntent.SetPassword -> setState { it.copy(password = intent.value) }
            is RootIntent.SetDisplayName -> setState { it.copy(displayName = intent.value) }
            RootIntent.ToggleRegisterMode -> setState { it.copy(registerMode = !it.registerMode) }
            RootIntent.Submit -> submit()
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

    private fun submit() {
        val s = currentState
        if (s.busy || !s.canSubmit) return
        setState { it.copy(busy = true) }
        scope.launch {
            val r =
                if (s.registerMode) authRepository.register(s.email, s.password, s.displayName)
                else authRepository.login(s.email, s.password)
            when (r) {
                is Outcome.Success ->
                    setState { it.copy(busy = false, user = r.data, phase = AppPhase.Home, email = "", password = "", displayName = "") }
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
