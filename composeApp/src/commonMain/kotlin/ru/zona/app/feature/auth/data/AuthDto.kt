package ru.zona.app.feature.auth.data

import kotlinx.serialization.Serializable
import ru.zona.app.core.model.User
import ru.zona.app.core.model.UserRole

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val displayName: String,
    val role: String,
    val bio: String = "",
    val avatarUrl: String? = null,
)

@Serializable
data class AuthResponseDto(val token: String, val user: UserDto)

fun UserDto.toDomain(): User =
    User(
        id = id,
        email = email,
        displayName = displayName,
        role = UserRole.from(role),
        bio = bio,
        avatarUrl = avatarUrl,
    )
