package ru.zona.app.core.network

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import ru.zona.app.core.error.ErrorType
import ru.zona.app.core.error.HttpStatus
import ru.zona.app.core.error.defaultMessage
import ru.zona.app.core.error.errorTypeFromStatus
import ru.zona.app.core.result.Outcome

/**
 * Выполняет HTTP-запрос и приводит результат к [Outcome], классифицируя ошибку по **коду** ответа,
 * а не по тексту исключения. Декодирование тела — через [decode].
 */
suspend inline fun <reified T> safeApiCall(
    crossinline request: suspend () -> HttpResponse,
    crossinline decode: suspend (HttpResponse) -> T,
): Outcome<T> =
    try {
        val response = request()
        if (response.status.value in 200..299) {
            Outcome.Success(decode(response))
        } else {
            val type = errorTypeFromStatus(response.status.value)
            Outcome.Failure(type, serverMessage(response) ?: type.defaultMessage())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: UnresolvedAddressException) {
        val t = errorTypeFromStatus(HttpStatus.NO_CONNECTION)
        Outcome.Failure(t, t.defaultMessage())
    } catch (e: IOException) {
        val t = errorTypeFromStatus(HttpStatus.NO_CONNECTION)
        Outcome.Failure(t, t.defaultMessage())
    } catch (e: Throwable) {
        Outcome.Failure(ErrorType.UNKNOWN, e.message ?: ErrorType.UNKNOWN.defaultMessage())
    }

/** Пытается достать осмысленный текст ошибки из тела [ApiErrorBody]. */
suspend fun serverMessage(response: HttpResponse): String? =
    try {
        val body = response.body<ApiErrorBody>()
        body.error.ifBlank { null }
    } catch (_: Throwable) {
        try {
            response.bodyAsText().ifBlank { null }
        } catch (_: Throwable) {
            null
        }
    }
