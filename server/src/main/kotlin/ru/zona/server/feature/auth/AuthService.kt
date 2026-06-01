package ru.zona.server.feature.auth

import io.ktor.http.HttpStatusCode
import ru.zona.server.plugins.ApiException
import ru.zona.server.security.JwtService
import ru.zona.server.security.PasswordHasher

/** Бизнес-логика авторизации: валидация, хеширование, выпуск токена. */
class AuthService(
    private val dao: AuthDao,
    private val jwtService: JwtService,
) {
    fun register(request: RegisterRequest): AuthResponse {
        val email = request.email.trim().lowercase()
        val name = request.displayName.trim()
        if (!email.contains("@") || email.length < 5) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "Некорректный email")
        }
        if (request.password.length < 6) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "Пароль не короче 6 символов")
        }
        if (name.isBlank()) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "Введите имя")
        }
        if (dao.emailExists(email)) {
            throw ApiException(HttpStatusCode.Conflict, "Пользователь с таким email уже есть")
        }
        val id =
            dao.insert(
                email = email,
                passwordHash = PasswordHasher.hash(request.password),
                displayName = name,
                role = ROLE_STUDENT,
            )
        val record = dao.findById(id) ?: error("user just created not found")
        return AuthResponse(token = jwtService.issue(id), user = record.toDto())
    }

    fun login(request: LoginRequest): AuthResponse {
        val email = request.email.trim().lowercase()
        val record =
            dao.findByEmail(email)
                ?: throw ApiException(HttpStatusCode.Unauthorized, "Неверный email или пароль")
        if (!PasswordHasher.verify(request.password, record.passwordHash)) {
            throw ApiException(HttpStatusCode.Unauthorized, "Неверный email или пароль")
        }
        return AuthResponse(token = jwtService.issue(record.id), user = record.toDto())
    }

    fun me(userId: Long): UserDto {
        val record =
            dao.findById(userId)
                ?: throw ApiException(HttpStatusCode.NotFound, "Пользователь не найден")
        return record.toDto()
    }

    fun updateProfile(userId: Long, request: UpdateProfileRequest): UserDto {
        val name = request.displayName?.trim()
        if (name != null && name.isBlank()) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "Имя не может быть пустым")
        }
        val avatar = request.avatarUrl?.trim()
        if (avatar != null && avatar.length > 2048) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "Ссылка на аватар слишком длинная")
        }
        val record =
            dao.updateProfile(
                id = userId,
                displayName = name,
                bio = request.bio?.trim(),
                avatarUrl = avatar,
            ) ?: throw ApiException(HttpStatusCode.NotFound, "Пользователь не найден")
        return record.toDto()
    }

    private fun UserRecord.toDto() =
        UserDto(
            id = id,
            email = email,
            displayName = displayName,
            role = role,
            bio = bio,
            avatarUrl = avatarUrl,
            xp = xp,
        )

    companion object {
        const val ROLE_STUDENT = "STUDENT"
    }
}
