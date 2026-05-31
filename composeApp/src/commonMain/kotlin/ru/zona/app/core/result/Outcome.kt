package ru.zona.app.core.result

import ru.zona.app.core.error.ErrorType

/**
 * Результат операции слоя data/domain. В отличие от kotlin [Result], несёт типизированную
 * категорию ошибки ([ErrorType]) и готовое сообщение для UI.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>
    data class Failure(val type: ErrorType, val message: String) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(data))
        is Outcome.Failure -> this
    }

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(data)
    return this
}

inline fun <T> Outcome<T>.onFailure(action: (Outcome.Failure) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(this)
    return this
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.data
