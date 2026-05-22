package ru.zona.server

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/** Полный сброс: только учётные записи и курсы. График, ДЗ и группы преподаватель создаёт сам. */
fun resetDatabase() {
    clearAllData()
    seedDemoData()
}

fun seedIfEmpty() {
    transaction {
        if (Users.selectAll().any()) return@transaction
        seedDemoDataInternal()
    }
}

private fun seedDemoData() {
    transaction { seedDemoDataInternal() }
}

private fun seedDemoDataInternal() {

        val adminId =
            (Users.insert {
                it[Users.email] = "admin@zona.local"
                it[Users.passwordHash] = hashPassword("admin123")
                it[Users.role] = UserRole.ADMIN
                it[Users.displayName] = "Админ Zona"
            } get Users.id).value
        val teacherId =
            (Users.insert {
                it[Users.email] = "teacher@zona.local"
                it[Users.passwordHash] = hashPassword("teacher123")
                it[Users.role] = UserRole.TEACHER
                it[Users.displayName] = "Препод Мария"
                it[Users.lessonDurationsCsv] = ""
            } get Users.id).value
        val teacher2Id =
            (Users.insert {
                it[Users.email] = "teacher2@zona.local"
                it[Users.passwordHash] = hashPassword("teacher123")
                it[Users.role] = UserRole.TEACHER
                it[Users.displayName] = "Препод Алекс"
                it[Users.lessonDurationsCsv] = ""
            } get Users.id).value
        val studentId =
            (Users.insert {
                it[Users.email] = "student@zona.local"
                it[Users.passwordHash] = hashPassword("student123")
                it[Users.role] = UserRole.STUDENT
                it[Users.displayName] = "Ученик Иван"
            } get Users.id).value

        val cid =
            (Courses.insert {
                it[Courses.title] = "Английский: разговорная практика"
                it[Courses.languageFrom] = "ru"
                it[Courses.languageTo] = "en"
                it[Courses.description] =
                    "Живой язык и занятия с преподавателем. Задания выдаёт преподаватель."
                it[Courses.teacherId] = teacherId
            } get Courses.id).value

        val cidEs =
            (Courses.insert {
                it[Courses.title] = "Испанский с нуля"
                it[Courses.languageFrom] = "ru"
                it[Courses.languageTo] = "es"
                it[Courses.description] = "Алфавит, приветствия, числа и базовый диалог за 4 недели."
                it[Courses.teacherId] = teacher2Id
            } get Courses.id).value

        val cidZh =
            (Courses.insert {
                it[Courses.title] = "Китайский: иероглифы и фонетика"
                it[Courses.languageFrom] = "ru"
                it[Courses.languageTo] = "zh"
                it[Courses.description] = "Пиньинь, тоны и первые 50 иероглифов в интерактивном формате."
                it[Courses.teacherId] = teacherId
            } get Courses.id).value

        Enrollments.insert {
            it[Enrollments.userId] = studentId
            it[Enrollments.courseId] = cid
        }
        Enrollments.insert {
            it[Enrollments.userId] = teacherId
            it[Enrollments.courseId] = cid
        }
        Enrollments.insert {
            it[Enrollments.userId] = studentId
            it[Enrollments.courseId] = cidEs
        }

        Lessons.insert {
            it[Lessons.courseId] = cid
            it[Lessons.title] = "Приветствия и базовые фразы"
            it[Lessons.sortOrder] = 1
        }
        Lessons.insert {
            it[Lessons.courseId] = cid
            it[Lessons.title] = "Семья и описание людей"
            it[Lessons.sortOrder] = 2
        }

        @Suppress("UNUSED_VARIABLE")
        val _adminCanUseAppToo = adminId
}
