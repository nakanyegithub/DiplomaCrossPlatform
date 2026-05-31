package ru.zona.server.db

import org.jetbrains.exposed.sql.Table

/**
 * Реестр таблиц. Фаза 0 — пусто; таблицы добавляются вместе с фичами (auth, courses, ...).
 */
object Tables {
    val all: Array<Table> = arrayOf()
}
