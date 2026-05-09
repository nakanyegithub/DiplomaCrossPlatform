package ru.zona.server

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val displayName: String,
    val role: String,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
)

@Serializable
data class CreateUserRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val role: String,
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
data class CourseDto(
    val id: Long,
    val title: String,
    val languageFrom: String,
    val languageTo: String,
    val description: String?,
    val teacherId: Long,
    val teacherName: String,
    val enrolled: Boolean = false,
)

@Serializable
data class CreateCourseRequest(
    val title: String,
    val languageFrom: String,
    val languageTo: String,
    val description: String? = null,
    /** Только для админа: назначить преподавателя */
    val teacherId: Long? = null,
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
data class CreateLessonRequest(
    val title: String,
    val sortOrder: Int = 0,
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
data class CreateExerciseRequest(
    val type: String,
    val prompt: String,
    val answer: String,
    val wrongOptions: List<String>? = null,
    val sortOrder: Int = 0,
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
    val courseId: Long,
    val courseTitle: String,
    val teacherId: Long,
    val teacherName: String,
    val title: String,
    val startsAtEpochMs: Long,
    val durationMinutes: Int,
    val maxStudents: Int,
    val bookedCount: Int,
    val bookedByMe: Boolean,
)

@Serializable
data class CreateLiveSessionRequest(
    val courseId: Long,
    val title: String,
    val startsAtEpochMs: Long,
    val durationMinutes: Int = 60,
    val maxStudents: Int = 6,
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
    val lastActivityEpochMs: Long?,
    val teacherHistory: List<TeacherHistoryEntryDto>,
    val homework: String,
    val notes: String,
)

@Serializable
data class StudentHomeworkDto(
    val teacherId: Long,
    val teacherName: String,
    val homework: String,
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
data class ErrorResponse(
    val message: String,
)
