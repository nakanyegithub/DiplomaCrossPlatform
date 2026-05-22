package ru.diploma.crossplatform.zona

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val displayName: String,
    val role: String,
    val bio: String = "",
    val avatarBase64: String? = null,
    val languages: String = "",
    val level: String = "",
)

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val languages: String? = null,
    val level: String? = null,
    val avatarBase64: String? = null,
)

@Serializable
data class PublicProfileDto(
    val id: Long,
    val displayName: String,
    val role: String,
    val bio: String = "",
    val avatarBase64: String? = null,
    val languages: String = "",
    val level: String = "",
)

@Serializable
data class TeacherProfileDto(
    val id: Long,
    val name: String,
    val bio: String = "",
    val avatarBase64: String? = null,
    val languages: String = "",
    val level: String = "",
    val allowedLessonMinutes: List<Int> = emptyList(),
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserDto,
)

@Serializable
data class CreateCourseRequest(
    val title: String,
    val languageFrom: String,
    val languageTo: String,
    val description: String? = null,
    val teacherId: Long? = null,
)

@Serializable
data class CreateLessonRequest(
    val title: String,
    val sortOrder: Int = 0,
)

@Serializable
data class CourseDto(
    val id: Long,
    val title: String,
    val languageFrom: String,
    val languageTo: String,
    val description: String? = null,
    val teacherId: Long,
    val teacherName: String,
    val enrolled: Boolean = false,
)

@Serializable
data class LessonDto(
    val id: Long,
    val courseId: Long,
    val title: String,
    val sortOrder: Int,
    val exerciseCount: Int,
    val attemptedByUser: Int,
    val completedByUser: Int,
)

@Serializable
data class ExercisePublicDto(
    val id: Long,
    val lessonId: Long,
    val type: String,
    val prompt: String,
    val choices: List<String>? = null,
)

@Serializable
data class SubmitExerciseRequest(
    val answer: String,
)

@Serializable
data class SubmitExerciseResponse(
    val correct: Boolean,
    val xp: Int,
)

@Serializable
data class LiveSessionDto(
    val id: Long,
    val courseId: Long? = null,
    val courseTitle: String,
    val teacherId: Long,
    val teacherName: String,
    val title: String,
    val description: String = "",
    val imageBase64: String? = null,
    val startsAtEpochMs: Long,
    val durationMinutes: Int,
    val maxStudents: Int,
    val bookedCount: Int,
    val bookedByMe: Boolean,
)

@Serializable
data class CreateLiveSessionRequest(
    val title: String,
    val description: String = "",
    val imageBase64: String? = null,
    val startsAtEpochMs: Long,
    val durationMinutes: Int = 60,
    val maxStudents: Int = 6,
    val courseId: Long? = null,
)

@Serializable
data class ScheduleItemDto(
    val id: Long,
    val type: String,
    val title: String,
    val teacherId: Long,
    val teacherName: String,
    val startsAtEpochMs: Long,
    val durationMinutes: Int,
    val status: String,
)

@Serializable
data class ConversationDto(
    val id: Long,
    val peerId: Long,
    val peerName: String,
    val peerAvatarBase64: String? = null,
    val lastMessage: String? = null,
    val lastMessageAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long,
)

@Serializable
data class ChatMessageDto(
    val id: Long,
    val conversationId: Long,
    val senderId: Long,
    val text: String,
    val sentAtEpochMs: Long,
)

@Serializable
data class SendMessageRequest(
    val text: String,
)

@Serializable
data class ConfirmBookingResponse(
    val message: String,
    val conversationId: Long,
)

@Serializable
data class ConversationIdResponse(
    val conversationId: Long,
)

@Serializable
data class CourseProgressDto(
    val courseId: Long,
    val courseTitle: String,
    val completedLessons: Int,
    val totalLessons: Int,
    val completedExercises: Int,
    val totalExercises: Int,
)

@Serializable
data class TeacherStudentDto(
    val studentId: Long,
    val studentName: String,
    val studentEmail: String,
    val courseProgress: List<CourseProgressDto>,
    val lastActivityEpochMs: Long? = null,
    val teacherHistory: List<TeacherHistoryEntryDto> = emptyList(),
    val homework: String,
    val homeworkResponse: String = "",
    val homeworkSubmittedAtEpochMs: Long? = null,
    val notes: String,
)

@Serializable
data class AvailabilityRangeDto(
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
)

@Serializable
data class ReplaceAvailabilityRequest(
    val ranges: List<AvailabilityRangeDto>,
    val allowedDurationMinutes: List<Int> = listOf(30, 60, 120),
)

@Serializable
data class TeacherAvailabilityDto(
    val ranges: List<AvailabilityRangeDto>,
    val allowedDurationMinutes: List<Int> = listOf(30, 60, 120),
)

@Serializable
data class BookingSlotDto(
    val epochMs: Long,
    val label: String,
    val durationMinutes: Int = 60,
)

@Serializable
data class AssignmentDto(
    val id: Long,
    val teacherId: Long,
    val teacherName: String,
    val studentId: Long,
    val studentName: String = "",
    val title: String,
    val description: String,
    val deadlineEpochMs: Long? = null,
    val studentResponse: String = "",
    val submittedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long,
)

@Serializable
data class CreateAssignmentRequest(
    val title: String,
    val description: String,
    val deadlineEpochMs: Long? = null,
)

@Serializable
data class StudentHomeworkDto(
    val teacherId: Long,
    val teacherName: String,
    val homework: String,
    val studentResponse: String = "",
    val submittedAtEpochMs: Long? = null,
)

@Serializable
data class UpdateTextRequest(
    val text: String,
)

@Serializable
data class TeacherShortDto(
    val id: Long,
    val name: String,
)

@Serializable
data class CreateTeacherBookingRequest(
    val scheduledAtEpochMs: Long,
    val durationMinutes: Int = 60,
)

@Serializable
data class TeacherBookingRequestDto(
    val id: Long,
    val studentId: Long,
    val studentName: String,
    val studentEmail: String,
    val teacherId: Long,
    val teacherName: String,
    val scheduledAtEpochMs: Long,
    val durationMinutes: Int = 60,
    val status: String,
)

@Serializable
data class TeacherHistoryEntryDto(
    val teacherId: Long,
    val teacherName: String,
    val scheduledAtEpochMs: Long,
    val status: String,
)

@Serializable
data class LeaderboardEntryDto(
    val userId: Long,
    val userName: String,
    val totalXp: Int,
    val totalCorrect: Int,
    val totalAttempts: Int,
)

@Serializable
data class TeacherApplicationAttachmentDto(
    val fileName: String,
    val mimeType: String = "application/octet-stream",
    val dataBase64: String,
)

@Serializable
data class TeacherApplicationDto(
    val id: Long,
    val studentId: Long,
    val studentName: String,
    val studentEmail: String,
    val motivation: String,
    val attachments: List<TeacherApplicationAttachmentDto>,
    val status: String,
    val adminMessage: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Serializable
data class SubmitTeacherApplicationRequest(
    val motivation: String,
    val attachments: List<TeacherApplicationAttachmentDto> = emptyList(),
)

@Serializable
data class AdminTeacherApplicationActionRequest(
    val message: String? = null,
)

@Serializable
data class ErrorResponse(
    val message: String,
)
