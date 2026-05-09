package ru.zona.server

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

private val json = Json { ignoreUnknownKeys = true }

fun seedIfEmpty() {
    transaction {
        if (Users.selectAll().any()) return@transaction

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
            } get Users.id).value
        val teacher2Id =
            (Users.insert {
                it[Users.email] = "teacher2@zona.local"
                it[Users.passwordHash] = hashPassword("teacher123")
                it[Users.role] = UserRole.TEACHER
                it[Users.displayName] = "Препод Алекс"
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
                    "Курс в духе Verbling: упор на живой язык и занятия с преподавателем."
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

        val lesson1 =
            (Lessons.insert {
                it[Lessons.courseId] = cid
                it[Lessons.title] = "Приветствия и базовые фразы"
                it[Lessons.sortOrder] = 1
            } get Lessons.id).value
        val lesson2 =
            (Lessons.insert {
                it[Lessons.courseId] = cid
                it[Lessons.title] = "Семья и описание людей"
                it[Lessons.sortOrder] = 2
            } get Lessons.id).value

        fun insertExercise(
            lessonId: Long,
            exType: ExerciseType,
            prompt: String,
            answer: String,
            wrong: List<String>?,
            order: Int,
        ) {
            Exercises.insert {
                it[Exercises.lessonId] = lessonId
                it[Exercises.type] = exType
                it[Exercises.prompt] = prompt
                it[Exercises.answer] = answer
                it[Exercises.wrongOptionsJson] =
                    wrong?.let { w -> json.encodeToString(ListSerializer(String.serializer()), w) }
                it[Exercises.sortOrder] = order
            }
        }

        insertExercise(
            lesson1,
            ExerciseType.CHOICE,
            "Как сказать «Здравствуйте» по-английски?",
            "Hello",
            listOf("Goodbye", "Please", "Thanks"),
            1,
        )
        insertExercise(lesson1, ExerciseType.TRANSLATION, "Переведите на английский: «Спасибо»", "Thank you", null, 2)
        insertExercise(
            lesson1,
            ExerciseType.CHOICE,
            "Выберите верный перевод слова «Дом»",
            "House",
            listOf("Horse", "Hour", "Hope"),
            3,
        )

        insertExercise(lesson2, ExerciseType.TRANSLATION, "Переведите: «Это моя сестра»", "This is my sister", null, 1)
        insertExercise(
            lesson2,
            ExerciseType.CHOICE,
            "Слово «Mother» означает",
            "Мать",
            listOf("Отец", "Брат", "Дочь"),
            2,
        )

        val soon = System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000
        LiveSessions.insert {
            it[LiveSessions.courseId] = cid
            it[LiveSessions.teacherId] = teacherId
            it[LiveSessions.title] = "Разговорный клуб: small talk"
            it[LiveSessions.startsAtEpochMs] = soon
            it[LiveSessions.durationMinutes] = 45
            it[LiveSessions.maxStudents] = 8
        }

        TeacherBookingRequests.insert {
            it[TeacherBookingRequests.studentId] = studentId
            it[TeacherBookingRequests.teacherId] = teacherId
            it[TeacherBookingRequests.scheduledAtEpochMs] = soon
            it[TeacherBookingRequests.status] = BookingStatus.CONFIRMED
        }
        TeacherBookingRequests.insert {
            it[TeacherBookingRequests.studentId] = studentId
            it[TeacherBookingRequests.teacherId] = teacher2Id
            it[TeacherBookingRequests.scheduledAtEpochMs] = soon + 2L * 24 * 60 * 60 * 1000
            it[TeacherBookingRequests.status] = BookingStatus.DECLINED
        }

        @Suppress("UNUSED_VARIABLE")
        val _adminCanUseAppToo = adminId
    }
}
