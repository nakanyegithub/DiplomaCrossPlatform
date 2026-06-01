package ru.zona.app.feature.auth.domain

import ru.zona.app.core.model.User
import ru.zona.app.core.result.Outcome

interface AuthRepository {
    suspend fun register(email: String, password: String, displayName: String): Outcome<User>

    suspend fun login(email: String, password: String): Outcome<User>

    /** Восстановление сессии по сохранённому токену. Success(null) — токена нет. */
    suspend fun restoreSession(): Outcome<User?>

    fun logout()
}
