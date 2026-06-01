package ru.zona.server.feature.wallet

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import ru.zona.server.db.WalletTransactions
import ru.zona.server.db.Wallets
import ru.zona.server.plugins.ApiException
import ru.zona.server.security.AUTH_JWT
import ru.zona.server.security.requireUserId

@Serializable
data class WalletDto(val balanceCents: Long, val transactions: List<WalletTxDto>)

@Serializable
data class WalletTxDto(val id: Long, val amountCents: Long, val kind: String, val note: String, val createdAt: Long)

@Serializable
data class TopUpRequest(val amountCents: Long)

/** Кошелёк (имитация платежей). Списания/начисления — атомарно в одной транзакции. */
class WalletService {
    fun ensureWallet(userId: Long) {
        transaction {
            val exists = Wallets.selectAll().where { Wallets.userId eq userId }.limit(1).count() > 0
            if (!exists) Wallets.insert { it[Wallets.userId] = userId; it[balanceCents] = 0 }
        }
    }

    fun balance(userId: Long): Long =
        transaction {
            Wallets.selectAll().where { Wallets.userId eq userId }.firstOrNull()?.get(Wallets.balanceCents) ?: 0
        }

    fun wallet(userId: Long): WalletDto =
        transaction {
            val bal = Wallets.selectAll().where { Wallets.userId eq userId }.firstOrNull()?.get(Wallets.balanceCents) ?: 0
            val txs =
                WalletTransactions.selectAll().where { WalletTransactions.userId eq userId }
                    .orderBy(WalletTransactions.createdAt to SortOrder.DESC)
                    .limit(50)
                    .map {
                        WalletTxDto(
                            it[WalletTransactions.id], it[WalletTransactions.amountCents],
                            it[WalletTransactions.kind], it[WalletTransactions.note], it[WalletTransactions.createdAt],
                        )
                    }
            WalletDto(bal, txs)
        }

    fun topUp(userId: Long, amountCents: Long): WalletDto {
        if (amountCents <= 0) throw ApiException(HttpStatusCode.UnprocessableEntity, "Сумма должна быть положительной")
        transaction {
            ensureRow(userId)
            val cur = Wallets.selectAll().where { Wallets.userId eq userId }.first()[Wallets.balanceCents]
            Wallets.update({ Wallets.userId eq userId }) { it[balanceCents] = cur + amountCents }
            recordTx(userId, amountCents, "TOPUP", "Пополнение баланса")
        }
        return wallet(userId)
    }

    /** Списание под покупку. Бросает 409 при недостатке средств. Вызывать внутри внешней транзакции нельзя — открывает свою. */
    fun charge(userId: Long, amountCents: Long, note: String) {
        if (amountCents <= 0) return
        transaction {
            ensureRow(userId)
            val cur = Wallets.selectAll().where { Wallets.userId eq userId }.first()[Wallets.balanceCents]
            if (cur < amountCents) {
                throw ApiException(HttpStatusCode.PaymentRequired, "Недостаточно средств на балансе")
            }
            Wallets.update({ Wallets.userId eq userId }) { it[balanceCents] = cur - amountCents }
            recordTx(userId, -amountCents, "PURCHASE", note)
        }
    }

    private fun ensureRow(userId: Long) {
        val exists = Wallets.selectAll().where { Wallets.userId eq userId }.limit(1).count() > 0
        if (!exists) Wallets.insert { it[Wallets.userId] = userId; it[balanceCents] = 0 }
    }

    private fun recordTx(userId: Long, amount: Long, kind: String, note: String) {
        WalletTransactions.insert {
            it[WalletTransactions.userId] = userId
            it[amountCents] = amount
            it[WalletTransactions.kind] = kind
            it[WalletTransactions.note] = note
            it[createdAt] = System.currentTimeMillis()
        }
    }
}

fun Route.walletRoutes(service: WalletService) {
    authenticate(AUTH_JWT) {
        get("/api/wallet") { call.respond(service.wallet(requireUserId())) }
        post("/api/wallet/topup") {
            call.respond(service.topUp(requireUserId(), call.receive<TopUpRequest>().amountCents))
        }
    }
}
