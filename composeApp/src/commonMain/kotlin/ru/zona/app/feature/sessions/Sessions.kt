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

// --- Создание занятия (преподаватель) ---
data class CreateSessionState(
    val type: String = "GROUP",
    val title: String = "",
    val description: String = "",
    val dateMillis: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 86_400_000L, // дата (полночь UTC)
    val hour: Int = 18,
    val minute: Int = 0,
    val durationMinutes: Int = 60,
    val capacity: Int = 6,
    val priceText: String = "",
    val saving: Boolean = false,
    val mySessions: List<SessionDto> = emptyList(),
)

sealed interface CreateSessionIntent {
    data object Load : CreateSessionIntent
    data class SetType(val v: String) : CreateSessionIntent
    data class SetTitle(val v: String) : CreateSessionIntent
    data class SetDescription(val v: String) : CreateSessionIntent
    data class SetDate(val millis: Long) : CreateSessionIntent
    data class SetHour(val v: Int) : CreateSessionIntent
    data class SetMinute(val v: Int) : CreateSessionIntent
    data class SetDuration(val v: Int) : CreateSessionIntent
    data class SetCapacity(val v: Int) : CreateSessionIntent
    data class SetPrice(val v: String) : CreateSessionIntent
    data object Create : CreateSessionIntent
}

sealed interface CreateSessionEffect { data class Message(val text: String) : CreateSessionEffect }

class CreateSessionStore(private val repo: SessionRepository, scope: CoroutineScope) :
    MviStore<CreateSessionState, CreateSessionIntent, CreateSessionEffect>(CreateSessionState(), scope) {
    override fun onIntent(intent: CreateSessionIntent) {
        when (intent) {
            CreateSessionIntent.Load -> load()
            is CreateSessionIntent.SetType -> setState { it.copy(type = intent.v, capacity = if (intent.v == "INDIVIDUAL") 1 else 6) }
            is CreateSessionIntent.SetTitle -> setState { it.copy(title = intent.v) }
            is CreateSessionIntent.SetDescription -> setState { it.copy(description = intent.v) }
            is CreateSessionIntent.SetDate -> setState { it.copy(dateMillis = intent.millis) }
            is CreateSessionIntent.SetHour -> setState { it.copy(hour = intent.v.coerceIn(0, 23)) }
            is CreateSessionIntent.SetMinute -> setState { it.copy(minute = ((intent.v % 60) + 60) % 60) }
            is CreateSessionIntent.SetDuration -> setState { it.copy(durationMinutes = intent.v.coerceIn(15, 240)) }
            is CreateSessionIntent.SetCapacity -> setState { it.copy(capacity = intent.v.coerceIn(1, 100)) }
            is CreateSessionIntent.SetPrice -> setState { it.copy(priceText = intent.v.filter { c -> c.isDigit() }) }
            CreateSessionIntent.Create -> create()
        }
    }
    private fun load() {
        scope.launch {
            when (val r = repo.teaching()) {
                is Outcome.Success -> setState { it.copy(mySessions = r.data) }
                is Outcome.Failure -> emit(CreateSessionEffect.Message(r.message))
            }
        }
    }
    private fun create() {
        val st = currentState
        if (st.saving || st.title.isBlank()) return
        setState { it.copy(saving = true) }
        scope.launch {
            val dayMs = 86_400_000L
            // дата (полночь) + выбранное время
            val startsAt = (st.dateMillis - (st.dateMillis % dayMs)) + st.hour * 3_600_000L + st.minute * 60_000L
            val price = st.priceText.toLongOrNull()?.let { it * 100 }
            val req = CreateSessionRequest(
                type = st.type,
                title = st.title.trim(),
                description = st.description.trim(),
                startsAt = startsAt,
                durationMinutes = st.durationMinutes,
                capacity = st.capacity,
                priceCents = price,
            )
            when (val r = repo.create(req)) {
                is Outcome.Success -> {
                    setState { CreateSessionState(mySessions = it.mySessions) }
                    emit(CreateSessionEffect.Message("Занятие «${r.data.title}» создано 🛰"))
                    load()
                }
                is Outcome.Failure -> { setState { it.copy(saving = false) }; emit(CreateSessionEffect.Message(r.message)) }
            }
        }
    }
}
