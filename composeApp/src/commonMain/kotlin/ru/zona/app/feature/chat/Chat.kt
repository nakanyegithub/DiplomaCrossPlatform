package ru.zona.app.feature.chat

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
data class ConversationDto(val id: Long, val peerId: Long, val peerName: String, val lastMessage: String? = null, val lastAt: Long? = null)

@Serializable
data class MessageDto(val id: Long, val conversationId: Long, val senderId: Long, val text: String, val sentAt: Long, val readAt: Long? = null)

@Serializable
data class SendMessageRequest(val text: String)

@Serializable
data class ConversationIdDto(val conversationId: Long)

class ChatApi(private val client: HttpClient, private val baseUrl: String) {
    suspend fun conversations(): HttpResponse = client.get("$baseUrl/api/conversations")
    suspend fun openWith(userId: Long): HttpResponse = client.post("$baseUrl/api/conversations/with/$userId")
    suspend fun messages(id: Long): HttpResponse = client.get("$baseUrl/api/conversations/$id/messages")
    suspend fun send(id: Long, text: String): HttpResponse = client.post("$baseUrl/api/conversations/$id/messages") { setBody(SendMessageRequest(text)) }
}

interface ChatRepository {
    suspend fun conversations(): Outcome<List<ConversationDto>>
    suspend fun openWith(userId: Long): Outcome<Long>
    suspend fun messages(id: Long): Outcome<List<MessageDto>>
    suspend fun send(id: Long, text: String): Outcome<MessageDto>
}

class ChatRepositoryImpl(private val api: ChatApi) : ChatRepository {
    override suspend fun conversations() = safeApiCall({ api.conversations() }, { it.body<List<ConversationDto>>() })
    override suspend fun openWith(userId: Long): Outcome<Long> =
        when (val r = safeApiCall({ api.openWith(userId) }, { it.body<ConversationIdDto>() })) {
            is Outcome.Success -> Outcome.Success(r.data.conversationId)
            is Outcome.Failure -> r
        }
    override suspend fun messages(id: Long) = safeApiCall({ api.messages(id) }, { it.body<List<MessageDto>>() })
    override suspend fun send(id: Long, text: String) = safeApiCall({ api.send(id, text) }, { it.body<MessageDto>() })
}

data class ChatListState(val loading: Boolean = true, val conversations: List<ConversationDto> = emptyList(), val error: String? = null)
sealed interface ChatListIntent { data object Load : ChatListIntent }

class ChatListStore(private val repo: ChatRepository, scope: CoroutineScope) :
    MviStore<ChatListState, ChatListIntent, Unit>(ChatListState(), scope) {
    override fun onIntent(intent: ChatListIntent) {
        setState { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = repo.conversations()) {
                is Outcome.Success -> setState { it.copy(loading = false, conversations = r.data) }
                is Outcome.Failure -> setState { it.copy(loading = false, error = r.message) }
            }
        }
    }
}

data class ChatState(
    val loading: Boolean = true,
    val messages: List<MessageDto> = emptyList(),
    val draft: String = "",
    val sending: Boolean = false,
    val error: String? = null,
)

sealed interface ChatIntent {
    data object Load : ChatIntent
    data class SetDraft(val text: String) : ChatIntent
    data object Send : ChatIntent
}

sealed interface ChatEffect { data class Message(val text: String) : ChatEffect }

class ChatStore(private val conversationId: Long, private val repo: ChatRepository, scope: CoroutineScope) :
    MviStore<ChatState, ChatIntent, ChatEffect>(ChatState(), scope) {
    override fun onIntent(intent: ChatIntent) {
        when (intent) {
            ChatIntent.Load -> load()
            is ChatIntent.SetDraft -> setState { it.copy(draft = intent.text) }
            ChatIntent.Send -> send()
        }
    }
    private fun load() {
        scope.launch {
            when (val r = repo.messages(conversationId)) {
                is Outcome.Success -> setState { it.copy(loading = false, messages = r.data) }
                is Outcome.Failure -> setState { it.copy(loading = false, error = r.message) }
            }
        }
    }
    private fun send() {
        val text = currentState.draft.trim()
        if (text.isBlank() || currentState.sending) return
        setState { it.copy(sending = true) }
        scope.launch {
            when (val r = repo.send(conversationId, text)) {
                is Outcome.Success -> { setState { it.copy(sending = false, draft = "", messages = it.messages + r.data) } }
                is Outcome.Failure -> { setState { it.copy(sending = false) }; emit(ChatEffect.Message(r.message)) }
            }
        }
    }
}
