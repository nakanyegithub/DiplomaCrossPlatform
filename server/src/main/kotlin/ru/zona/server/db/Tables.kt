package ru.zona.server.db

import org.jetbrains.exposed.sql.Table

/** Реестр таблиц. Растёт вместе с фичами. */
object Tables {
    val all: Array<Table> get() = arrayOf(Users)
}

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val displayName = varchar("display_name", 255)
    val role = varchar("role", 32) // STUDENT | TEACHER | ADMIN
    val bio = text("bio").default("")
    val avatarUrl = varchar("avatar_url", 2048).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
