package ru.zona.server

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun initDatabase(
    jdbcUrl: String,
    user: String,
    password: String,
) {
    val driver =
        when {
            jdbcUrl.startsWith("jdbc:h2") -> "org.h2.Driver"
            else -> "org.postgresql.Driver"
        }
    Database.connect(
        url = jdbcUrl,
        driver = driver,
        user = user,
        password = password,
    )
    transaction {
        SchemaUtils.createMissingTablesAndColumns(
            Users,
            Courses,
            Enrollments,
            Lessons,
            Exercises,
            ExerciseAttempts,
            LiveSessions,
            SessionBookings,
            TeacherBookingRequests,
            TeacherStudentMeta,
        )
    }
}
