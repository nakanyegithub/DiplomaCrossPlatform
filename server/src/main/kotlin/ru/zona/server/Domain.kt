package ru.zona.server

enum class UserRole {
    ADMIN,
    TEACHER,
    STUDENT,
}

enum class ExerciseType {
    /** Несколько вариантов, один верный */
    CHOICE,
    /** Ввод перевода / ответа текстом */
    TRANSLATION,
}

enum class BookingStatus {
    PENDING,
    CONFIRMED,
    DECLINED,
}
