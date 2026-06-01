package ru.zona.app.feature.auth.data

import io.ktor.client.call.body
import ru.zona.app.core.model.User
import ru.zona.app.core.network.TokenStorage
import ru.zona.app.core.network.safeApiCall
import ru.zona.app.core.result.Outcome
import ru.zona.app.feature.auth.domain.AuthRepository

class AuthRepositoryImpl(
    private val api: AuthApi,
    private val tokenStorage: TokenStorage,
) : AuthRepository {

    override suspend fun register(
        email: String,
        password: String,
        displayName: String,
    ): Outcome<User> {
        val result =
            safeApiCall(
                request = { api.register(RegisterRequest(email.trim(), password, displayName.trim())) },
                decode = { it.body<AuthResponseDto>() },
            )
        return result.persistAndMap()
    }

    override suspend fun login(email: String, password: String): Outcome<User> {
        val result =
            safeApiCall(
                request = { api.login(LoginRequest(email.trim(), password)) },
                decode = { it.body<AuthResponseDto>() },
            )
        return result.persistAndMap()
    }

    override suspend fun restoreSession(): Outcome<User?> {
        if (tokenStorage.token.isNullOrBlank()) return Outcome.Success(null)
        return when (val r = safeApiCall(request = { api.me() }, decode = { it.body<UserDto>() })) {
            is Outcome.Success -> Outcome.Success(r.data.toDomain())
            is Outcome.Failure -> {
                tokenStorage.clear()
                Outcome.Success(null)
            }
        }
    }

    override fun logout() {
        tokenStorage.clear()
    }

    private fun Outcome<AuthResponseDto>.persistAndMap(): Outcome<User> =
        when (this) {
            is Outcome.Success -> {
                tokenStorage.token = data.token
                Outcome.Success(data.user.toDomain())
            }
            is Outcome.Failure -> this
        }
}
