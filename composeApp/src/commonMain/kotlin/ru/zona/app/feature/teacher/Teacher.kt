package ru.zona.app.feature.teacher

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import ru.zona.app.core.mvi.MviStore
import ru.zona.app.core.network.safeApiCall
import ru.zona.app.core.result.Outcome

@Serializable
data class TeacherDto(
    val id: Long,
    val displayName: String,
    val headline: String,
    val bio: String,
    val avatarUrl: String? = null,
    val pricePerHourCents: Long? = null,
    val ratingAvg: Double? = null,
    val ratingCount: Long = 0,
)

@Serializable
data class DocDto(val id: Long, val fileName: String, val description: String)

@Serializable
data class TeacherApplicationDto(
    val id: Long,
    val userId: Long,
    val userName: String,
    val motivation: String,
    val headline: String,
    val status: String,
    val adminMessage: String? = null,
    val createdAt: Long,
    val documents: List<DocDto> = emptyList(),
)

@Serializable
data class DocInput(val fileName: String, val description: String = "")

@Serializable
data class SubmitApplicationRequest(val motivation: String, val headline: String = "", val documents: List<DocInput> = emptyList())

@Serializable
data class AdminDecisionRequest(val message: String? = null)

class TeacherApi(private val client: HttpClient, private val baseUrl: String) {
    suspend fun teachers(): HttpResponse = client.get("$baseUrl/api/teachers")
    suspend fun myApplication(): HttpResponse = client.get("$baseUrl/api/teacher-application")
    suspend fun submit(body: SubmitApplicationRequest): HttpResponse = client.post("$baseUrl/api/teacher-application") { setBody(body) }
    suspend fun pending(): HttpResponse = client.get("$baseUrl/api/admin/applications")
    suspend fun approve(id: Long): HttpResponse = client.post("$baseUrl/api/admin/applications/$id/approve")
    suspend fun reject(id: Long, message: String?): HttpResponse = client.post("$baseUrl/api/admin/applications/$id/reject") { setBody(AdminDecisionRequest(message)) }
}

interface TeacherRepository {
    suspend fun teachers(): Outcome<List<TeacherDto>>
    suspend fun myApplication(): Outcome<TeacherApplicationDto?>
    suspend fun submit(req: SubmitApplicationRequest): Outcome<TeacherApplicationDto>
    suspend fun pending(): Outcome<List<TeacherApplicationDto>>
    suspend fun approve(id: Long): Outcome<TeacherApplicationDto>
    suspend fun reject(id: Long, message: String?): Outcome<TeacherApplicationDto>
}

class TeacherRepositoryImpl(private val api: TeacherApi) : TeacherRepository {
    override suspend fun teachers() = safeApiCall({ api.teachers() }, { it.body<List<TeacherDto>>() })
    override suspend fun myApplication(): Outcome<TeacherApplicationDto?> =
        safeApiCall({ api.myApplication() }, { resp ->
            if (resp.status == HttpStatusCode.NoContent) null else resp.body<TeacherApplicationDto>()
        })
    override suspend fun submit(req: SubmitApplicationRequest) = safeApiCall({ api.submit(req) }, { it.body<TeacherApplicationDto>() })
    override suspend fun pending() = safeApiCall({ api.pending() }, { it.body<List<TeacherApplicationDto>>() })
    override suspend fun approve(id: Long) = safeApiCall({ api.approve(id) }, { it.body<TeacherApplicationDto>() })
    override suspend fun reject(id: Long, message: String?) = safeApiCall({ api.reject(id, message) }, { it.body<TeacherApplicationDto>() })
}

// Teachers catalog store
data class TeachersState(val loading: Boolean = true, val teachers: List<TeacherDto> = emptyList(), val error: String? = null)
sealed interface TeachersIntent { data object Load : TeachersIntent }
sealed interface TeachersEffect { data class Message(val text: String) : TeachersEffect }

class TeachersStore(private val repo: TeacherRepository, scope: CoroutineScope) :
    MviStore<TeachersState, TeachersIntent, TeachersEffect>(TeachersState(), scope) {
    override fun onIntent(intent: TeachersIntent) {
        setState { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = repo.teachers()) {
                is Outcome.Success -> setState { it.copy(loading = false, teachers = r.data) }
                is Outcome.Failure -> setState { it.copy(loading = false, error = r.message) }
            }
        }
    }
}

// Application store
data class ApplicationState(
    val loading: Boolean = true,
    val application: TeacherApplicationDto? = null,
    val motivation: String = "",
    val headline: String = "",
    val docName: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
)

sealed interface ApplicationIntent {
    data object Load : ApplicationIntent
    data class SetMotivation(val v: String) : ApplicationIntent
    data class SetHeadline(val v: String) : ApplicationIntent
    data class SetDocName(val v: String) : ApplicationIntent
    data object Submit : ApplicationIntent
}

sealed interface ApplicationEffect { data class Message(val text: String) : ApplicationEffect }

class ApplicationStore(private val repo: TeacherRepository, scope: CoroutineScope) :
    MviStore<ApplicationState, ApplicationIntent, ApplicationEffect>(ApplicationState(), scope) {
    override fun onIntent(intent: ApplicationIntent) {
        when (intent) {
            ApplicationIntent.Load -> load()
            is ApplicationIntent.SetMotivation -> setState { it.copy(motivation = intent.v) }
            is ApplicationIntent.SetHeadline -> setState { it.copy(headline = intent.v) }
            is ApplicationIntent.SetDocName -> setState { it.copy(docName = intent.v) }
            ApplicationIntent.Submit -> submit()
        }
    }
    private fun load() {
        setState { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = repo.myApplication()) {
                is Outcome.Success -> setState { it.copy(loading = false, application = r.data) }
                is Outcome.Failure -> setState { it.copy(loading = false, error = r.message) }
            }
        }
    }
    private fun submit() {
        val st = currentState
        if (st.submitting || st.motivation.isBlank()) return
        setState { it.copy(submitting = true) }
        scope.launch {
            val docs = if (st.docName.isBlank()) emptyList() else listOf(DocInput(st.docName.trim()))
            when (val r = repo.submit(SubmitApplicationRequest(st.motivation.trim(), st.headline.trim(), docs))) {
                is Outcome.Success -> { setState { it.copy(submitting = false, application = r.data) }; emit(ApplicationEffect.Message("Заявка отправлена ✨")) }
                is Outcome.Failure -> { setState { it.copy(submitting = false) }; emit(ApplicationEffect.Message(r.message)) }
            }
        }
    }
}

// Admin moderation store
data class AdminState(val loading: Boolean = true, val applications: List<TeacherApplicationDto> = emptyList(), val error: String? = null)
sealed interface AdminIntent {
    data object Load : AdminIntent
    data class Approve(val id: Long) : AdminIntent
    data class Reject(val id: Long) : AdminIntent
}
sealed interface AdminEffect { data class Message(val text: String) : AdminEffect }

class AdminStore(private val repo: TeacherRepository, scope: CoroutineScope) :
    MviStore<AdminState, AdminIntent, AdminEffect>(AdminState(), scope) {
    override fun onIntent(intent: AdminIntent) {
        when (intent) {
            AdminIntent.Load -> load()
            is AdminIntent.Approve -> act(intent.id, approve = true)
            is AdminIntent.Reject -> act(intent.id, approve = false)
        }
    }
    private fun load() {
        setState { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = repo.pending()) {
                is Outcome.Success -> setState { it.copy(loading = false, applications = r.data) }
                is Outcome.Failure -> setState { it.copy(loading = false, error = r.message) }
            }
        }
    }
    private fun act(id: Long, approve: Boolean) {
        scope.launch {
            val r = if (approve) repo.approve(id) else repo.reject(id, "Заявка отклонена")
            when (r) {
                is Outcome.Success -> { emit(AdminEffect.Message(if (approve) "Одобрено ✅" else "Отклонено")); load() }
                is Outcome.Failure -> emit(AdminEffect.Message(r.message))
            }
        }
    }
}
