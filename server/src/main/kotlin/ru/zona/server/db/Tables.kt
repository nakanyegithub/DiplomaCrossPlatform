package ru.zona.server.db

import org.jetbrains.exposed.sql.Table

/** Реестр таблиц. Создаётся целиком при старте (createMissingTablesAndColumns). */
object Tables {
    val all: Array<Table>
        get() =
            arrayOf(
                Users,
                TeacherApplications,
                TeacherApplicationDocs,
                Wallets,
                WalletTransactions,
                Courses,
                Enrollments,
                Lessons,
                Exercises,
                ExerciseAttempts,
                LessonProgress,
                Decks,
                Flashcards,
                FlashcardReviews,
                Sessions,
                SessionBookings,
                Conversations,
                Messages,
            )
}

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val displayName = varchar("display_name", 255)
    val role = varchar("role", 32) // STUDENT | TEACHER | ADMIN
    val bio = text("bio").default("")
    val avatarUrl = text("avatar_url").nullable() // base64-картинка или null
    val headline = varchar("headline", 255).default("")
    val pricePerHourCents = long("price_per_hour_cents").nullable()
    val ratingSum = long("rating_sum").default(0)
    val ratingCount = long("rating_count").default(0)
    val xp = long("xp").default(0)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TeacherApplications : Table("teacher_applications") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val motivation = text("motivation")
    val headline = varchar("headline", 255).default("")
    val status = varchar("status", 32) // PENDING | NEED_INFO | APPROVED | REJECTED
    val adminMessage = text("admin_message").nullable()
    val createdAt = long("created_at")
    val decidedAt = long("decided_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object TeacherApplicationDocs : Table("teacher_application_docs") {
    val id = long("id").autoIncrement()
    val applicationId = long("application_id").references(TeacherApplications.id)
    val fileName = varchar("file_name", 512)
    val description = varchar("description", 1024).default("")
    override val primaryKey = PrimaryKey(id)
}

object Wallets : Table("wallets") {
    val userId = long("user_id").references(Users.id)
    val balanceCents = long("balance_cents").default(0)
    override val primaryKey = PrimaryKey(userId)
}

object WalletTransactions : Table("wallet_transactions") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val amountCents = long("amount_cents") // + пополнение, - покупка
    val kind = varchar("kind", 32) // TOPUP | PURCHASE
    val note = varchar("note", 512).default("")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Courses : Table("courses") {
    val id = long("id").autoIncrement()
    val teacherId = long("teacher_id").references(Users.id)
    val title = varchar("title", 255)
    val description = text("description").default("")
    val languageFrom = varchar("language_from", 64).default("")
    val languageTo = varchar("language_to", 64).default("")
    val level = varchar("level", 32).default("A1")
    val priceCents = long("price_cents").nullable() // null = бесплатно
    val coverEmoji = varchar("cover_emoji", 16).default("🚀")
    val galleryJson = text("gallery_json").nullable() // JSON-массив base64-изображений
    val published = bool("published").default(true)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Enrollments : Table("enrollments") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val courseId = long("course_id").references(Courses.id)
    val paidCents = long("paid_cents").default(0)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex(userId, courseId) }
}

object Lessons : Table("lessons") {
    val id = long("id").autoIncrement()
    val courseId = long("course_id").references(Courses.id)
    val title = varchar("title", 255)
    val sortOrder = integer("sort_order").default(0)
    override val primaryKey = PrimaryKey(id)
}

object Exercises : Table("exercises") {
    val id = long("id").autoIncrement()
    val lessonId = long("lesson_id").references(Lessons.id)
    val type = varchar("type", 32) // SINGLE_CHOICE | MULTI_CHOICE | TEXT_INPUT
    val prompt = text("prompt")
    val choicesJson = text("choices_json").nullable() // JSON array строк
    val correctAnswer = text("correct_answer") // индекс(ы) или текст
    val xp = integer("xp").default(10)
    val sortOrder = integer("sort_order").default(0)
    override val primaryKey = PrimaryKey(id)
}

object ExerciseAttempts : Table("exercise_attempts") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val exerciseId = long("exercise_id").references(Exercises.id)
    val answer = text("answer")
    val correct = bool("correct")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object LessonProgress : Table("lesson_progress") {
    val userId = long("user_id").references(Users.id)
    val lessonId = long("lesson_id").references(Lessons.id)
    val completed = bool("completed").default(false)
    val completedAt = long("completed_at").nullable()
    override val primaryKey = PrimaryKey(userId, lessonId)
}

object Decks : Table("decks") {
    val id = long("id").autoIncrement()
    val courseId = long("course_id").references(Courses.id).nullable()
    val ownerId = long("owner_id").references(Users.id)
    val title = varchar("title", 255)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Flashcards : Table("flashcards") {
    val id = long("id").autoIncrement()
    val deckId = long("deck_id").references(Decks.id)
    val front = text("front")
    val back = text("back")
    val sortOrder = integer("sort_order").default(0)
    override val primaryKey = PrimaryKey(id)
}

object FlashcardReviews : Table("flashcard_reviews") {
    val userId = long("user_id").references(Users.id)
    val flashcardId = long("flashcard_id").references(Flashcards.id)
    val box = integer("box").default(0) // Leitner box 0..5
    val dueAt = long("due_at").default(0)
    override val primaryKey = PrimaryKey(userId, flashcardId)
}

object Sessions : Table("sessions") {
    val id = long("id").autoIncrement()
    val teacherId = long("teacher_id").references(Users.id)
    val courseId = long("course_id").references(Courses.id).nullable()
    val type = varchar("type", 32) // GROUP | INDIVIDUAL
    val title = varchar("title", 255)
    val description = text("description").default("")
    val startsAt = long("starts_at")
    val durationMinutes = integer("duration_minutes").default(60)
    val capacity = integer("capacity").default(1)
    val priceCents = long("price_cents").nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object SessionBookings : Table("session_bookings") {
    val id = long("id").autoIncrement()
    val sessionId = long("session_id").references(Sessions.id)
    val studentId = long("student_id").references(Users.id)
    val status = varchar("status", 32).default("BOOKED") // BOOKED | CANCELLED
    val paidCents = long("paid_cents").default(0)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex(sessionId, studentId) }
}

object Conversations : Table("conversations") {
    val id = long("id").autoIncrement()
    val userA = long("user_a").references(Users.id) // min(id)
    val userB = long("user_b").references(Users.id) // max(id)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex(userA, userB) }
}

object Messages : Table("messages") {
    val id = long("id").autoIncrement()
    val conversationId = long("conversation_id").references(Conversations.id)
    val senderId = long("sender_id").references(Users.id)
    val text = text("text")
    val sentAt = long("sent_at")
    val readAt = long("read_at").nullable()
    override val primaryKey = PrimaryKey(id)
}
