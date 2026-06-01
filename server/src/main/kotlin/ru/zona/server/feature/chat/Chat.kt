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
import ru.zona.server.db.Conversations
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
)

@Serializable
data class MessageDto(val id: Long, val conversationId: Long, val senderId: Long, val text: String, val sentAt: Long)

@Serializable
data class SendMessageRequest(val text: String)

@Serializable
data class ConversationIdDto(val conversationId: Long)

class ChatService {
    fun conversations(userId: Long): List<ConversationDto> =
        transaction {
            Conversations.selectAll()
                .where { (Conversations.userA eq userId) or (Conversations.userB eq userId) }
                .map { row ->
                    val id = row[Conversations.id]
                    val peerId = if (row[Conversations.userA] == userId) row[Conversations.userB] else row[Conversations.userA]
                    val peerName = Users.selectAll().where { Users.id eq peerId }.firstOrNull()?.get(Users.displayName) ?: ""
                    val last = Messages.selectAll().where { Messages.conversationId eq id }
                        .orderBy(Messages.sentAt to SortOrder.DESC).limit(1).firstOrNull()
                    ConversationDto(id, peerId, peerName, last?.get(Messages.text), last?.get(Messages.sentAt))
                }
                .sortedByDescending { it.lastAt ?: 0 }
        }

    fun messages(conversationId: Long, userId: Long): List<MessageDto> =
        transaction {
            requireMember(conversationId, userId)
            Messages.selectAll().where { Messages.conversationId eq conversationId }
                .orderBy(Messages.sentAt to SortOrder.ASC)
                .map { MessageDto(it[Messages.id], conversationId, it[Messages.senderId], it[Messages.text], it[Messages.sentAt]) }
        }

    fun send(conversationId: Long, userId: Long, text: String): MessageDto =
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
                }[Messages.id]
            MessageDto(id, conversationId, userId, text.trim(), now)
        }

    fun openWith(userId: Long, peerId: Long): ConversationIdDto =
        transaction {
            if (peerId == userId) throw ApiException(HttpStatusCode.BadRequest, "Нельзя написать самому себе")
            Users.selectAll().where { Users.id eq peerId }.firstOrNull()
                ?: throw ApiException(HttpStatusCode.NotFound, "Пользователь не найден")
            val a = minOf(userId, peerId)
            val b = maxOf(userId, peerId)
            val existing = Conversations.selectAll()
                .where { (Conversations.userA eq a) and (Conversations.userB eq b) }
                .firstOrNull()
            val id = existing?.get(Conversations.id)
                ?: Conversations.insert {
                    it[userA] = a
                    it[userB] = b
                    it[createdAt] = System.currentTimeMillis()
                }[Conversations.id]
            ConversationIdDto(id)
        }

    private fun requireMember(conversationId: Long, userId: Long) {
        val c = Conversations.selectAll().where { Conversations.id eq conversationId }.firstOrNull()
            ?: throw ApiException(HttpStatusCode.NotFound, "Диалог не найден")
        if (c[Conversations.userA] != userId && c[Conversations.userB] != userId) {
            throw ApiException(HttpStatusCode.Forbidden, "Нет доступа к диалогу")
        }
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
            call.respond(service.send(id, requireUserId(), call.receive<SendMessageRequest>().text))
        }
    }
}
