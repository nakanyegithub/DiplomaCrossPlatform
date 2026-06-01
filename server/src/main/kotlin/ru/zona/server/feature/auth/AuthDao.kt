package ru.zona.server.feature.auth

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import ru.zona.server.db.Users

data class UserRecord(
    val id: Long,
    val email: String,
    val passwordHash: String,
    val displayName: String,
    val role: String,
    val bio: String,
    val avatarUrl: String?,
    val xp: Long,
)

/** Доступ к таблице пользователей. Транзакции узкие — только вокруг запросов. */
class AuthDao {
    fun findByEmail(email: String): UserRecord? =
        transaction {
            Users.selectAll().where { Users.email eq email.lowercase() }
                .firstOrNull()?.toRecord()
        }

    fun findById(id: Long): UserRecord? =
        transaction {
            Users.selectAll().where { Users.id eq id }.firstOrNull()?.toRecord()
        }

    fun emailExists(email: String): Boolean =
        transaction {
            Users.selectAll().where { Users.email eq email.lowercase() }.limit(1).count() > 0
        }

    fun countUsers(): Long =
        transaction {
            Users.selectAll().count()
        }

    fun updateProfile(
        id: Long,
        displayName: String?,
        bio: String?,
        avatarUrl: String?,
    ): UserRecord? =
        transaction {
            val updated =
                Users.update({ Users.id eq id }) { row ->
                    displayName?.let { row[Users.displayName] = it }
                    bio?.let { row[Users.bio] = it }
                    if (avatarUrl != null) {
                        row[Users.avatarUrl] = avatarUrl.ifBlank { null }
                    }
                }
            if (updated == 0) return@transaction null
            Users.selectAll().where { Users.id eq id }.firstOrNull()?.toRecord()
        }

    fun insert(
        email: String,
        passwordHash: String,
        displayName: String,
        role: String,
    ): Long =
        transaction {
            Users.insert {
                it[Users.email] = email.lowercase()
                it[Users.passwordHash] = passwordHash
                it[Users.displayName] = displayName
                it[Users.role] = role
                it[Users.bio] = ""
                it[Users.avatarUrl] = null
                it[Users.createdAt] = System.currentTimeMillis()
            }[Users.id]
        }

    private fun ResultRow.toRecord() =
        UserRecord(
            id = this[Users.id],
            email = this[Users.email],
            passwordHash = this[Users.passwordHash],
            displayName = this[Users.displayName],
            role = this[Users.role],
            bio = this[Users.bio],
            avatarUrl = this[Users.avatarUrl],
            xp = this[Users.xp],
        )
}
