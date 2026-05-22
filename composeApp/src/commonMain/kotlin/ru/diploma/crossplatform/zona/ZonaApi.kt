package ru.diploma.crossplatform.zona

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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

    suspend fun createCourse(request: CreateCourseRequest): CourseDto =
        client.post("${base()}/api/courses") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireOk()

    suspend fun createLesson(
        courseId: Long,
        request: CreateLessonRequest,
    ): LessonDto =
        client.post("${base()}/api/courses/$courseId/lessons") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireOk()

    suspend fun teachers(): List<TeacherProfileDto> =
        client.get("${base()}/api/teachers") { authHeader() }.requireOk()

    suspend fun teacherGroupSessions(teacherId: Long): List<LiveSessionDto> =
        client.get("${base()}/api/teachers/$teacherId/sessions") { authHeader() }.requireOk()

    suspend fun studentSchedule(): List<ScheduleItemDto> =
        client.get("${base()}/api/student/schedule") { authHeader() }.requireOk()

    suspend fun updateProfile(request: UpdateProfileRequest): UserDto =
        client.patch("${base()}/api/me") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireOk()

    suspend fun publicProfile(userId: Long): PublicProfileDto =
        client.get("${base()}/api/users/$userId/profile") { authHeader() }.requireOk()

    suspend fun conversations(): List<ConversationDto> =
        client.get("${base()}/api/conversations") { authHeader() }.requireOk()

    suspend fun chatMessages(conversationId: Long): List<ChatMessageDto> =
        client.get("${base()}/api/conversations/$conversationId/messages") { authHeader() }.requireOk()

    suspend fun sendChatMessage(
        conversationId: Long,
        text: String,
    ): ChatMessageDto =
        client.post("${base()}/api/conversations/$conversationId/messages") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(SendMessageRequest(text))
        }.requireOk()

    suspend fun openConversationWith(userId: Long): Long =
        client.post("${base()}/api/conversations/with/$userId") { authHeader() }.requireOk<ConversationIdResponse>().conversationId

    suspend fun createLiveSession(request: CreateLiveSessionRequest): LiveSessionDto =
        client.post("${base()}/api/sessions") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireOk()

    suspend fun teacherGroupSessions(): List<LiveSessionDto> =
        client.get("${base()}/api/teacher/sessions") { authHeader() }.requireOk()

    suspend fun enroll(courseId: Long) {
        client.post("${base()}/api/courses/$courseId/enroll") { authHeader() }.requireOk<ErrorResponse>()
    }

    suspend fun requestTeacherBooking(
        teacherId: Long,
        scheduledAtEpochMs: Long,
        durationMinutes: Int,
    ) {
        client.post("${base()}/api/teachers/$teacherId/booking-requests") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(CreateTeacherBookingRequest(scheduledAtEpochMs, durationMinutes))
        }.requireOk<ErrorResponse>()
    }

    suspend fun teacherBookingSlots(
        teacherId: Long,
        durationMinutes: Int,
        days: Int = 14,
    ): List<BookingSlotDto> =
        client
            .get("${base()}/api/teachers/$teacherId/booking-slots?days=$days&durationMinutes=$durationMinutes") {
                authHeader()
            }.requireOk()

    suspend fun teacherAvailability(): TeacherAvailabilityDto =
        client.get("${base()}/api/teacher/availability") { authHeader() }.requireOk()

    suspend fun saveTeacherAvailability(
        ranges: List<AvailabilityRangeDto>,
        allowedDurationMinutes: List<Int>,
    ) {
        client.post("${base()}/api/teacher/availability") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(ReplaceAvailabilityRequest(ranges, allowedDurationMinutes))
        }.requireOk<ErrorResponse>()
    }

    suspend fun studentAssignments(): List<AssignmentDto> =
        client.get("${base()}/api/student/assignments") { authHeader() }.requireOk()

    suspend fun submitAssignment(
        assignmentId: Long,
        response: String,
    ) {
        client.post("${base()}/api/student/assignments/$assignmentId/submit") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(UpdateTextRequest(response))
        }.requireOk<ErrorResponse>()
    }

    suspend fun teacherAssignments(): List<AssignmentDto> =
        client.get("${base()}/api/teacher/assignments") { authHeader() }.requireOk()

    suspend fun createAssignment(
        studentId: Long,
        request: CreateAssignmentRequest,
    ) {
        client.post("${base()}/api/teacher/students/$studentId/assignments") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(request)
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

    suspend fun confirmBookingRequest(requestId: Long): ConfirmBookingResponse =
        client.post("${base()}/api/teacher/booking-requests/$requestId/confirm") { authHeader() }.requireOk()

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

    suspend fun submitHomework(
        teacherId: Long,
        response: String,
    ) {
        client.post("${base()}/api/student/homework/$teacherId/submit") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(UpdateTextRequest(response))
        }.requireOk<ErrorResponse>()
    }

    suspend fun leaderboard(): List<LeaderboardEntryDto> =
        client.get("${base()}/api/leaderboard") { authHeader() }.requireOk()

    suspend fun myTeacherApplication(): TeacherApplicationDto? {
        val response = client.get("${base()}/api/student/teacher-application") { authHeader() }
        if (response.status == HttpStatusCode.NotFound) return null
        return response.requireOk()
    }

    suspend fun submitTeacherApplication(request: SubmitTeacherApplicationRequest): TeacherApplicationDto =
        client.post("${base()}/api/student/teacher-application") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireOk()

    suspend fun adminTeacherApplications(): List<TeacherApplicationDto> =
        client.get("${base()}/api/admin/teacher-applications") { authHeader() }.requireOk()

    suspend fun approveTeacherApplication(id: Long) {
        client.post("${base()}/api/admin/teacher-applications/$id/approve") { authHeader() }.requireOk<ErrorResponse>()
    }

    suspend fun rejectTeacherApplication(
        id: Long,
        message: String?,
    ) {
        client.post("${base()}/api/admin/teacher-applications/$id/reject") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(AdminTeacherApplicationActionRequest(message))
        }.requireOk<ErrorResponse>()
    }

    suspend fun requestTeacherApplicationInfo(
        id: Long,
        message: String,
    ) {
        client.post("${base()}/api/admin/teacher-applications/$id/request-info") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(AdminTeacherApplicationActionRequest(message))
        }.requireOk<ErrorResponse>()
    }
}
