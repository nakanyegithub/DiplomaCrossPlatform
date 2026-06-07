package ru.zona.app.feature.profile

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
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
data class CertificateDto(val id: Long, val fileName: String, val createdAt: Long)

@Serializable
data class AddCertificateRequest(val fileName: String)

class CertificateApi(private val client: HttpClient, private val baseUrl: String) {
    suspend fun list(): HttpResponse = client.get("$baseUrl/api/me/certificates")
    suspend fun add(fileName: String): HttpResponse = client.post("$baseUrl/api/me/certificates") { setBody(AddCertificateRequest(fileName)) }
    suspend fun remove(id: Long): HttpResponse = client.delete("$baseUrl/api/me/certificates/$id")
}

interface CertificateRepository {
    suspend fun list(): Outcome<List<CertificateDto>>
    suspend fun add(fileName: String): Outcome<CertificateDto>
    suspend fun remove(id: Long): Outcome<Unit>
}

class CertificateRepositoryImpl(private val api: CertificateApi) : CertificateRepository {
    override suspend fun list() = safeApiCall({ api.list() }, { it.body<List<CertificateDto>>() })
    override suspend fun add(fileName: String) = safeApiCall({ api.add(fileName) }, { it.body<CertificateDto>() })
    override suspend fun remove(id: Long) = safeApiCall({ api.remove(id) }, { })
}

data class CertificatesState(
    val loading: Boolean = true,
    val items: List<CertificateDto> = emptyList(),
    val error: String? = null,
)

sealed interface CertificatesIntent {
    data object Load : CertificatesIntent
    data class Add(val fileName: String) : CertificatesIntent
    data class Remove(val id: Long) : CertificatesIntent
}

sealed interface CertificatesEffect { data class Message(val text: String) : CertificatesEffect }

class CertificatesStore(private val repo: CertificateRepository, scope: CoroutineScope) :
    MviStore<CertificatesState, CertificatesIntent, CertificatesEffect>(CertificatesState(), scope) {
    override fun onIntent(intent: CertificatesIntent) {
        when (intent) {
            CertificatesIntent.Load -> load()
            is CertificatesIntent.Add -> add(intent.fileName)
            is CertificatesIntent.Remove -> remove(intent.id)
        }
    }
    private fun load() {
        setState { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = repo.list()) {
                is Outcome.Success -> setState { it.copy(loading = false, items = r.data) }
                is Outcome.Failure -> setState { it.copy(loading = false, error = r.message) }
            }
        }
    }
    private fun add(fileName: String) {
        scope.launch {
            when (val r = repo.add(fileName)) {
                is Outcome.Success -> { emit(CertificatesEffect.Message("Сертификат добавлен")); load() }
                is Outcome.Failure -> emit(CertificatesEffect.Message(r.message))
            }
        }
    }
    private fun remove(id: Long) {
        scope.launch {
            when (val r = repo.remove(id)) {
                is Outcome.Success -> load()
                is Outcome.Failure -> emit(CertificatesEffect.Message(r.message))
            }
        }
    }
}
