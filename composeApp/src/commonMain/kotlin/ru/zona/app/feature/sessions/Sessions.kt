package ru.zona.app.feature.sessions

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
    val priceCents: Long? = null,
    val bookedByMe: Boolean = false,
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

class SessionApi(private val client: HttpClient, private val baseUrl: String) {
    suspend fun upcoming(): HttpResponse = client.get("$baseUrl/api/sessions")
    suspend fun my(): HttpResponse = client.get("$baseUrl/api/sessions/my")
    suspend fun teaching(): HttpResponse = client.get("$baseUrl/api/sessions/teaching")
    suspend fun create(body: CreateSessionRequest): HttpResponse = client.post("$baseUrl/api/sessions") { setBody(body) }
    suspend fun book(id: Long): HttpResponse = client.post("$baseUrl/api/sessions/$id/book")
}

interface SessionRepository {
    suspend fun upcoming(): Outcome<List<SessionDto>>
    suspend fun my(): Outcome<List<SessionDto>>
    suspend fun teaching(): Outcome<List<SessionDto>>
    suspend fun create(req: CreateSessionRequest): Outcome<SessionDto>
    suspend fun book(id: Long): Outcome<SessionDto>
}

class SessionRepositoryImpl(private val api: SessionApi) : SessionRepository {
    override suspend fun upcoming() = safeApiCall({ api.upcoming() }, { it.body<List<SessionDto>>() })
    override suspend fun my() = safeApiCall({ api.my() }, { it.body<List<SessionDto>>() })
    override suspend fun teaching() = safeApiCall({ api.teaching() }, { it.body<List<SessionDto>>() })
    override suspend fun create(req: CreateSessionRequest) = safeApiCall({ api.create(req) }, { it.body<SessionDto>() })
    override suspend fun book(id: Long) = safeApiCall({ api.book(id) }, { it.body<SessionDto>() })
}

enum class SessionsTab { Upcoming, Mine }

data class SessionsState(
    val tab: SessionsTab = SessionsTab.Upcoming,
    val loading: Boolean = true,
    val sessions: List<SessionDto> = emptyList(),
    val error: String? = null,
)

sealed interface SessionsIntent {
    data object Load : SessionsIntent
    data class SetTab(val tab: SessionsTab) : SessionsIntent
    data class Book(val id: Long) : SessionsIntent
}

sealed interface SessionsEffect { data class Message(val text: String) : SessionsEffect }

class SessionsStore(private val repo: SessionRepository, scope: CoroutineScope) :
    MviStore<SessionsState, SessionsIntent, SessionsEffect>(SessionsState(), scope) {
    override fun onIntent(intent: SessionsIntent) {
        when (intent) {
            SessionsIntent.Load -> load()
            is SessionsIntent.SetTab -> { setState { it.copy(tab = intent.tab) }; load() }
            is SessionsIntent.Book -> book(intent.id)
        }
    }
    private fun load() {
        setState { it.copy(loading = true, error = null) }
        scope.launch {
            val r = if (currentState.tab == SessionsTab.Mine) repo.my() else repo.upcoming()
            when (r) {
                is Outcome.Success -> setState { it.copy(loading = false, sessions = r.data) }
                is Outcome.Failure -> setState { it.copy(loading = false, error = r.message) }
            }
        }
    }
    private fun book(id: Long) {
        scope.launch {
            when (val r = repo.book(id)) {
                is Outcome.Success -> { emit(SessionsEffect.Message("Вы записаны на занятие 🛰")); load() }
                is Outcome.Failure -> emit(SessionsEffect.Message(r.message))
            }
        }
    }
}
