package ru.zona.app.feature.wallet

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
data class WalletDto(val balanceCents: Long, val transactions: List<WalletTxDto> = emptyList())

@Serializable
data class WalletTxDto(val id: Long, val amountCents: Long, val kind: String, val note: String, val createdAt: Long)

@Serializable
data class TopUpRequest(val amountCents: Long)

class WalletApi(private val client: HttpClient, private val baseUrl: String) {
    suspend fun wallet(): HttpResponse = client.get("$baseUrl/api/wallet")
    suspend fun topUp(amount: Long): HttpResponse = client.post("$baseUrl/api/wallet/topup") { setBody(TopUpRequest(amount)) }
}

interface WalletRepository {
    suspend fun wallet(): Outcome<WalletDto>
    suspend fun topUp(amountCents: Long): Outcome<WalletDto>
}

class WalletRepositoryImpl(private val api: WalletApi) : WalletRepository {
    override suspend fun wallet(): Outcome<WalletDto> = safeApiCall({ api.wallet() }, { it.body() })
    override suspend fun topUp(amountCents: Long): Outcome<WalletDto> = safeApiCall({ api.topUp(amountCents) }, { it.body() })
}

data class WalletState(
    val loading: Boolean = true,
    val wallet: WalletDto? = null,
    val busy: Boolean = false,
    val customAmount: String = "",
    val error: String? = null,
)

sealed interface WalletIntent {
    data object Load : WalletIntent
    data class TopUp(val amountCents: Long) : WalletIntent
    data class SetCustomAmount(val value: String) : WalletIntent
    data object TopUpCustom : WalletIntent
}

sealed interface WalletEffect { data class Message(val text: String) : WalletEffect }

class WalletStore(
    private val repo: WalletRepository,
    scope: CoroutineScope,
) : MviStore<WalletState, WalletIntent, WalletEffect>(WalletState(), scope) {
    override fun onIntent(intent: WalletIntent) {
        when (intent) {
            WalletIntent.Load -> load()
            is WalletIntent.TopUp -> topUp(intent.amountCents)
            is WalletIntent.SetCustomAmount -> setState { it.copy(customAmount = intent.value.filter { c -> c.isDigit() }.take(7)) }
            WalletIntent.TopUpCustom -> {
                val amount = currentState.customAmount.toLongOrNull()
                if (amount != null && amount > 0) { setState { it.copy(customAmount = "") }; topUp(amount * 100) }
            }
        }
    }

    private fun load() {
        setState { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = repo.wallet()) {
                is Outcome.Success -> setState { it.copy(loading = false, wallet = r.data) }
                is Outcome.Failure -> setState { it.copy(loading = false, error = r.message) }
            }
        }
    }

    private fun topUp(amount: Long) {
        if (currentState.busy) return
        setState { it.copy(busy = true) }
        scope.launch {
            when (val r = repo.topUp(amount)) {
                is Outcome.Success -> {
                    setState { it.copy(busy = false, wallet = r.data) }
                    emit(WalletEffect.Message("Баланс пополнен 🛰"))
                }
                is Outcome.Failure -> {
                    setState { it.copy(busy = false) }
                    emit(WalletEffect.Message(r.message))
                }
            }
        }
    }
}
