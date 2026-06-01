package ru.zona.app.core.model

/** Роль пользователя. TEACHER — студент после модерации (сохраняет возможности ученика). */
enum class UserRole {
    STUDENT,
    TEACHER,
    ADMIN,
    ;

    companion object {
        fun from(raw: String): UserRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: STUDENT
    }
}

/** Доменная модель пользователя (без аннотаций сериализации). */
data class User(
    val id: Long,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val bio: String = "",
    val avatarUrl: String? = null,
)
