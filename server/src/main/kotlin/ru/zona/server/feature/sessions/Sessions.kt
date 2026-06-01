package ru.zona.server.feature.sessions

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
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
    val myStatus: String? = null, // null | PENDING | BOOKED | DECLINED
)

@Serializable
data class BookingRequestDto(
    val bookingId: Long,
    val sessionId: Long,
    val sessionTitle: String,
    val startsAt: Long,
    val durationMinutes: Int,
    val priceCents: Long?,
    val studentId: Long,
    val studentName: String,
    val status: String,
    val createdAt: Long,
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
        val sess = transaction {
            val s = Sessions.selectAll().where { Sessions.id eq sessionId }.firstOrNull()
                ?: throw ApiException(HttpStatusCode.NotFound, "Занятие не найдено")
            if (s[Sessions.teacherId] == userId) throw ApiException(HttpStatusCode.Conflict, "Нельзя записаться на своё занятие")
            val existing = SessionBookings.selectAll()
                .where { (SessionBookings.sessionId eq sessionId) and (SessionBookings.studentId eq userId) and (SessionBookings.status inList listOf("BOOKED", "PENDING")) }
                .limit(1).count() > 0
            if (existing) throw ApiException(HttpStatusCode.Conflict, "Вы уже записаны или заявка на рассмотрении")
            s
        }
        val isGroup = sess[Sessions.type] == "GROUP"
        val teacherId = sess[Sessions.teacherId]
        val price = sess[Sessions.priceCents] ?: 0L

        if (isGroup) {
            // Групповое — мгновенно, со списанием.
            transaction {
                val booked = SessionBookings.selectAll()
                    .where { (SessionBookings.sessionId eq sessionId) and (SessionBookings.status eq "BOOKED") }
                    .count().toInt()
                if (booked >= sess[Sessions.capacity]) throw ApiException(HttpStatusCode.Conflict, "Мест больше нет")
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
            val convId = chat.ensureGroupForSession(sessionId, teacherId, sess[Sessions.title])
            chat.addParticipant(convId, userId)
        } else {
            // Индивидуальное — заявка (PENDING), без списания. Сразу заводим личный чат с преподом.
            transaction {
                SessionBookings.insert {
                    it[SessionBookings.sessionId] = sessionId
                    it[studentId] = userId
                    it[status] = "PENDING"
                    it[paidCents] = 0
                    it[createdAt] = System.currentTimeMillis()
                }
            }
            chat.directConversation(userId, teacherId)
        }
        return transaction { dto(sessionId, userId)!! }
    }

    /** Заявки на индивидуальные занятия преподавателя. */
    fun incomingRequests(teacherId: Long): List<BookingRequestDto> =
        transaction {
            val myIndividual = Sessions.selectAll()
                .where { (Sessions.teacherId eq teacherId) and (Sessions.type eq "INDIVIDUAL") }
                .associate { it[Sessions.id] to it }
            if (myIndividual.isEmpty()) return@transaction emptyList()
            SessionBookings.selectAll()
                .where { (SessionBookings.sessionId inList myIndividual.keys) and (SessionBookings.status eq "PENDING") }
                .orderBy(SessionBookings.createdAt to SortOrder.DESC)
                .map { b ->
                    val s = myIndividual.getValue(b[SessionBookings.sessionId])
                    val studentName = Users.selectAll().where { Users.id eq b[SessionBookings.studentId] }.firstOrNull()?.get(Users.displayName) ?: ""
                    BookingRequestDto(
                        bookingId = b[SessionBookings.id],
                        sessionId = s[Sessions.id],
                        sessionTitle = s[Sessions.title],
                        startsAt = s[Sessions.startsAt],
                        durationMinutes = s[Sessions.durationMinutes],
                        priceCents = s[Sessions.priceCents],
                        studentId = b[SessionBookings.studentId],
                        studentName = studentName,
                        status = b[SessionBookings.status],
                        createdAt = b[SessionBookings.createdAt],
                    )
                }
        }

    fun acceptRequest(teacherId: Long, bookingId: Long) {
        val (sessionId, studentId, price) = transaction {
            val b = SessionBookings.selectAll().where { SessionBookings.id eq bookingId }.firstOrNull()
                ?: throw ApiException(HttpStatusCode.NotFound, "Заявка не найдена")
            val s = Sessions.selectAll().where { Sessions.id eq b[SessionBookings.sessionId] }.first()
            if (s[Sessions.teacherId] != teacherId) throw ApiException(HttpStatusCode.Forbidden, "Это не ваше занятие")
            if (b[SessionBookings.status] != "PENDING") throw ApiException(HttpStatusCode.Conflict, "Заявка уже обработана")
            Triple(s[Sessions.id], b[SessionBookings.studentId], s[Sessions.priceCents] ?: 0L)
        }
        if (price > 0) wallet.charge(studentId, price, "Индивидуальное занятие #$sessionId")
        transaction {
            SessionBookings.update({ SessionBookings.id eq bookingId }) {
                it[status] = "BOOKED"
                it[paidCents] = price
            }
        }
        chat.directConversation(studentId, teacherId)
    }

    fun declineRequest(teacherId: Long, bookingId: Long) {
        transaction {
            val b = SessionBookings.selectAll().where { SessionBookings.id eq bookingId }.firstOrNull()
                ?: throw ApiException(HttpStatusCode.NotFound, "Заявка не найдена")
            val s = Sessions.selectAll().where { Sessions.id eq b[SessionBookings.sessionId] }.first()
            if (s[Sessions.teacherId] != teacherId) throw ApiException(HttpStatusCode.Forbidden, "Это не ваше занятие")
            SessionBookings.update({ SessionBookings.id eq bookingId }) { it[status] = "DECLINED" }
        }
    }

    fun deleteSession(teacherId: Long, sessionId: Long) {
        transaction {
            val s = Sessions.selectAll().where { Sessions.id eq sessionId }.firstOrNull()
                ?: throw ApiException(HttpStatusCode.NotFound, "Занятие не найдено")
            if (s[Sessions.teacherId] != teacherId) throw ApiException(HttpStatusCode.Forbidden, "Это не ваше занятие")
            SessionBookings.deleteWhere { SessionBookings.sessionId eq sessionId }
            Sessions.deleteWhere { Sessions.id eq sessionId }
        }
    }

    private fun dto(sessionId: Long, userId: Long): SessionDto? {
        val s = Sessions.selectAll().where { Sessions.id eq sessionId }.firstOrNull() ?: return null
        val teacherName = Users.selectAll().where { Users.id eq s[Sessions.teacherId] }.firstOrNull()?.get(Users.displayName) ?: ""
        val booked = SessionBookings.selectAll()
            .where { (SessionBookings.sessionId eq sessionId) and (SessionBookings.status eq "BOOKED") }
            .count().toInt()
        val myBooking = SessionBookings.selectAll()
            .where { (SessionBookings.sessionId eq sessionId) and (SessionBookings.studentId eq userId) and (SessionBookings.status inList listOf("BOOKED", "PENDING")) }
            .orderBy(SessionBookings.createdAt to SortOrder.DESC)
            .firstOrNull()
        val myStatus = myBooking?.get(SessionBookings.status)
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
            bookedByMe = (myStatus == "BOOKED"),
            myStatus = myStatus,
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
        delete("/api/sessions/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            service.deleteSession(requireUserId(), id); call.respond(HttpStatusCode.NoContent)
        }
        get("/api/sessions/requests") { call.respond(service.incomingRequests(requireUserId())) }
        post("/api/sessions/requests/{bookingId}/accept") {
            val id = call.parameters["bookingId"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "bookingId")
            service.acceptRequest(requireUserId(), id); call.respond(HttpStatusCode.NoContent)
        }
        post("/api/sessions/requests/{bookingId}/decline") {
            val id = call.parameters["bookingId"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "bookingId")
            service.declineRequest(requireUserId(), id); call.respond(HttpStatusCode.NoContent)
        }
    }
}
