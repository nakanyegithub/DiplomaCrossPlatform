package ru.zona.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val jsonFmt = Json { ignoreUnknownKeys = true }

private fun normalizeAnswer(s: String): String =
    s.trim().lowercase().replace(Regex("\\s+"), " ")

fun Application.configureRouting(jwt: JwtSupport) {
    routing {
        route("/api") {
            post("/auth/register") {
                val body = call.receive<RegisterRequest>()
                val created =
                    transaction {
                        if (Users.selectAll().where(Users.email.lowerCase() eq body.email.lowercase()).any()) {
                            return@transaction null
                        }
                        val id =
                            (Users.insert {
                                it[Users.email] = body.email.trim().lowercase()
                                it[Users.passwordHash] = hashPassword(body.password)
                                it[Users.role] = UserRole.STUDENT
                                it[Users.displayName] = body.displayName.trim()
                            } get Users.id).value
                        id
                    }
                if (created == null) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse("Email уже занят"))
                    return@post
                }
                val user = transaction { loadUser(created)!! }
                val token = jwt.token(created, UserRole.STUDENT)
                call.respond(AuthResponse(token, user))
            }

            post("/auth/login") {
                val body = call.receive<LoginRequest>()
                val row =
                    transaction {
                        Users
                            .selectAll()
                            .where(Users.email.lowerCase() eq body.email.trim().lowercase())
                            .firstOrNull()
                    }
                if (row == null || !verifyPassword(body.password, row[Users.passwordHash])) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Неверный email или пароль"))
                    return@post
                }
                val id = row[Users.id].value
                val role = row[Users.role]
                val user = transaction { loadUser(id)!! }
                call.respond(AuthResponse(jwt.token(id, role), user))
            }

            authenticate("jwt") {
                get("/me") {
                    val p = call.principal<ZonaPrincipal>()!!
                    val u = transaction { loadUser(p.userId)!! }
                    call.respond(u)
                }

                get("/courses") {
                    val p = call.principal<ZonaPrincipal>()!!
                    val list = transaction { listCoursesForUser(p.userId) }
                    call.respond(list)
                }

                post("/courses") {
                    val p = call.principal<ZonaPrincipal>()!!
                    if (p.role != UserRole.TEACHER && p.role != UserRole.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Недостаточно прав"))
                        return@post
                    }
                    val body = call.receive<CreateCourseRequest>()
                    val teacherId =
                        when (p.role) {
                            UserRole.TEACHER -> p.userId
                            UserRole.ADMIN ->
                                body.teacherId ?: run {
                                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Укажите teacherId"))
                                    return@post
                                }
                            else -> return@post
                        }
                    val okTeacher =
                        transaction {
                            val r = Users.selectAll().where(Users.id eq teacherId).firstOrNull()
                            r != null && r[Users.role] == UserRole.TEACHER
                        }
                    if (!okTeacher) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Некорректный преподаватель"))
                        return@post
                    }
                    val newId =
                        transaction {
                            (Courses.insert {
                                it[Courses.title] = body.title.trim()
                                it[Courses.languageFrom] = body.languageFrom.trim()
                                it[Courses.languageTo] = body.languageTo.trim()
                                it[Courses.description] = body.description?.trim()
                                it[Courses.teacherId] = teacherId
                            } get Courses.id).value
                        }
                    call.respond(transaction { loadCourse(newId, p.userId)!! })
                }

                get("/courses/{id}/lessons") {
                    val p = call.principal<ZonaPrincipal>()!!
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Некорректный id"))
                        return@get
                    }
                    val lessons = transaction { listLessons(id, p.userId) }
                    call.respond(lessons)
                }

                post("/courses/{id}/enroll") {
                    val p = call.principal<ZonaPrincipal>()!!
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Некорректный id"))
                        return@post
                    }
                    val inserted =
                        transaction {
                            if (Courses.selectAll().where(Courses.id eq id).none()) return@transaction false
                            if (Enrollments.selectAll().where(Enrollments.userId eq p.userId and (Enrollments.courseId eq id)).any()) {
                                return@transaction true
                            }
                            Enrollments.insert {
                                it[Enrollments.userId] = p.userId
                                it[Enrollments.courseId] = id
                            }
                            true
                        }
                    if (!inserted) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Курс не найден"))
                        return@post
                    }
                    call.respond(ErrorResponse("Записались на курс"))
                }

                post("/courses/{id}/lessons") {
                    val p = call.principal<ZonaPrincipal>()!!
                    val id = call.parameters["id"]?.toLongOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Некорректный id"))
                        return@post
                    }
                    if (!transaction { canManageCourse(p, id) }) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Нет доступа к курсу"))
                        return@post
                    }
                    val body = call.receive<CreateLessonRequest>()
                    val lid =
                        transaction {
                            (Lessons.insert {
                                it[Lessons.courseId] = id
                                it[Lessons.title] = body.title.trim()
                                it[Lessons.sortOrder] = body.sortOrder
                            } get Lessons.id).value
                        }
                    call.respond(transaction { loadLesson(lid, p.userId)!! })
                }

                get("/lessons/{id}/exercises") {
                    val p = call.principal<ZonaPrincipal>()!!
                    val id = call.parameters["id"]?.toLongOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Некорректный id"))
                        return@get
                    }
                    val list = transaction { listPublicExercises(id, p.userId) }
                    call.respond(list)
                }

                post("/exercises/{id}/submit") {
                    val p = call.principal<ZonaPrincipal>()!!
                    val id = call.parameters["id"]?.toLongOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Некорректный id"))
                        return@post
                    }
                    val body = call.receive<SubmitExerciseRequest>()
                    val result =
                        transaction {
                            val ex =
                                Exercises
                                    .selectAll()
                                    .where(Exercises.id eq id)
                                    .firstOrNull() ?: return@transaction null
                            val alreadySolved =
                                ExerciseAttempts
                                    .selectAll()
                                    .where(
                                        (ExerciseAttempts.userId eq p.userId) and
                                            (ExerciseAttempts.exerciseId eq id) and
                                            (ExerciseAttempts.correct eq true),
                                    ).any()
                            val correct =
                                when (ex[Exercises.type]) {
                                    ExerciseType.CHOICE,
                                    ExerciseType.TRANSLATION,
                                    -> normalizeAnswer(body.answer) == normalizeAnswer(ex[Exercises.answer])
                                }
                            if (!alreadySolved || !correct) {
                                ExerciseAttempts.insert {
                                    it[ExerciseAttempts.userId] = p.userId
                                    it[ExerciseAttempts.exerciseId] = id
                                    it[ExerciseAttempts.correct] = correct
                                }
                            }
                            SubmitExerciseResponse(
                                correct = correct,
                                xp = if (correct && !alreadySolved) 10 else 0,
                            )
                        }
                    if (result == null) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Задание не найдено"))
                        return@post
                    }
                    call.respond(result)
                }

                get("/sessions") {
                    val p = call.principal<ZonaPrincipal>()!!
                    val list = transaction { listLiveSessions(p.userId) }
                    call.respond(list)
                }

                post("/sessions") {
                    val pr = call.principal<ZonaPrincipal>()!!
                    if (pr.role != UserRole.TEACHER && pr.role != UserRole.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Только преподаватель"))
                        return@post
                    }
                    val body = call.receive<CreateLiveSessionRequest>()
                    val owns =
                        transaction {
                            val c = Courses.selectAll().where(Courses.id eq body.courseId).firstOrNull()
                            c != null && (c[Courses.teacherId].value == pr.userId || pr.role == UserRole.ADMIN)
                        }
                    if (!owns) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Курс не ваш"))
                        return@post
                    }
                    val teacherId =
                        transaction {
                            Courses
                                .selectAll()
                                .where(Courses.id eq body.courseId)
                                .first()[Courses.teacherId]
                                .value
                        }
                    val sid =
                        transaction {
                            (LiveSessions.insert {
                                it[LiveSessions.courseId] = body.courseId
                                it[LiveSessions.teacherId] = teacherId
                                it[LiveSessions.title] = body.title.trim()
                                it[LiveSessions.startsAtEpochMs] = body.startsAtEpochMs
                                it[LiveSessions.durationMinutes] = body.durationMinutes
                                it[LiveSessions.maxStudents] = body.maxStudents
                            } get LiveSessions.id).value
                        }
                    call.respond(transaction { loadSession(sid, pr.userId)!! })
                }

                post("/sessions/{id}/book") {
                    val p = call.principal<ZonaPrincipal>()!!
                    if (p.role != UserRole.STUDENT && p.role != UserRole.TEACHER && p.role != UserRole.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Недоступно"))
                        return@post
                    }
                    val id = call.parameters["id"]?.toLongOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Некорректный id"))
                        return@post
                    }
                    val ok =
                        transaction {
                            val s = LiveSessions.selectAll().where(LiveSessions.id eq id).firstOrNull() ?: return@transaction false
                            val booked =
                                SessionBookings
                                    .selectAll()
                                    .where(SessionBookings.sessionId eq id)
                                    .count()
                            if (booked >= s[LiveSessions.maxStudents]) return@transaction false
                            if (SessionBookings
                                    .selectAll()
                                    .where(SessionBookings.sessionId eq id and (SessionBookings.studentId eq p.userId))
                                    .any()
                            ) {
                                return@transaction true
                            }
                            SessionBookings.insert {
                                it[SessionBookings.sessionId] = id
                                it[SessionBookings.studentId] = p.userId
                            }
                            true
                        }
                    if (!ok) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Не удалось записаться (мест нет или слот не найден)"))
                        return@post
                    }
                    call.respond(ErrorResponse("Вы записаны на занятие"))
                }

                get("/teachers") {
                    val teachers =
                        transaction {
                            Users
                                .selectAll()
                                .where(Users.role eq UserRole.TEACHER)
                                .map {
                                    TeacherShortDto(
                                        id = it[Users.id].value,
                                        name = it[Users.displayName],
                                    )
                                }
                        }
                    call.respond(teachers)
                }

                post("/teachers/{id}/booking-requests") {
                    val p = call.principal<ZonaPrincipal>()!!
                    if (p.role != UserRole.STUDENT && p.role != UserRole.ADMIN && p.role != UserRole.TEACHER) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Недоступно"))
                        return@post
                    }
                    val teacherId = call.parameters["id"]?.toLongOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Некорректный id преподавателя"))
                        return@post
                    }
                    val body = call.receive<CreateTeacherBookingRequest>()
                    val ok = transaction { createTeacherBookingRequest(p.userId, teacherId, body.scheduledAtEpochMs) }
                    if (!ok) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Не удалось создать заявку"))
                        return@post
                    }
                    call.respond(ErrorResponse("Заявка отправлена преподавателю"))
                }

                get("/teacher/booking-requests") {
                    val p = call.principal<ZonaPrincipal>()!!
                    if (p.role != UserRole.TEACHER && p.role != UserRole.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Только преподаватель"))
                        return@get
                    }
                    val list = transaction { listBookingRequestsForTeacher(p.userId) }
                    call.respond(list)
                }

                post("/teacher/booking-requests/{id}/confirm") {
                    val p = call.principal<ZonaPrincipal>()!!
                    if (p.role != UserRole.TEACHER && p.role != UserRole.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Только преподаватель"))
                        return@post
                    }
                    val requestId = call.parameters["id"]?.toLongOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Некорректный id заявки"))
                        return@post
                    }
                    val ok = transaction { updateBookingRequestStatus(p.userId, requestId, BookingStatus.CONFIRMED) }
                    if (!ok) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Заявка не найдена"))
                        return@post
                    }
                    call.respond(ErrorResponse("Заявка подтверждена"))
                }

                post("/teacher/booking-requests/{id}/decline") {
                    val p = call.principal<ZonaPrincipal>()!!
                    if (p.role != UserRole.TEACHER && p.role != UserRole.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Только преподаватель"))
                        return@post
                    }
                    val requestId = call.parameters["id"]?.toLongOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Некорректный id заявки"))
                        return@post
                    }
                    val ok = transaction { updateBookingRequestStatus(p.userId, requestId, BookingStatus.DECLINED) }
                    if (!ok) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Заявка не найдена"))
                        return@post
                    }
                    call.respond(ErrorResponse("Заявка отклонена"))
                }

                get("/teacher/students") {
                    val p = call.principal<ZonaPrincipal>()!!
                    if (p.role != UserRole.TEACHER && p.role != UserRole.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Только преподаватель"))
                        return@get
                    }
                    val students = transaction { listTeacherStudents(p.userId) }
                    call.respond(students)
                }

                post("/teacher/students/{id}/homework") {
                    val p = call.principal<ZonaPrincipal>()!!
                    if (p.role != UserRole.TEACHER && p.role != UserRole.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Только преподаватель"))
                        return@post
                    }
                    val studentId = call.parameters["id"]?.toLongOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Некорректный id ученика"))
                        return@post
                    }
                    val body = call.receive<UpdateTextRequest>()
                    val updated = transaction { setTeacherHomework(p.userId, studentId, body.text.trim()) }
                    if (!updated) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Ученик не найден среди ваших записей"))
                        return@post
                    }
                    call.respond(ErrorResponse("Домашнее задание сохранено"))
                }

                post("/teacher/students/{id}/notes") {
                    val p = call.principal<ZonaPrincipal>()!!
                    if (p.role != UserRole.TEACHER && p.role != UserRole.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Только преподаватель"))
                        return@post
                    }
                    val studentId = call.parameters["id"]?.toLongOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Некорректный id ученика"))
                        return@post
                    }
                    val body = call.receive<UpdateTextRequest>()
                    val updated = transaction { setTeacherNote(p.userId, studentId, body.text.trim()) }
                    if (!updated) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Ученик не найден среди ваших записей"))
                        return@post
                    }
                    call.respond(ErrorResponse("Заметка сохранена"))
                }

                get("/student/homework") {
                    val p = call.principal<ZonaPrincipal>()!!
                    if (p.role != UserRole.STUDENT && p.role != UserRole.TEACHER && p.role != UserRole.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Недоступно"))
                        return@get
                    }
                    val list = transaction { listHomeworkForStudent(p.userId) }
                    call.respond(list)
                }

                get("/leaderboard") {
                    val list = transaction { leaderboard() }
                    call.respond(list)
                }

                get("/admin/users") {
                    val p = call.principal<ZonaPrincipal>()!!
                    if (p.role != UserRole.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Только админ"))
                        return@get
                    }
                    val users =
                        transaction {
                            Users
                                .selectAll()
                                .map {
                                    UserDto(
                                        id = it[Users.id].value,
                                        email = it[Users.email],
                                        displayName = it[Users.displayName],
                                        role = it[Users.role].name,
                                    )
                                }
                        }
                    call.respond(users)
                }

                post("/admin/users") {
                    val p = call.principal<ZonaPrincipal>()!!
                    if (p.role != UserRole.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Только админ"))
                        return@post
                    }
                    val body = call.receive<CreateUserRequest>()
                    val role =
                        runCatching { UserRole.valueOf(body.role) }.getOrNull() ?: run {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Некорректная роль"))
                            return@post
                        }
                    val created =
                        transaction {
                            if (Users.selectAll().where(Users.email.lowerCase() eq body.email.lowercase()).any()) {
                                return@transaction null
                            }
                            (Users.insert {
                                it[Users.email] = body.email.trim().lowercase()
                                it[Users.passwordHash] = hashPassword(body.password)
                                it[Users.role] = role
                                it[Users.displayName] = body.displayName.trim()
                            } get Users.id).value
                        }
                    if (created == null) {
                        call.respond(HttpStatusCode.Conflict, ErrorResponse("Email занят"))
                        return@post
                    }
                    call.respond(transaction { loadUser(created)!! })
                }
            }
        }
    }
}

private fun loadUser(id: Long): UserDto? =
    Users
        .selectAll()
        .where(Users.id eq id)
        .firstOrNull()
        ?.let {
            UserDto(
                id = it[Users.id].value,
                email = it[Users.email],
                displayName = it[Users.displayName],
                role = it[Users.role].name,
            )
        }

private fun loadCourse(
    id: Long,
    viewerId: Long,
): CourseDto? {
    val row =
        Courses
            .join(Users, JoinType.INNER, Courses.teacherId, Users.id)
            .selectAll()
            .where(Courses.id eq id)
            .firstOrNull() ?: return null
    val enrolled =
        Enrollments
            .selectAll()
            .where(Enrollments.userId eq viewerId and (Enrollments.courseId eq id))
            .any()
    return CourseDto(
        id = row[Courses.id].value,
        title = row[Courses.title],
        languageFrom = row[Courses.languageFrom],
        languageTo = row[Courses.languageTo],
        description = row[Courses.description],
        teacherId = row[Courses.teacherId].value,
        teacherName = row[Users.displayName],
        enrolled = enrolled,
    )
}

private fun listCoursesForUser(viewerId: Long): List<CourseDto> {
    val rows =
        Courses
            .join(Users, JoinType.INNER, Courses.teacherId, Users.id)
            .selectAll()
            .map { row ->
                val cid = row[Courses.id].value
                val enrolled =
                    Enrollments
                        .selectAll()
                        .where(Enrollments.userId eq viewerId and (Enrollments.courseId eq cid))
                        .any()
                CourseDto(
                    id = cid,
                    title = row[Courses.title],
                    languageFrom = row[Courses.languageFrom],
                    languageTo = row[Courses.languageTo],
                    description = row[Courses.description],
                    teacherId = row[Courses.teacherId].value,
                    teacherName = row[Users.displayName],
                    enrolled = enrolled,
                )
            }
    return rows
}

private fun canManageCourse(
    p: ZonaPrincipal,
    courseId: Long,
): Boolean {
    val c = Courses.selectAll().where(Courses.id eq courseId).firstOrNull() ?: return false
    return p.role == UserRole.ADMIN || c[Courses.teacherId].value == p.userId
}

private fun listLessons(
    courseId: Long,
    viewerId: Long,
): List<LessonDto> {
    val lessons =
        Lessons
            .selectAll()
            .where(Lessons.courseId eq courseId)
            .orderBy(Lessons.sortOrder to SortOrder.ASC)
            .toList()
    return lessons.map { l ->
        val lid = l[Lessons.id].value
        val total =
            Exercises
                .selectAll()
                .where(Exercises.lessonId eq lid)
                .count()
        val distinctCorrect =
            ExerciseAttempts
                .join(Exercises, JoinType.INNER, ExerciseAttempts.exerciseId, Exercises.id)
                .selectAll()
                .where(
                    (Exercises.lessonId eq lid) and (ExerciseAttempts.userId eq viewerId) and (ExerciseAttempts.correct eq true),
                )
                .map { it[Exercises.id].value }
                .distinct()
                .size
        val distinctAttempted =
            ExerciseAttempts
                .join(Exercises, JoinType.INNER, ExerciseAttempts.exerciseId, Exercises.id)
                .selectAll()
                .where((Exercises.lessonId eq lid) and (ExerciseAttempts.userId eq viewerId))
                .map { it[Exercises.id].value }
                .distinct()
                .size
        LessonDto(
            id = lid,
            courseId = courseId,
            title = l[Lessons.title],
            sortOrder = l[Lessons.sortOrder],
            exerciseCount = total.toInt(),
            attemptedByUser = distinctAttempted.toInt(),
            completedByUser = distinctCorrect.toInt(),
        )
    }
}

private fun loadLesson(
    id: Long,
    viewerId: Long,
): LessonDto? {
    val l = Lessons.selectAll().where(Lessons.id eq id).firstOrNull() ?: return null
    val lid = l[Lessons.id].value
    val courseId = l[Lessons.courseId].value
    val total =
        Exercises
            .selectAll()
            .where(Exercises.lessonId eq lid)
            .count()
    val distinctCorrect =
        ExerciseAttempts
            .join(Exercises, JoinType.INNER, ExerciseAttempts.exerciseId, Exercises.id)
            .selectAll()
            .where(
                (Exercises.lessonId eq lid) and (ExerciseAttempts.userId eq viewerId) and (ExerciseAttempts.correct eq true),
            )
            .map { it[Exercises.id].value }
            .distinct()
            .size
    val distinctAttempted =
        ExerciseAttempts
            .join(Exercises, JoinType.INNER, ExerciseAttempts.exerciseId, Exercises.id)
            .selectAll()
            .where((Exercises.lessonId eq lid) and (ExerciseAttempts.userId eq viewerId))
            .map { it[Exercises.id].value }
            .distinct()
            .size
    return LessonDto(
        id = lid,
        courseId = courseId,
        title = l[Lessons.title],
        sortOrder = l[Lessons.sortOrder],
        exerciseCount = total.toInt(),
        attemptedByUser = distinctAttempted.toInt(),
        completedByUser = distinctCorrect.toInt(),
    )
}

private fun listPublicExercises(
    lessonId: Long,
    @Suppress("UNUSED_PARAMETER") viewerId: Long,
): List<ExercisePublicDto> {
    val rows =
        Exercises
            .selectAll()
            .where(Exercises.lessonId eq lessonId)
            .orderBy(Exercises.sortOrder to SortOrder.ASC)
            .toList()
    return rows.map { row ->
        val id = row[Exercises.id].value
        val type = row[Exercises.type]
        val wrong =
            row[Exercises.wrongOptionsJson]?.let {
                jsonFmt.decodeFromString(ListSerializer(String.serializer()), it)
            }
        val choices =
            if (type == ExerciseType.CHOICE) {
                (wrong.orEmpty() + row[Exercises.answer]).shuffled()
            } else {
                null
            }
        ExercisePublicDto(
            id = id,
            lessonId = lessonId,
            type = type.name,
            prompt = row[Exercises.prompt],
            choices = choices,
        )
    }
}

private fun loadSession(
    id: Long,
    viewerId: Long,
): LiveSessionDto? {
    val s = LiveSessions.selectAll().where(LiveSessions.id eq id).firstOrNull() ?: return null
    val course =
        Courses
            .selectAll()
            .where(Courses.id eq s[LiveSessions.courseId].value)
            .first()
    val teacher =
        Users
            .selectAll()
            .where(Users.id eq s[LiveSessions.teacherId].value)
            .first()
    val booked =
        SessionBookings
            .selectAll()
            .where(SessionBookings.sessionId eq id)
            .count()
    val mine =
        SessionBookings
            .selectAll()
            .where(SessionBookings.sessionId eq id and (SessionBookings.studentId eq viewerId))
            .any()
    return LiveSessionDto(
        id = s[LiveSessions.id].value,
        courseId = s[LiveSessions.courseId].value,
        courseTitle = course[Courses.title],
        teacherId = s[LiveSessions.teacherId].value,
        teacherName = teacher[Users.displayName],
        title = s[LiveSessions.title],
        startsAtEpochMs = s[LiveSessions.startsAtEpochMs],
        durationMinutes = s[LiveSessions.durationMinutes],
        maxStudents = s[LiveSessions.maxStudents],
        bookedCount = booked.toInt(),
        bookedByMe = mine,
    )
}

private fun listLiveSessions(viewerId: Long): List<LiveSessionDto> =
    LiveSessions
        .selectAll()
        .orderBy(LiveSessions.startsAtEpochMs to SortOrder.ASC)
        .mapNotNull { loadSession(it[LiveSessions.id].value, viewerId) }

private fun createTeacherBookingRequest(
    studentId: Long,
    teacherId: Long,
    scheduledAtEpochMs: Long,
): Boolean {
    val teacher =
        Users
            .selectAll()
            .where((Users.id eq teacherId) and (Users.role eq UserRole.TEACHER))
            .firstOrNull() ?: return false
    val student =
        Users
            .selectAll()
            .where((Users.id eq studentId) and (Users.role eq UserRole.STUDENT))
            .firstOrNull() ?: return false
    TeacherBookingRequests.insert {
        it[TeacherBookingRequests.studentId] = student[Users.id].value
        it[TeacherBookingRequests.teacherId] = teacher[Users.id].value
        it[TeacherBookingRequests.scheduledAtEpochMs] = scheduledAtEpochMs
        it[TeacherBookingRequests.status] = BookingStatus.PENDING
    }
    return true
}

private fun listBookingRequestsForTeacher(teacherId: Long): List<TeacherBookingRequestDto> {
    val teacherName = Users.selectAll().where(Users.id eq teacherId).firstOrNull()?.get(Users.displayName) ?: ""
    return TeacherBookingRequests
        .join(Users, JoinType.INNER, TeacherBookingRequests.studentId, Users.id)
        .selectAll()
        .where(TeacherBookingRequests.teacherId eq teacherId)
        .orderBy(TeacherBookingRequests.createdAtEpochMs to SortOrder.DESC)
        .map {
            TeacherBookingRequestDto(
                id = it[TeacherBookingRequests.id].value,
                studentId = it[TeacherBookingRequests.studentId].value,
                studentName = it[Users.displayName],
                studentEmail = it[Users.email],
                teacherId = teacherId,
                teacherName = teacherName,
                scheduledAtEpochMs = it[TeacherBookingRequests.scheduledAtEpochMs],
                status = it[TeacherBookingRequests.status].name,
            )
        }
}

private fun updateBookingRequestStatus(
    teacherId: Long,
    requestId: Long,
    status: BookingStatus,
): Boolean {
    val req =
        TeacherBookingRequests
            .selectAll()
            .where((TeacherBookingRequests.id eq requestId) and (TeacherBookingRequests.teacherId eq teacherId))
            .firstOrNull() ?: return false
    TeacherBookingRequests.update({ TeacherBookingRequests.id eq req[TeacherBookingRequests.id].value }) {
        it[TeacherBookingRequests.status] = status
    }
    return true
}

private fun isStudentOfTeacher(
    teacherId: Long,
    studentId: Long,
): Boolean =
    TeacherBookingRequests
        .selectAll()
        .where(
            (TeacherBookingRequests.teacherId eq teacherId) and
                (TeacherBookingRequests.studentId eq studentId) and
                (TeacherBookingRequests.status eq BookingStatus.CONFIRMED),
        )
        .any()

private fun teacherHistoryForStudent(studentId: Long): List<TeacherHistoryEntryDto> =
    TeacherBookingRequests
        .join(Users, JoinType.INNER, TeacherBookingRequests.teacherId, Users.id)
        .selectAll()
        .where(TeacherBookingRequests.studentId eq studentId)
        .orderBy(TeacherBookingRequests.createdAtEpochMs to SortOrder.DESC)
        .map {
            TeacherHistoryEntryDto(
                teacherId = it[TeacherBookingRequests.teacherId].value,
                teacherName = it[Users.displayName],
                scheduledAtEpochMs = it[TeacherBookingRequests.scheduledAtEpochMs],
                status = it[TeacherBookingRequests.status].name,
            )
        }

private fun listTeacherStudents(teacherId: Long): List<TeacherStudentDto> {
    val studentIds =
        TeacherBookingRequests
            .selectAll()
            .where((TeacherBookingRequests.teacherId eq teacherId) and (TeacherBookingRequests.status eq BookingStatus.CONFIRMED))
            .map { it[TeacherBookingRequests.studentId].value }
            .distinct()

    return studentIds.mapNotNull { sid ->
        val user =
            Users
                .selectAll()
                .where((Users.id eq sid) and (Users.role eq UserRole.STUDENT))
                .firstOrNull() ?: return@mapNotNull null

        val progress =
            Courses
                .selectAll()
                .where(Courses.teacherId eq teacherId)
                .map { c ->
                    val cid = c[Courses.id].value
                    val lessons = listLessons(cid, sid)
                    val completedLessons = lessons.count { it.exerciseCount > 0 && it.completedByUser >= it.exerciseCount }
                    val totalLessons = lessons.size
                    val completedExercises = lessons.sumOf { it.completedByUser }
                    val totalExercises = lessons.sumOf { it.exerciseCount }
                    CourseProgressDto(
                        courseId = cid,
                        courseTitle = c[Courses.title],
                        completedLessons = completedLessons,
                        totalLessons = totalLessons,
                        completedExercises = completedExercises,
                        totalExercises = totalExercises,
                    )
                }

        val lastActivity =
            ExerciseAttempts
                .selectAll()
                .where(ExerciseAttempts.userId eq sid)
                .maxOfOrNull { it[ExerciseAttempts.answeredAtEpochMs] }

        val meta = ensureMetaRecord(teacherId, sid)
        TeacherStudentDto(
            studentId = sid,
            studentName = user[Users.displayName],
            studentEmail = user[Users.email],
            courseProgress = progress,
            lastActivityEpochMs = lastActivity,
            teacherHistory = teacherHistoryForStudent(sid),
            homework = meta.first,
            notes = meta.second,
        )
    }
}

private fun ensureMetaRecord(
    teacherId: Long,
    studentId: Long,
): Pair<String, String> {
    val existing =
        TeacherStudentMeta
            .selectAll()
            .where((TeacherStudentMeta.teacherId eq teacherId) and (TeacherStudentMeta.studentId eq studentId))
            .firstOrNull()
    if (existing != null) {
        return existing[TeacherStudentMeta.homework] to existing[TeacherStudentMeta.notes]
    }
    TeacherStudentMeta.insert {
        it[TeacherStudentMeta.teacherId] = teacherId
        it[TeacherStudentMeta.studentId] = studentId
        it[TeacherStudentMeta.homework] = ""
        it[TeacherStudentMeta.notes] = ""
    }
    return "" to ""
}

private fun setTeacherHomework(
    teacherId: Long,
    studentId: Long,
    text: String,
): Boolean {
    if (!isStudentOfTeacher(teacherId, studentId)) return false
    ensureMetaRecord(teacherId, studentId)
    TeacherStudentMeta.update({ (TeacherStudentMeta.teacherId eq teacherId) and (TeacherStudentMeta.studentId eq studentId) }) {
        it[TeacherStudentMeta.homework] = text
    }
    return true
}

private fun setTeacherNote(
    teacherId: Long,
    studentId: Long,
    text: String,
): Boolean {
    if (!isStudentOfTeacher(teacherId, studentId)) return false
    ensureMetaRecord(teacherId, studentId)
    TeacherStudentMeta.update({ (TeacherStudentMeta.teacherId eq teacherId) and (TeacherStudentMeta.studentId eq studentId) }) {
        it[TeacherStudentMeta.notes] = text
    }
    return true
}

private fun listHomeworkForStudent(studentId: Long): List<StudentHomeworkDto> {
    val rows =
        TeacherStudentMeta
            .join(Users, JoinType.INNER, TeacherStudentMeta.teacherId, Users.id)
            .selectAll()
            .where(TeacherStudentMeta.studentId eq studentId)
            .toList()
    return rows.map {
        StudentHomeworkDto(
            teacherId = it[TeacherStudentMeta.teacherId].value,
            teacherName = it[Users.displayName],
            homework = it[TeacherStudentMeta.homework],
        )
    }.filter { it.homework.isNotBlank() }
}

private fun leaderboard(): List<LeaderboardEntryDto> {
    val students =
        Users
            .selectAll()
            .where(Users.role eq UserRole.STUDENT)
            .toList()
    return students
        .map { st ->
            val sid = st[Users.id].value
            val attempts =
                ExerciseAttempts
                    .selectAll()
                    .where(ExerciseAttempts.userId eq sid)
                    .toList()
            val totalAttempts =
                attempts
                    .map { it[ExerciseAttempts.exerciseId].value }
                    .distinct()
                    .size
            val totalCorrect =
                attempts
                    .filter { it[ExerciseAttempts.correct] }
                    .map { it[ExerciseAttempts.exerciseId].value }
                    .distinct()
                    .size
            LeaderboardEntryDto(
                userId = sid,
                userName = st[Users.displayName],
                totalXp = totalCorrect * 10,
                totalCorrect = totalCorrect,
                totalAttempts = totalAttempts,
            )
        }.sortedWith(compareByDescending<LeaderboardEntryDto> { it.totalXp }.thenByDescending { it.totalCorrect })
}
