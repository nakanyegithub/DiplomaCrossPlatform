package ru.zona.app.feature.flashcards

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import ru.zona.app.core.mvi.MviStore
import ru.zona.app.core.network.safeApiCall
import ru.zona.app.core.result.Outcome

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

class FlashcardApi(private val client: HttpClient, private val baseUrl: String) {
    suspend fun decks(): HttpResponse = client.get("$baseUrl/api/decks")
    suspend fun createDeck(body: CreateDeckRequest): HttpResponse = client.post("$baseUrl/api/decks") { setBody(body) }
    suspend fun cards(deckId: Long): HttpResponse = client.get("$baseUrl/api/decks/$deckId/cards")
    suspend fun addCard(deckId: Long, body: CreateCardRequest): HttpResponse = client.post("$baseUrl/api/decks/$deckId/cards") { setBody(body) }
    suspend fun review(cardId: Long, remembered: Boolean): HttpResponse = client.post("$baseUrl/api/cards/$cardId/review") { setBody(ReviewRequest(remembered)) }
}

interface FlashcardRepository {
    suspend fun decks(): Outcome<List<DeckDto>>
    suspend fun cards(deckId: Long): Outcome<List<FlashcardDto>>
    suspend fun review(cardId: Long, remembered: Boolean): Outcome<FlashcardDto>
    suspend fun createDeck(title: String): Outcome<DeckDto>
    suspend fun addCard(deckId: Long, front: String, back: String): Outcome<FlashcardDto>
}

class FlashcardRepositoryImpl(private val api: FlashcardApi) : FlashcardRepository {
    override suspend fun decks() = safeApiCall({ api.decks() }, { it.body<List<DeckDto>>() })
    override suspend fun cards(deckId: Long) = safeApiCall({ api.cards(deckId) }, { it.body<List<FlashcardDto>>() })
    override suspend fun review(cardId: Long, remembered: Boolean) = safeApiCall({ api.review(cardId, remembered) }, { it.body<FlashcardDto>() })
    override suspend fun createDeck(title: String) = safeApiCall({ api.createDeck(CreateDeckRequest(title)) }, { it.body<DeckDto>() })
    override suspend fun addCard(deckId: Long, front: String, back: String) = safeApiCall({ api.addCard(deckId, CreateCardRequest(front, back)) }, { it.body<FlashcardDto>() })
}

// --- Decks list store ---
data class DecksState(val loading: Boolean = true, val decks: List<DeckDto> = emptyList(), val error: String? = null)
sealed interface DecksIntent { data object Load : DecksIntent; data class Create(val title: String) : DecksIntent }
sealed interface DecksEffect { data class Message(val text: String) : DecksEffect }

class DecksStore(private val repo: FlashcardRepository, scope: CoroutineScope) :
    MviStore<DecksState, DecksIntent, DecksEffect>(DecksState(), scope) {
    override fun onIntent(intent: DecksIntent) {
        when (intent) {
            DecksIntent.Load -> load()
            is DecksIntent.Create -> create(intent.title)
        }
    }
    private fun load() {
        setState { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = repo.decks()) {
                is Outcome.Success -> setState { it.copy(loading = false, decks = r.data) }
                is Outcome.Failure -> setState { it.copy(loading = false, error = r.message) }
            }
        }
    }
    private fun create(title: String) {
        scope.launch {
            when (val r = repo.createDeck(title)) {
                is Outcome.Success -> { emit(DecksEffect.Message("Колода создана")); load() }
                is Outcome.Failure -> emit(DecksEffect.Message(r.message))
            }
        }
    }
}

// --- Deck management store (добавление карточек) ---
data class ManageDeckState(
    val loading: Boolean = true,
    val cards: List<FlashcardDto> = emptyList(),
    val front: String = "",
    val back: String = "",
    val saving: Boolean = false,
    val error: String? = null,
)

sealed interface ManageDeckIntent {
    data object Load : ManageDeckIntent
    data class SetFront(val v: String) : ManageDeckIntent
    data class SetBack(val v: String) : ManageDeckIntent
    data object AddCard : ManageDeckIntent
}

sealed interface ManageDeckEffect { data class Message(val text: String) : ManageDeckEffect }

class ManageDeckStore(private val deckId: Long, private val repo: FlashcardRepository, scope: CoroutineScope) :
    MviStore<ManageDeckState, ManageDeckIntent, ManageDeckEffect>(ManageDeckState(), scope) {
    override fun onIntent(intent: ManageDeckIntent) {
        when (intent) {
            ManageDeckIntent.Load -> load()
            is ManageDeckIntent.SetFront -> setState { it.copy(front = intent.v) }
            is ManageDeckIntent.SetBack -> setState { it.copy(back = intent.v) }
            ManageDeckIntent.AddCard -> add()
        }
    }
    private fun load() {
        setState { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = repo.cards(deckId)) {
                is Outcome.Success -> setState { it.copy(loading = false, cards = r.data) }
                is Outcome.Failure -> setState { it.copy(loading = false, error = r.message) }
            }
        }
    }
    private fun add() {
        val st = currentState
        if (st.saving || st.front.isBlank() || st.back.isBlank()) return
        setState { it.copy(saving = true) }
        scope.launch {
            when (val r = repo.addCard(deckId, st.front.trim(), st.back.trim())) {
                is Outcome.Success -> { setState { it.copy(saving = false, front = "", back = "") }; emit(ManageDeckEffect.Message("Карточка добавлена")); load() }
                is Outcome.Failure -> { setState { it.copy(saving = false) }; emit(ManageDeckEffect.Message(r.message)) }
            }
        }
    }
}

// --- Study store ---
data class StudyState(
    val loading: Boolean = true,
    val cards: List<FlashcardDto> = emptyList(),
    val index: Int = 0,
    val flipped: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null,
) {
    val current: FlashcardDto? get() = cards.getOrNull(index)
}

sealed interface StudyIntent {
    data object Load : StudyIntent
    data object Flip : StudyIntent
    data class Answer(val remembered: Boolean) : StudyIntent
}

class StudyStore(private val deckId: Long, private val repo: FlashcardRepository, scope: CoroutineScope) :
    MviStore<StudyState, StudyIntent, Unit>(StudyState(), scope) {
    override fun onIntent(intent: StudyIntent) {
        when (intent) {
            StudyIntent.Load -> load()
            StudyIntent.Flip -> setState { it.copy(flipped = !it.flipped) }
            is StudyIntent.Answer -> answer(intent.remembered)
        }
    }
    private fun load() {
        setState { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = repo.cards(deckId)) {
                is Outcome.Success -> setState { StudyState(loading = false, cards = r.data, finished = r.data.isEmpty()) }
                is Outcome.Failure -> setState { it.copy(loading = false, error = r.message) }
            }
        }
    }
    private fun answer(remembered: Boolean) {
        val card = currentState.current ?: return
        scope.launch { repo.review(card.id, remembered) }
        setState {
            val next = it.index + 1
            if (next >= it.cards.size) it.copy(finished = true) else it.copy(index = next, flipped = false)
        }
    }
}
