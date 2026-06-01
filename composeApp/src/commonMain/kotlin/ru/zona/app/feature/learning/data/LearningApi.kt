package ru.zona.app.feature.learning.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
    val priceCents: Long? = null,
    val coverEmoji: String = "🚀",
    val lessonCount: Int = 0,
    val enrolled: Boolean = false,
    val progressPercent: Int = 0,
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
    val choices: List<String> = emptyList(),
    val xp: Int = 10,
    val sortOrder: Int = 0,
)

@Serializable
data class CourseDetailDto(val course: CourseDto, val lessons: List<LessonDto>)

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

class LearningApi(private val client: HttpClient, private val baseUrl: String) {
    suspend fun catalog(query: String?): HttpResponse =
        client.get("$baseUrl/api/courses") { if (!query.isNullOrBlank()) parameter("q", query) }

    suspend fun myCourses(): HttpResponse = client.get("$baseUrl/api/courses/my")

    suspend fun detail(courseId: Long): HttpResponse = client.get("$baseUrl/api/courses/$courseId")

    suspend fun enroll(courseId: Long): HttpResponse = client.post("$baseUrl/api/courses/$courseId/enroll")

    suspend fun exercises(lessonId: Long): HttpResponse = client.get("$baseUrl/api/lessons/$lessonId/exercises")

    suspend fun submit(exerciseId: Long, answer: String): HttpResponse =
        client.post("$baseUrl/api/exercises/$exerciseId/submit") { setBody(SubmitAnswerRequest(answer)) }

    suspend fun createCourse(body: CreateCourseRequest): HttpResponse =
        client.post("$baseUrl/api/courses") { setBody(body) }

    suspend fun addLesson(courseId: Long, body: CreateLessonRequest): HttpResponse =
        client.post("$baseUrl/api/courses/$courseId/lessons") { setBody(body) }

    suspend fun addExercise(lessonId: Long, body: CreateExerciseRequest): HttpResponse =
        client.post("$baseUrl/api/lessons/$lessonId/exercises") { setBody(body) }
}
