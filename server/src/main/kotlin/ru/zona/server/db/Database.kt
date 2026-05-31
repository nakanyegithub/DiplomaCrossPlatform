package ru.zona.server.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import ru.zona.server.ServerConfig

object ZonaDatabase {
    fun init(config: ServerConfig) {
        Database.connect(
            url = config.jdbcUrl,
            driver = config.dbDriver,
            user = config.dbUser,
            password = config.dbPassword,
        )
        transaction {
            // Фаза 0: схема растёт по мере добавления фич.
            SchemaUtils.createMissingTablesAndColumns(*Tables.all)
        }
    }
}
