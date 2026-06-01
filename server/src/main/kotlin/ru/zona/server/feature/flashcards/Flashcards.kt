package ru.zona.server.feature.flashcards

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
import org.jetbrains.exposed.sql.upsert
import ru.zona.server.db.FlashcardReviews
import ru.zona.server.db.Flashcards
import ru.zona.server.db.Decks
import ru.zona.server.plugins.ApiException
import ru.zona.server.security.AUTH_JWT
import ru.zona.server.security.requireUserId

@Serializable
data class DeckDto(val id: Long, val title: String, val cardCount: Int, val dueCount: Int)

@Serializable
data class FlashcardDto(val id: Long, val front: String, val back: String, val box: Int)

@Serializable
data class CreateDeckRequest(val title: String, val courseId: Long? = null)

@Serializable
data class CreateCardRequest(val front: String, val back: String)

@Serializable
data class ReviewRequest(val remembered: Boolean)

/** Карточки + интервальные повторения по системе Лейтнера (box 0..5). */
class FlashcardService {
    private val boxIntervalsMs = longArrayOf(0, 60_000, 600_000, 3_600_000, 86_400_000, 432_000_000)

    fun decks(userId: Long): List<DeckDto> =
        transaction {
            Decks.selectAll().orderBy(Decks.createdAt to SortOrder.DESC).map { d ->
                val deckId = d[Decks.id]
                val cards = Flashcards.selectAll().where { Flashcards.deckId eq deckId }.map { it[Flashcards.id] }
                val now = System.currentTimeMillis()
                val due =
                    cards.count { cid ->
                        val rev =
                            FlashcardReviews.selectAll()
                                .where { (FlashcardReviews.userId eq userId) and (FlashcardReviews.flashcardId eq cid) }
                                .firstOrNull()
                        rev == null || rev[FlashcardReviews.dueAt] <= now
                    }
                DeckDto(deckId, d[Decks.title], cards.size, due)
            }
        }

    fun cards(deckId: Long, userId: Long): List<FlashcardDto> =
        transaction {
            Flashcards.selectAll().where { Flashcards.deckId eq deckId }
                .orderBy(Flashcards.sortOrder to SortOrder.ASC)
                .map { c ->
                    val box =
                        FlashcardReviews.selectAll()
                            .where { (FlashcardReviews.userId eq userId) and (FlashcardReviews.flashcardId eq c[Flashcards.id]) }
                            .firstOrNull()?.get(FlashcardReviews.box) ?: 0
                    FlashcardDto(c[Flashcards.id], c[Flashcards.front], c[Flashcards.back], box)
                }
        }

    fun review(cardId: Long, userId: Long, remembered: Boolean): FlashcardDto =
        transaction {
            val card = Flashcards.selectAll().where { Flashcards.id eq cardId }.firstOrNull()
                ?: throw ApiException(HttpStatusCode.NotFound, "Карточка не найдена")
            val curBox =
                FlashcardReviews.selectAll()
                    .where { (FlashcardReviews.userId eq userId) and (FlashcardReviews.flashcardId eq cardId) }
                    .firstOrNull()?.get(FlashcardReviews.box) ?: 0
            val newBox = if (remembered) (curBox + 1).coerceAtMost(5) else 0
            val due = System.currentTimeMillis() + boxIntervalsMs[newBox]
            FlashcardReviews.upsert {
                it[FlashcardReviews.userId] = userId
                it[flashcardId] = cardId
                it[box] = newBox
                it[dueAt] = due
            }
            FlashcardDto(cardId, card[Flashcards.front], card[Flashcards.back], newBox)
        }

    fun createDeck(ownerId: Long, req: CreateDeckRequest): DeckDto =
        transaction {
            if (req.title.isBlank()) throw ApiException(HttpStatusCode.UnprocessableEntity, "Введите название колоды")
            val id =
                Decks.insert {
                    it[Decks.ownerId] = ownerId
                    it[courseId] = req.courseId
                    it[title] = req.title.trim()
                    it[createdAt] = System.currentTimeMillis()
                }[Decks.id]
            DeckDto(id, req.title.trim(), 0, 0)
        }

    fun addCard(ownerId: Long, deckId: Long, req: CreateCardRequest): FlashcardDto =
        transaction {
            val owner = Decks.selectAll().where { Decks.id eq deckId }.firstOrNull()?.get(Decks.ownerId)
                ?: throw ApiException(HttpStatusCode.NotFound, "Колода не найдена")
            if (owner != ownerId) throw ApiException(HttpStatusCode.Forbidden, "Это не ваша колода")
            if (req.front.isBlank() || req.back.isBlank()) {
                throw ApiException(HttpStatusCode.UnprocessableEntity, "Заполните обе стороны карточки")
            }
            val order = Flashcards.selectAll().where { Flashcards.deckId eq deckId }.count().toInt()
            val id =
                Flashcards.insert {
                    it[Flashcards.deckId] = deckId
                    it[front] = req.front.trim()
                    it[back] = req.back.trim()
                    it[sortOrder] = order
                }[Flashcards.id]
            FlashcardDto(id, req.front.trim(), req.back.trim(), 0)
        }
}

fun Route.flashcardRoutes(service: FlashcardService) {
    authenticate(AUTH_JWT) {
        get("/api/decks") { call.respond(service.decks(requireUserId())) }
        post("/api/decks") { call.respond(service.createDeck(requireUserId(), call.receive<CreateDeckRequest>())) }
        get("/api/decks/{id}/cards") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            call.respond(service.cards(id, requireUserId()))
        }
        post("/api/decks/{id}/cards") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            call.respond(service.addCard(requireUserId(), id, call.receive<CreateCardRequest>()))
        }
        post("/api/cards/{id}/review") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            call.respond(service.review(id, requireUserId(), call.receive<ReviewRequest>().remembered))
        }
    }
}
