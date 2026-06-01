package ru.zona.server.feature.chat

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
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import ru.zona.server.db.Conversations
import ru.zona.server.db.ConversationParticipants
import ru.zona.server.db.Messages
import ru.zona.server.db.Users
import ru.zona.server.plugins.ApiException
import ru.zona.server.security.AUTH_JWT
import ru.zona.server.security.requireUserId

@Serializable
data class ConversationDto(
    val id: Long,
    val peerId: Long,
    val peerName: String,
    val lastMessage: String?,
    val lastAt: Long?,
    val isGroup: Boolean = false,
)

@Serializable
data class MessageDto(
    val id: Long,
    val conversationId: Long,
    val senderId: Long,
    val senderName: String = "",
    val senderAvatar: String? = null,
    val text: String,
    val sentAt: Long,
    val readAt: Long? = null,
    val replyToId: Long? = null,
    val replyToText: String? = null,
    val replyToSender: String? = null,
)

@Serializable
data class SendMessageRequest(val text: String, val replyToId: Long? = null)

@Serializable
data class ConversationIdDto(val conversationId: Long)

class ChatService {
    fun conversations(userId: Long): List<ConversationDto> =
        transaction {
            // 1-на-1, где пользователь — одна из сторон
            val directIds = Conversations.selectAll()
                .where { (Conversations.userA eq userId) or (Conversations.userB eq userId) }
                .map { it[Conversations.id] }
            // групповые, где пользователь — участник
            val groupIds = ConversationParticipants.selectAll()
                .where { ConversationParticipants.userId eq userId }
                .map { it[ConversationParticipants.conversationId] }
            (directIds + groupIds).distinct().mapNotNull { id ->
                val row = Conversations.selectAll().where { Conversations.id eq id }.firstOrNull() ?: return@mapNotNull null
                val last = Messages.selectAll().where { Messages.conversationId eq id }
                    .orderBy(Messages.sentAt to SortOrder.DESC).limit(1).firstOrNull()
                if (row[Conversations.isGroup]) {
                    ConversationDto(id, 0, row[Conversations.title].ifBlank { "Групповой чат" }, last?.get(Messages.text), last?.get(Messages.sentAt), true)
                } else {
                    val peerId = (if (row[Conversations.userA] == userId) row[Conversations.userB] else row[Conversations.userA]) ?: 0L
                    val peerName = Users.selectAll().where { Users.id eq peerId }.firstOrNull()?.get(Users.displayName) ?: ""
                    ConversationDto(id, peerId, peerName, last?.get(Messages.text), last?.get(Messages.sentAt), false)
                }
            }.sortedByDescending { it.lastAt ?: 0 }
        }

    /** Создаёт групповой разговор для занятия (если ещё нет) и добавляет преподавателя. */
    fun ensureGroupForSession(sessionId: Long, teacherId: Long, title: String): Long =
        transaction {
            val existing = Conversations.selectAll().where { Conversations.sessionId eq sessionId }.firstOrNull()
            val id = existing?.get(Conversations.id) ?: Conversations.insert {
                it[isGroup] = true
                it[Conversations.title] = title
                it[Conversations.sessionId] = sessionId
                it[createdAt] = System.currentTimeMillis()
            }[Conversations.id]
            addParticipant(id, teacherId)
            id
        }

    fun addParticipant(conversationId: Long, userId: Long) {
        transaction {
            val exists = ConversationParticipants.selectAll()
                .where { (ConversationParticipants.conversationId eq conversationId) and (ConversationParticipants.userId eq userId) }
                .limit(1).count() > 0
            if (!exists) ConversationParticipants.insert {
                it[ConversationParticipants.conversationId] = conversationId
                it[ConversationParticipants.userId] = userId
            }
        }
    }

    fun messages(conversationId: Long, userId: Long): List<MessageDto> =
        transaction {
            requireMember(conversationId, userId)
            // Чужие непрочитанные сообщения помечаем прочитанными.
            val now = System.currentTimeMillis()
            Messages.update({
                (Messages.conversationId eq conversationId) and
                    (Messages.senderId neq userId) and
                    (Messages.readAt.isNull())
            }) { it[readAt] = now }
            Messages.selectAll().where { Messages.conversationId eq conversationId }
                .orderBy(Messages.sentAt to SortOrder.ASC)
                .map { rowToDto(it) }
        }

    fun send(conversationId: Long, userId: Long, text: String, replyToId: Long?): MessageDto =
        transaction {
            requireMember(conversationId, userId)
            if (text.isBlank()) throw ApiException(HttpStatusCode.UnprocessableEntity, "Пустое сообщение")
            val now = System.currentTimeMillis()
            val id =
                Messages.insert {
                    it[Messages.conversationId] = conversationId
                    it[senderId] = userId
                    it[Messages.text] = text.trim()
                    it[sentAt] = now
                    it[Messages.replyToId] = replyToId
                }[Messages.id]
            Messages.selectAll().where { Messages.id eq id }.first().let { rowToDto(it) }
        }

    /** Должна вызываться внутри transaction { }. */
    private fun rowToDto(row: org.jetbrains.exposed.sql.ResultRow): MessageDto {
        val senderId = row[Messages.senderId]
        val sender = Users.selectAll().where { Users.id eq senderId }.firstOrNull()
        val replyToId = row[Messages.replyToId]
        val replyRow = replyToId?.let { rid -> Messages.selectAll().where { Messages.id eq rid }.firstOrNull() }
        val replySender = replyRow?.let { r -> Users.selectAll().where { Users.id eq r[Messages.senderId] }.firstOrNull()?.get(Users.displayName) }
        return MessageDto(
            id = row[Messages.id],
            conversationId = row[Messages.conversationId],
            senderId = senderId,
            senderName = sender?.get(Users.displayName) ?: "",
            senderAvatar = sender?.get(Users.avatarUrl),
            text = row[Messages.text],
            sentAt = row[Messages.sentAt],
            readAt = row[Messages.readAt],
            replyToId = replyToId,
            replyToText = replyRow?.get(Messages.text),
            replyToSender = replySender,
        )
    }

    fun openWith(userId: Long, peerId: Long): ConversationIdDto =
        ConversationIdDto(directConversation(userId, peerId))

    /** Создаёт (или находит) личный диалог двух пользователей. Возвращает id. */
    fun directConversation(userId: Long, peerId: Long): Long =
        transaction {
            if (peerId == userId) throw ApiException(HttpStatusCode.BadRequest, "Нельзя написать самому себе")
            Users.selectAll().where { Users.id eq peerId }.firstOrNull()
                ?: throw ApiException(HttpStatusCode.NotFound, "Пользователь не найден")
            val a = minOf(userId, peerId)
            val b = maxOf(userId, peerId)
            val existing = Conversations.selectAll()
                .where { (Conversations.userA eq a) and (Conversations.userB eq b) }
                .firstOrNull()
            existing?.get(Conversations.id)
                ?: Conversations.insert {
                    it[userA] = a
                    it[userB] = b
                    it[isGroup] = false
                    it[createdAt] = System.currentTimeMillis()
                }[Conversations.id]
        }

    private fun requireMember(conversationId: Long, userId: Long) {
        val c = Conversations.selectAll().where { Conversations.id eq conversationId }.firstOrNull()
            ?: throw ApiException(HttpStatusCode.NotFound, "Диалог не найден")
        val member = if (c[Conversations.isGroup]) {
            ConversationParticipants.selectAll()
                .where { (ConversationParticipants.conversationId eq conversationId) and (ConversationParticipants.userId eq userId) }
                .limit(1).count() > 0
        } else {
            c[Conversations.userA] == userId || c[Conversations.userB] == userId
        }
        if (!member) throw ApiException(HttpStatusCode.Forbidden, "Нет доступа к диалогу")
    }
}

fun Route.chatRoutes(service: ChatService) {
    authenticate(AUTH_JWT) {
        get("/api/conversations") { call.respond(service.conversations(requireUserId())) }
        post("/api/conversations/with/{userId}") {
            val peer = call.parameters["userId"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "userId")
            call.respond(service.openWith(requireUserId(), peer))
        }
        get("/api/conversations/{id}/messages") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            call.respond(service.messages(id, requireUserId()))
        }
        post("/api/conversations/{id}/messages") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            val req = call.receive<SendMessageRequest>()
            call.respond(service.send(id, requireUserId(), req.text, req.replyToId))
        }
    }
}
