package ru.zona.server

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Users : LongIdTable("users") {
    val email = varchar("email", 320).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = enumerationByName("role", 16, UserRole::class)
    val displayName = varchar("display_name", 200)
    val bio = text("bio").default("")
    val avatarBase64 = text("avatar_base64").nullable()
    val languages = varchar("languages", 500).default("")
    val level = varchar("level", 64).default("")
    /** Для преподавателя: «30,60,120» — допустимые длительности индивидуальных уроков (мин). */
    val lessonDurationsCsv = varchar("lesson_durations", 64).default("30,60,120")
}

object Courses : LongIdTable("courses") {
    val title = varchar("title", 500)
    val languageFrom = varchar("language_from", 64)
    val languageTo = varchar("language_to", 64)
    val description = text("description").nullable()
    val teacherId = reference("teacher_id", Users, onDelete = ReferenceOption.CASCADE)
}

object Enrollments : LongIdTable("enrollments") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val courseId = reference("course_id", Courses, onDelete = ReferenceOption.CASCADE)

    init {
        uniqueIndex(userId, courseId)
    }
}

object Lessons : LongIdTable("lessons") {
    val courseId = reference("course_id", Courses, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 300)
    val sortOrder = integer("sort_order").default(0)
}

object Exercises : LongIdTable("exercises") {
    val lessonId = reference("lesson_id", Lessons, onDelete = ReferenceOption.CASCADE)
    val type = enumerationByName("type", 20, ExerciseType::class)
    val prompt = text("prompt")
    val answer = text("answer")
    /** JSON-массив строк неверных вариантов (для CHOICE) */
    val wrongOptionsJson = text("wrong_options_json").nullable()
    val sortOrder = integer("sort_order").default(0)
}

object ExerciseAttempts : LongIdTable("exercise_attempts") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val exerciseId = reference("exercise_id", Exercises, onDelete = ReferenceOption.CASCADE)
    val correct = bool("correct")
    val answeredAtEpochMs = long("answered_at").clientDefault { System.currentTimeMillis() }
}

/** Живые слоты (стиль Verbling): занятие с преподавателем по расписанию */
object LiveSessions : LongIdTable("live_sessions") {
    val courseId = reference("course_id", Courses, onDelete = ReferenceOption.SET_NULL).nullable()
    val teacherId = reference("teacher_id", Users, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 300)
    val description = text("description").default("")
    val imageBase64 = text("image_base64").nullable()
    val startsAtEpochMs = long("starts_at")
    val durationMinutes = integer("duration_minutes").default(60)
    val maxStudents = integer("max_students").default(6)
}

object SessionBookings : LongIdTable("session_bookings") {
    val sessionId = reference("session_id", LiveSessions, onDelete = ReferenceOption.CASCADE)
    val studentId = reference("student_id", Users, onDelete = ReferenceOption.CASCADE)

    init {
        uniqueIndex(sessionId, studentId)
    }
}

/** Запросы на бронь преподавателя учеником */
object TeacherBookingRequests : LongIdTable("teacher_booking_requests") {
    val studentId = reference("student_id", Users, onDelete = ReferenceOption.CASCADE)
    val teacherId = reference("teacher_id", Users, onDelete = ReferenceOption.CASCADE)
    val scheduledAtEpochMs = long("scheduled_at")
    val durationMinutes = integer("duration_minutes").default(60)
    val status = enumerationByName("status", 20, BookingStatus::class).default(BookingStatus.PENDING)
    val createdAtEpochMs = long("created_at").clientDefault { System.currentTimeMillis() }
}

/** Рабочие окна преподавателя: день недели (1=пн … 7=вс) и минуты от полуночи */
object TeacherAvailability : LongIdTable("teacher_availability") {
    val teacherId = reference("teacher_id", Users, onDelete = ReferenceOption.CASCADE)
    val dayOfWeek = integer("day_of_week")
    val startMinute = integer("start_minute")
    val endMinute = integer("end_minute")
}

/** Задания, которые создаёт преподаватель для ученика */
object Assignments : LongIdTable("assignments") {
    val teacherId = reference("teacher_id", Users, onDelete = ReferenceOption.CASCADE)
    val studentId = reference("student_id", Users, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 500)
    val description = text("description")
    val deadlineEpochMs = long("deadline_epoch_ms").nullable()
    val studentResponse = text("student_response").default("")
    val submittedAtEpochMs = long("submitted_at").nullable()
    val createdAtEpochMs = long("created_at").clientDefault { System.currentTimeMillis() }
}

/** Приватные данные "преподаватель <-> ученик": домашка и заметки */
object TeacherStudentMeta : LongIdTable("teacher_student_meta") {
    val teacherId = reference("teacher_id", Users, onDelete = ReferenceOption.CASCADE)
    val studentId = reference("student_id", Users, onDelete = ReferenceOption.CASCADE)
    val homework = text("homework").default("")
    val homeworkResponse = text("homework_response").default("")
    val homeworkSubmittedAtEpochMs = long("homework_submitted_at").nullable()
    val notes = text("notes").default("")

    init {
        uniqueIndex(teacherId, studentId)
    }
}

/** Диалог ученик ↔ преподаватель */
object Conversations : LongIdTable("conversations") {
    val studentId = reference("student_id", Users, onDelete = ReferenceOption.CASCADE)
    val teacherId = reference("teacher_id", Users, onDelete = ReferenceOption.CASCADE)
    val updatedAtEpochMs = long("updated_at").clientDefault { System.currentTimeMillis() }

    init {
        uniqueIndex(studentId, teacherId)
    }
}

object Messages : LongIdTable("messages") {
    val conversationId = reference("conversation_id", Conversations, onDelete = ReferenceOption.CASCADE)
    val senderId = reference("sender_id", Users, onDelete = ReferenceOption.CASCADE)
    val text = text("text")
    val sentAtEpochMs = long("sent_at").clientDefault { System.currentTimeMillis() }
}

/** Заявка ученика на роль преподавателя */
object TeacherApplications : LongIdTable("teacher_applications") {
    val studentId = reference("student_id", Users, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val motivation = text("motivation")
    val attachmentsJson = text("attachments_json").default("[]")
    val status = enumerationByName("status", 20, TeacherApplicationStatus::class).default(TeacherApplicationStatus.PENDING)
    val adminMessage = text("admin_message").nullable()
    val createdAtEpochMs = long("created_at").clientDefault { System.currentTimeMillis() }
    val updatedAtEpochMs = long("updated_at").clientDefault { System.currentTimeMillis() }
}
