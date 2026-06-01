package ru.zona.server.db

import ru.zona.server.feature.auth.AuthDao
import ru.zona.server.feature.auth.AuthService
import ru.zona.server.feature.flashcards.CreateCardRequest
import ru.zona.server.feature.flashcards.CreateDeckRequest
import ru.zona.server.feature.flashcards.FlashcardService
import ru.zona.server.feature.learning.CreateCourseRequest
import ru.zona.server.feature.learning.CreateExerciseRequest
import ru.zona.server.feature.learning.CreateLessonRequest
import ru.zona.server.feature.learning.LearningService
import ru.zona.server.feature.sessions.CreateSessionRequest
import ru.zona.server.feature.sessions.SessionService
import ru.zona.server.feature.wallet.WalletService
import ru.zona.server.security.PasswordHasher

/** Демо-наполнение для локального запуска и защиты (только при пустой БД). */
object ZonaSeed {
    fun seedIfEmpty(
        authDao: AuthDao,
        learning: LearningService,
        flashcards: FlashcardService,
        sessions: SessionService,
        wallet: WalletService,
    ) {
        if (authDao.countUsers() > 0) return

        val admin = authDao.insert("admin@zona.space", PasswordHasher.hash("admin123"), "Командир станции", "ADMIN")
        val teacher = authDao.insert("teacher@zona.space", PasswordHasher.hash("teacher123"), "Анна Орбитальная", "TEACHER")
        val student = authDao.insert("student@zona.space", PasswordHasher.hash("student123"), "Юный Космонавт", "STUDENT")

        wallet.topUp(student, 500_00) // 500.00 у.е. на демо-баланс
        wallet.topUp(teacher, 100_00)

        // Курс 1 — бесплатный
        val english =
            learning.createCourse(
                teacher,
                CreateCourseRequest(
                    title = "Английский для космонавтов",
                    description = "Базовый английский: приветствия, числа, фразы для миссии.",
                    languageFrom = "Русский",
                    languageTo = "English",
                    level = "A1",
                    priceCents = null,
                    coverEmoji = "🚀",
                ),
            )
        val l1 = learning.addLesson(teacher, english.id, CreateLessonRequest("Приветствия"))
        learning.addExercise(
            teacher, l1.id,
            CreateExerciseRequest("SINGLE_CHOICE", "Как будет «Привет»?", listOf("Hello", "Goodbye", "Thanks"), "0", 10),
        )
        learning.addExercise(
            teacher, l1.id,
            CreateExerciseRequest("TEXT_INPUT", "Переведите: «Спасибо»", emptyList(), "Thank you", 15),
        )
        val l2 = learning.addLesson(teacher, english.id, CreateLessonRequest("Числа"))
        learning.addExercise(
            teacher, l2.id,
            CreateExerciseRequest("SINGLE_CHOICE", "Сколько будет three + two?", listOf("five", "four", "six"), "0", 10),
        )

        // Курс 2 — платный
        val spanish =
            learning.createCourse(
                teacher,
                CreateCourseRequest(
                    title = "Испанский: первый контакт",
                    description = "Поехали покорять испаноязычные галактики.",
                    languageFrom = "Русский",
                    languageTo = "Español",
                    level = "A1",
                    priceCents = 199_00,
                    coverEmoji = "🪐",
                ),
            )
        val s1 = learning.addLesson(teacher, spanish.id, CreateLessonRequest("Saludos"))
        learning.addExercise(
            teacher, s1.id,
            CreateExerciseRequest("SINGLE_CHOICE", "«Привет» по-испански?", listOf("Hola", "Adiós", "Gracias"), "0", 10),
        )

        // Колода карточек
        val deck = flashcards.createDeck(teacher, CreateDeckRequest("English: первые 5 слов", english.id))
        listOf(
            "Hello" to "Привет",
            "Goodbye" to "Пока",
            "Please" to "Пожалуйста",
            "Thanks" to "Спасибо",
            "Yes" to "Да",
        ).forEach { (f, b) -> flashcards.addCard(teacher, deck.id, CreateCardRequest(f, b)) }

        // Занятия
        val dayMs = 86_400_000L
        sessions.create(
            teacher,
            CreateSessionRequest(
                type = "GROUP",
                title = "Разговорный клуб: English",
                description = "Практикуем приветствия в прямом эфире.",
                startsAt = System.currentTimeMillis() + dayMs,
                durationMinutes = 60,
                capacity = 8,
                priceCents = null,
            ),
        )
        sessions.create(
            teacher,
            CreateSessionRequest(
                type = "INDIVIDUAL",
                title = "Индивидуальный урок с Анной",
                description = "Персональная отработка произношения.",
                startsAt = System.currentTimeMillis() + 2 * dayMs,
                durationMinutes = 45,
                capacity = 1,
                priceCents = 150_00,
            ),
        )
    }
}
