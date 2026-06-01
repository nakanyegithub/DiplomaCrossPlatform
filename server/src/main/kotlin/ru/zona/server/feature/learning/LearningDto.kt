package ru.zona.server.feature.learning

import kotlinx.serialization.Serializable

@Serializable
data class CourseDto(
    val id: Long,
    val teacherId: Long,
    val teacherName: String,
    val title: String,
    val description: String,
    val languageFrom: String,
    val languageTo: String,
    val level: String,
    val priceCents: Long?,
    val coverEmoji: String,
    val lessonCount: Int,
    val enrolled: Boolean,
    val progressPercent: Int,
)

@Serializable
data class LessonDto(
    val id: Long,
    val courseId: Long,
    val title: String,
    val sortOrder: Int,
    val exerciseCount: Int,
    val completed: Boolean,
)

@Serializable
data class ExerciseDto(
    val id: Long,
    val lessonId: Long,
    val type: String,
    val prompt: String,
    val choices: List<String>,
    val xp: Int,
    val sortOrder: Int,
)

@Serializable
data class CourseDetailDto(
    val course: CourseDto,
    val lessons: List<LessonDto>,
)

@Serializable
data class SubmitAnswerRequest(val answer: String)

@Serializable
data class SubmitAnswerResponse(val correct: Boolean, val xpEarned: Int, val totalXp: Long)

@Serializable
data class CreateCourseRequest(
    val title: String,
    val description: String = "",
    val languageFrom: String = "",
    val languageTo: String = "",
    val level: String = "A1",
    val priceCents: Long? = null,
    val coverEmoji: String = "🚀",
)

@Serializable
data class CreateLessonRequest(val title: String)

@Serializable
data class CreateExerciseRequest(
    val type: String,
    val prompt: String,
    val choices: List<String> = emptyList(),
    val correctAnswer: String,
    val xp: Int = 10,
)
