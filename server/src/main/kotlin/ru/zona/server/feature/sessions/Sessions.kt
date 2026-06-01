package ru.zona.server.feature.sessions

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import ru.zona.server.db.SessionBookings
import ru.zona.server.db.Sessions
import ru.zona.server.db.Users
import ru.zona.server.feature.wallet.WalletService
import ru.zona.server.plugins.ApiException
import ru.zona.server.security.AUTH_JWT
import ru.zona.server.security.requireUserId

@Serializable
data class SessionDto(
    val id: Long,
    val teacherId: Long,
    val teacherName: String,
    val type: String,
    val title: String,
    val description: String,
    val startsAt: Long,
    val durationMinutes: Int,
    val capacity: Int,
    val bookedCount: Int,
    val priceCents: Long?,
    val bookedByMe: Boolean,
)

@Serializable
data class CreateSessionRequest(
    val type: String,
    val title: String,
    val description: String = "",
    val startsAt: Long,
    val durationMinutes: Int = 60,
    val capacity: Int = 1,
    val priceCents: Long? = null,
)

class SessionService(
    private val wallet: WalletService,
    private val chat: ru.zona.server.feature.chat.ChatService,
) {
    fun upcoming(userId: Long): List<SessionDto> =
        transaction {
            val now = System.currentTimeMillis()
            Sessions.selectAll().where { Sessions.startsAt greaterEq now }
                .orderBy(Sessions.startsAt to SortOrder.ASC)
                .map { dto(it[Sessions.id], userId)!! }
        }

    fun myBookings(userId: Long): List<SessionDto> =
        transaction {
            SessionBookings.selectAll()
                .where { (SessionBookings.studentId eq userId) and (SessionBookings.status eq "BOOKED") }
                .map { it[SessionBookings.sessionId] }
                .mapNotNull { dto(it, userId) }
                .sortedBy { it.startsAt }
        }

    fun teacherSessions(teacherId: Long): List<SessionDto> =
        transaction {
            Sessions.selectAll().where { Sessions.teacherId eq teacherId }
                .orderBy(Sessions.startsAt to SortOrder.DESC)
                .map { dto(it[Sessions.id], teacherId)!! }
        }

    fun create(teacherId: Long, req: CreateSessionRequest): SessionDto {
        if (req.title.isBlank()) throw ApiException(HttpStatusCode.UnprocessableEntity, "Введите название занятия")
        val id =
            transaction {
                Sessions.insert {
                    it[Sessions.teacherId] = teacherId
                    it[type] = req.type
                    it[title] = req.title.trim()
                    it[description] = req.description.trim()
                    it[startsAt] = req.startsAt
                    it[durationMinutes] = req.durationMinutes
                    it[capacity] = if (req.type == "INDIVIDUAL") 1 else req.capacity.coerceAtLeast(1)
                    it[priceCents] = req.priceCents?.takeIf { p -> p > 0 }
                    it[createdAt] = System.currentTimeMillis()
                }[Sessions.id]
            }
        // Для групповых занятий — сразу создаём групповой чат с преподавателем.
        if (req.type == "GROUP") chat.ensureGroupForSession(id, teacherId, req.title.trim())
        return transaction { dto(id, teacherId)!! }
    }

    fun book(sessionId: Long, userId: Long): SessionDto {
        val price =
            transaction {
                val s = Sessions.selectAll().where { Sessions.id eq sessionId }.firstOrNull()
                    ?: throw ApiException(HttpStatusCode.NotFound, "Занятие не найдено")
                if (s[Sessions.teacherId] == userId) throw ApiException(HttpStatusCode.Conflict, "Нельзя записаться на своё занятие")
                val already =
                    SessionBookings.selectAll()
                        .where { (SessionBookings.sessionId eq sessionId) and (SessionBookings.studentId eq userId) and (SessionBookings.status eq "BOOKED") }
                        .limit(1).count() > 0
                if (already) throw ApiException(HttpStatusCode.Conflict, "Вы уже записаны")
                val booked = SessionBookings.selectAll()
                    .where { (SessionBookings.sessionId eq sessionId) and (SessionBookings.status eq "BOOKED") }
                    .count().toInt()
                if (booked >= s[Sessions.capacity]) throw ApiException(HttpStatusCode.Conflict, "Мест больше нет")
                s[Sessions.priceCents] ?: 0L
            }
        if (price > 0) wallet.charge(userId, price, "Занятие #$sessionId")
        transaction {
            SessionBookings.insert {
                it[SessionBookings.sessionId] = sessionId
                it[studentId] = userId
                it[status] = "BOOKED"
                it[paidCents] = price
                it[createdAt] = System.currentTimeMillis()
            }
        }
        // Группа: добавляем ученика в групповой чат занятия.
        val sess = transaction { Sessions.selectAll().where { Sessions.id eq sessionId }.first() }
        if (sess[Sessions.type] == "GROUP") {
            val convId = chat.ensureGroupForSession(sessionId, sess[Sessions.teacherId], sess[Sessions.title])
            chat.addParticipant(convId, userId)
        }
        return transaction { dto(sessionId, userId)!! }
    }

    private fun dto(sessionId: Long, userId: Long): SessionDto? {
        val s = Sessions.selectAll().where { Sessions.id eq sessionId }.firstOrNull() ?: return null
        val teacherName = Users.selectAll().where { Users.id eq s[Sessions.teacherId] }.firstOrNull()?.get(Users.displayName) ?: ""
        val booked = SessionBookings.selectAll()
            .where { (SessionBookings.sessionId eq sessionId) and (SessionBookings.status eq "BOOKED") }
            .count().toInt()
        val byMe = SessionBookings.selectAll()
            .where { (SessionBookings.sessionId eq sessionId) and (SessionBookings.studentId eq userId) and (SessionBookings.status eq "BOOKED") }
            .limit(1).count() > 0
        return SessionDto(
            id = sessionId,
            teacherId = s[Sessions.teacherId],
            teacherName = teacherName,
            type = s[Sessions.type],
            title = s[Sessions.title],
            description = s[Sessions.description],
            startsAt = s[Sessions.startsAt],
            durationMinutes = s[Sessions.durationMinutes],
            capacity = s[Sessions.capacity],
            bookedCount = booked,
            priceCents = s[Sessions.priceCents],
            bookedByMe = byMe,
        )
    }
}

fun Route.sessionRoutes(service: SessionService) {
    authenticate(AUTH_JWT) {
        get("/api/sessions") { call.respond(service.upcoming(requireUserId())) }
        get("/api/sessions/my") { call.respond(service.myBookings(requireUserId())) }
        get("/api/sessions/teaching") { call.respond(service.teacherSessions(requireUserId())) }
        post("/api/sessions") { call.respond(service.create(requireUserId(), call.receive<CreateSessionRequest>())) }
        post("/api/sessions/{id}/book") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            call.respond(service.book(id, requireUserId()))
        }
    }
}
