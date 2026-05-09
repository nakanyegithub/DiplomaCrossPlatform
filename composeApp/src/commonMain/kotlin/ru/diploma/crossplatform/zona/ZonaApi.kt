package ru.diploma.crossplatform.zona

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

class ZonaApiException(
    message: String,
) : Exception(message)

class ZonaApi(
    private val client: HttpClient,
) {
    var bearerToken: String? = null

    private fun base(): String = zonaApiBaseUrl().trimEnd('/')

    private suspend inline fun <reified T> HttpResponse.requireOk(): T {
        if (status.value in 200..299) return body()
        val msg = runCatching { body<ErrorResponse>() }.getOrNull()?.message ?: "$status"
        throw ZonaApiException(msg)
    }

    private fun HttpRequestBuilder.authHeader() {
        bearerToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    suspend fun login(
        email: String,
        password: String,
    ): AuthResponse {
        val r =
            client.post("${base()}/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }
        return r.requireOk()
    }

    suspend fun register(
        email: String,
        password: String,
        displayName: String,
    ): AuthResponse {
        val r =
            client.post("${base()}/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(email, password, displayName))
            }
        return r.requireOk()
    }

    suspend fun me(): UserDto =
        client.get("${base()}/api/me") { authHeader() }.requireOk()

    suspend fun courses(): List<CourseDto> =
        client.get("${base()}/api/courses") { authHeader() }.requireOk()

    suspend fun teachers(): List<TeacherShortDto> =
        client.get("${base()}/api/teachers") { authHeader() }.requireOk()

    suspend fun enroll(courseId: Long) {
        client.post("${base()}/api/courses/$courseId/enroll") { authHeader() }.requireOk<ErrorResponse>()
    }

    suspend fun requestTeacherBooking(
        teacherId: Long,
        scheduledAtEpochMs: Long,
    ) {
        client.post("${base()}/api/teachers/$teacherId/booking-requests") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(CreateTeacherBookingRequest(scheduledAtEpochMs))
        }.requireOk<ErrorResponse>()
    }

    suspend fun lessons(courseId: Long): List<LessonDto> =
        client.get("${base()}/api/courses/$courseId/lessons") { authHeader() }.requireOk()

    suspend fun exercises(lessonId: Long): List<ExercisePublicDto> =
        client.get("${base()}/api/lessons/$lessonId/exercises") { authHeader() }.requireOk()

    suspend fun submitExercise(
        exerciseId: Long,
        answer: String,
    ): SubmitExerciseResponse =
        client.post("${base()}/api/exercises/$exerciseId/submit") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(SubmitExerciseRequest(answer))
        }.requireOk()

    suspend fun sessions(): List<LiveSessionDto> =
        client.get("${base()}/api/sessions") { authHeader() }.requireOk()

    suspend fun bookSession(sessionId: Long) {
        client.post("${base()}/api/sessions/$sessionId/book") { authHeader() }.requireOk<ErrorResponse>()
    }

    suspend fun adminUsers(): List<UserDto> =
        client.get("${base()}/api/admin/users") { authHeader() }.requireOk()

    suspend fun teacherStudents(): List<TeacherStudentDto> =
        client.get("${base()}/api/teacher/students") { authHeader() }.requireOk()

    suspend fun teacherBookingRequests(): List<TeacherBookingRequestDto> =
        client.get("${base()}/api/teacher/booking-requests") { authHeader() }.requireOk()

    suspend fun confirmBookingRequest(requestId: Long) {
        client.post("${base()}/api/teacher/booking-requests/$requestId/confirm") { authHeader() }.requireOk<ErrorResponse>()
    }

    suspend fun declineBookingRequest(requestId: Long) {
        client.post("${base()}/api/teacher/booking-requests/$requestId/decline") { authHeader() }.requireOk<ErrorResponse>()
    }

    suspend fun assignHomework(
        studentId: Long,
        text: String,
    ) {
        client.post("${base()}/api/teacher/students/$studentId/homework") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(UpdateTextRequest(text))
        }.requireOk<ErrorResponse>()
    }

    suspend fun saveTeacherNote(
        studentId: Long,
        text: String,
    ) {
        client.post("${base()}/api/teacher/students/$studentId/notes") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(UpdateTextRequest(text))
        }.requireOk<ErrorResponse>()
    }

    suspend fun studentHomework(): List<StudentHomeworkDto> =
        client.get("${base()}/api/student/homework") { authHeader() }.requireOk()

    suspend fun leaderboard(): List<LeaderboardEntryDto> =
        client.get("${base()}/api/leaderboard") { authHeader() }.requireOk()
}
