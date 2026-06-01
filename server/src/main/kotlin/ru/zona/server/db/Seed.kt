package ru.zona.server.db

import ru.zona.server.feature.auth.AuthDao
import ru.zona.server.feature.auth.AuthService
import ru.zona.server.security.PasswordHasher

/** Демо-аккаунты для локальной разработки (только если БД пустая). */
object ZonaSeed {
    fun seedIfEmpty(dao: AuthDao) {
        if (dao.countUsers() > 0) return
        dao.insert(
            email = "student@zona.local",
            passwordHash = PasswordHasher.hash("student123"),
            displayName = "Ученик Демо",
            role = AuthService.ROLE_STUDENT,
        )
        dao.insert(
            email = "admin@zona.local",
            passwordHash = PasswordHasher.hash("admin123"),
            displayName = "Админ",
            role = "ADMIN",
        )
    }
}
