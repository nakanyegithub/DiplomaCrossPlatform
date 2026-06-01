package ru.zona.server.feature.learning

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import ru.zona.server.db.Courses
import ru.zona.server.db.Enrollments
import ru.zona.server.db.ExerciseAttempts
import ru.zona.server.db.Exercises
import ru.zona.server.db.LessonProgress
import ru.zona.server.db.Lessons
import ru.zona.server.db.Users
import ru.zona.server.feature.wallet.WalletService
import ru.zona.server.plugins.ApiException

class LearningService(
    private val wallet: WalletService,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun catalog(userId: Long, query: String?): List<CourseDto> =
        transaction {
            Courses.selectAll().where { Courses.published eq true }
                .orderBy(Courses.createdAt to SortOrder.DESC)
                .map { it[Courses.id] }
                .mapNotNull { courseDto(it, userId) }
                .filter { c ->
                    query.isNullOrBlank() ||
                        c.title.contains(query, true) ||
                        c.languageTo.contains(query, true) ||
                        c.teacherName.contains(query, true)
                }
        }

    fun myCourses(userId: Long): List<CourseDto> =
        transaction {
            Enrollments.selectAll().where { Enrollments.userId eq userId }
                .map { it[Enrollments.courseId] }
                .mapNotNull { courseDto(it, userId) }
        }

    fun detail(courseId: Long, userId: Long): CourseDetailDto =
        transaction {
            val course = courseDto(courseId, userId) ?: throw ApiException(HttpStatusCode.NotFound, "Курс не найден")
            val lessons =
                Lessons.selectAll().where { Lessons.courseId eq courseId }
                    .orderBy(Lessons.sortOrder to SortOrder.ASC)
                    .map { row ->
                        val lessonId = row[Lessons.id]
                        val exCount = Exercises.selectAll().where { Exercises.lessonId eq lessonId }.count().toInt()
                        val done =
                            LessonProgress.selectAll()
                                .where { (LessonProgress.userId eq userId) and (LessonProgress.lessonId eq lessonId) }
                                .firstOrNull()?.get(LessonProgress.completed) ?: false
                        LessonDto(lessonId, courseId, row[Lessons.title], row[Lessons.sortOrder], exCount, done)
                    }
            CourseDetailDto(course, lessons)
        }

    fun enroll(courseId: Long, userId: Long): CourseDto {
        val price =
            transaction {
                val c = Courses.selectAll().where { Courses.id eq courseId }.firstOrNull()
                    ?: throw ApiException(HttpStatusCode.NotFound, "Курс не найден")
                val already =
                    Enrollments.selectAll()
                        .where { (Enrollments.userId eq userId) and (Enrollments.courseId eq courseId) }
                        .limit(1).count() > 0
                if (already) throw ApiException(HttpStatusCode.Conflict, "Вы уже записаны на курс")
                c[Courses.priceCents] ?: 0L
            }
        if (price > 0) wallet.charge(userId, price, "Курс #$courseId")
        transaction {
            Enrollments.insert {
                it[Enrollments.userId] = userId
                it[Enrollments.courseId] = courseId
                it[paidCents] = price
                it[createdAt] = System.currentTimeMillis()
            }
        }
        return transaction { courseDto(courseId, userId)!! }
    }

    fun exercises(lessonId: Long, userId: Long): List<ExerciseDto> =
        transaction {
            requireEnrolledForLesson(lessonId, userId)
            Exercises.selectAll().where { Exercises.lessonId eq lessonId }
                .orderBy(Exercises.sortOrder to SortOrder.ASC)
                .map {
                    ExerciseDto(
                        id = it[Exercises.id],
                        lessonId = lessonId,
                        type = it[Exercises.type],
                        prompt = it[Exercises.prompt],
                        choices = it[Exercises.choicesJson]?.let { c -> runCatching { json.decodeFromString(ListSerializer(String.serializer()), c) }.getOrDefault(emptyList()) } ?: emptyList(),
                        xp = it[Exercises.xp],
                        sortOrder = it[Exercises.sortOrder],
                    )
                }
        }

    fun submitAnswer(exerciseId: Long, userId: Long, answer: String): SubmitAnswerResponse {
        return transaction {
            val ex = Exercises.selectAll().where { Exercises.id eq exerciseId }.firstOrNull()
                ?: throw ApiException(HttpStatusCode.NotFound, "Упражнение не найдено")
            val lessonId = ex[Exercises.lessonId]
            requireEnrolledForLesson(lessonId, userId)
            val correctAnswer = ex[Exercises.correctAnswer].trim()
            val correct =
                when (ex[Exercises.type]) {
                    "TEXT_INPUT" -> answer.trim().equals(correctAnswer, ignoreCase = true)
                    "MULTI_CHOICE" -> answer.split(",").map { it.trim() }.toSet() == correctAnswer.split(",").map { it.trim() }.toSet()
                    else -> answer.trim() == correctAnswer
                }
            val firstCorrect =
                ExerciseAttempts.selectAll()
                    .where { (ExerciseAttempts.userId eq userId) and (ExerciseAttempts.exerciseId eq exerciseId) and (ExerciseAttempts.correct eq true) }
                    .limit(1).count() == 0L
            ExerciseAttempts.insert {
                it[ExerciseAttempts.userId] = userId
                it[ExerciseAttempts.exerciseId] = exerciseId
                it[ExerciseAttempts.answer] = answer
                it[ExerciseAttempts.correct] = correct
                it[createdAt] = System.currentTimeMillis()
            }
            var earned = 0
            if (correct && firstCorrect) {
                earned = ex[Exercises.xp]
                val curXp = Users.selectAll().where { Users.id eq userId }.first()[Users.xp]
                Users.update({ Users.id eq userId }) { it[xp] = curXp + earned }
            }
            if (correct) maybeCompleteLesson(lessonId, userId)
            val totalXp = Users.selectAll().where { Users.id eq userId }.first()[Users.xp]
            SubmitAnswerResponse(correct, earned, totalXp)
        }
    }

    // --- teacher authoring ---
    fun createCourse(teacherId: Long, req: CreateCourseRequest): CourseDto {
        if (req.title.isBlank()) throw ApiException(HttpStatusCode.UnprocessableEntity, "Введите название курса")
        val id =
            transaction {
                Courses.insert {
                    it[Courses.teacherId] = teacherId
                    it[title] = req.title.trim()
                    it[description] = req.description.trim()
                    it[languageFrom] = req.languageFrom.trim()
                    it[languageTo] = req.languageTo.trim()
                    it[level] = req.level
                    it[priceCents] = req.priceCents?.takeIf { p -> p > 0 }
                    it[coverEmoji] = req.coverEmoji.ifBlank { "🚀" }
                    it[galleryJson] = if (req.gallery.isEmpty()) null else json.encodeToString(ListSerializer(String.serializer()), req.gallery)
                    it[published] = true
                    it[createdAt] = System.currentTimeMillis()
                }[Courses.id]
            }
        return transaction { courseDto(id, teacherId)!! }
    }

    fun addLesson(teacherId: Long, courseId: Long, req: CreateLessonRequest): LessonDto =
        transaction {
            requireCourseOwner(courseId, teacherId)
            if (req.title.isBlank()) throw ApiException(HttpStatusCode.UnprocessableEntity, "Введите название урока")
            val order = (Lessons.selectAll().where { Lessons.courseId eq courseId }.count()).toInt()
            val id =
                Lessons.insert {
                    it[Lessons.courseId] = courseId
                    it[title] = req.title.trim()
                    it[sortOrder] = order
                }[Lessons.id]
            LessonDto(id, courseId, req.title.trim(), order, 0, false)
        }

    fun addExercise(teacherId: Long, lessonId: Long, req: CreateExerciseRequest): ExerciseDto =
        transaction {
            val courseId = Lessons.selectAll().where { Lessons.id eq lessonId }.firstOrNull()?.get(Lessons.courseId)
                ?: throw ApiException(HttpStatusCode.NotFound, "Урок не найден")
            requireCourseOwner(courseId, teacherId)
            if (req.prompt.isBlank()) throw ApiException(HttpStatusCode.UnprocessableEntity, "Введите вопрос")
            val order = (Exercises.selectAll().where { Exercises.lessonId eq lessonId }.count()).toInt()
            val id =
                Exercises.insert {
                    it[Exercises.lessonId] = lessonId
                    it[type] = req.type
                    it[prompt] = req.prompt.trim()
                    it[choicesJson] = if (req.choices.isEmpty()) null else json.encodeToString(ListSerializer(String.serializer()), req.choices)
                    it[correctAnswer] = req.correctAnswer.trim()
                    it[xp] = req.xp
                    it[sortOrder] = order
                }[Exercises.id]
            ExerciseDto(id, lessonId, req.type, req.prompt.trim(), req.choices, req.xp, order)
        }

    // --- helpers ---
    private fun maybeCompleteLesson(lessonId: Long, userId: Long) {
        val exIds = Exercises.selectAll().where { Exercises.lessonId eq lessonId }.map { it[Exercises.id] }
        if (exIds.isEmpty()) return
        val solved =
            exIds.all { exId ->
                ExerciseAttempts.selectAll()
                    .where { (ExerciseAttempts.userId eq userId) and (ExerciseAttempts.exerciseId eq exId) and (ExerciseAttempts.correct eq true) }
                    .limit(1).count() > 0
            }
        if (solved) {
            LessonProgress.upsert {
                it[LessonProgress.userId] = userId
                it[LessonProgress.lessonId] = lessonId
                it[completed] = true
                it[completedAt] = System.currentTimeMillis()
            }
        }
    }

    private fun requireEnrolledForLesson(lessonId: Long, userId: Long) {
        val courseId = Lessons.selectAll().where { Lessons.id eq lessonId }.firstOrNull()?.get(Lessons.courseId)
            ?: throw ApiException(HttpStatusCode.NotFound, "Урок не найден")
        val enrolled =
            Enrollments.selectAll()
                .where { (Enrollments.userId eq userId) and (Enrollments.courseId eq courseId) }
                .limit(1).count() > 0
        val owner = Courses.selectAll().where { Courses.id eq courseId }.firstOrNull()?.get(Courses.teacherId) == userId
        if (!enrolled && !owner) throw ApiException(HttpStatusCode.Forbidden, "Сначала запишитесь на курс")
    }

    private fun requireCourseOwner(courseId: Long, teacherId: Long) {
        val owner = Courses.selectAll().where { Courses.id eq courseId }.firstOrNull()?.get(Courses.teacherId)
            ?: throw ApiException(HttpStatusCode.NotFound, "Курс не найден")
        if (owner != teacherId) throw ApiException(HttpStatusCode.Forbidden, "Это не ваш курс")
    }

    /** Должен вызываться внутри transaction { }. */
    private fun courseDto(courseId: Long, userId: Long): CourseDto? {
        val row = Courses.selectAll().where { Courses.id eq courseId }.firstOrNull() ?: return null
        val teacherName = Users.selectAll().where { Users.id eq row[Courses.teacherId] }.firstOrNull()?.get(Users.displayName) ?: ""
        val lessonIds = Lessons.selectAll().where { Lessons.courseId eq courseId }.map { it[Lessons.id] }
        val enrolled =
            Enrollments.selectAll()
                .where { (Enrollments.userId eq userId) and (Enrollments.courseId eq courseId) }
                .limit(1).count() > 0
        val progress =
            if (lessonIds.isEmpty()) {
                0
            } else {
                val done =
                    lessonIds.count { lid ->
                        LessonProgress.selectAll()
                            .where { (LessonProgress.userId eq userId) and (LessonProgress.lessonId eq lid) and (LessonProgress.completed eq true) }
                            .limit(1).count() > 0
                    }
                (done * 100 / lessonIds.size)
            }
        return CourseDto(
            id = courseId,
            teacherId = row[Courses.teacherId],
            teacherName = teacherName,
            title = row[Courses.title],
            description = row[Courses.description],
            languageFrom = row[Courses.languageFrom],
            languageTo = row[Courses.languageTo],
            level = row[Courses.level],
            priceCents = row[Courses.priceCents],
            coverEmoji = row[Courses.coverEmoji],
            gallery = row[Courses.galleryJson]?.let { g -> runCatching { json.decodeFromString(ListSerializer(String.serializer()), g) }.getOrDefault(emptyList()) } ?: emptyList(),
            lessonCount = lessonIds.size,
            enrolled = enrolled,
            progressPercent = progress,
        )
    }
}
