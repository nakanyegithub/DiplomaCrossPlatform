package ru.zona.app.core.error

/** Категории ошибок, на которые осмысленно реагирует UI. */
enum class ErrorType {
    NO_CONNECTION,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    SERVER_ERROR,
    UNKNOWN,
}

/** HTTP-коды, на которые завязан маппинг (а не текст ответа). */
object HttpStatus {
    const val NO_CONNECTION = -1
    const val BAD_REQUEST = 400
    const val UNAUTHORIZED = 401
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
    const val CONFLICT = 409
    const val UNPROCESSABLE = 422
    const val SERVER_ERROR = 500
}

fun errorTypeFromStatus(code: Int): ErrorType =
    when (code) {
        HttpStatus.NO_CONNECTION -> ErrorType.NO_CONNECTION
        HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE -> ErrorType.BAD_REQUEST
        HttpStatus.UNAUTHORIZED -> ErrorType.UNAUTHORIZED
        HttpStatus.FORBIDDEN -> ErrorType.FORBIDDEN
        HttpStatus.NOT_FOUND -> ErrorType.NOT_FOUND
        HttpStatus.CONFLICT -> ErrorType.CONFLICT
        in 500..599 -> ErrorType.SERVER_ERROR
        else -> ErrorType.UNKNOWN
    }

/** Текст по умолчанию для категории (UI может переопределить). */
fun ErrorType.defaultMessage(): String =
    when (this) {
        ErrorType.NO_CONNECTION -> "Нет соединения с сервером"
        ErrorType.BAD_REQUEST -> "Проверьте введённые данные"
        ErrorType.UNAUTHORIZED -> "Войдите снова"
        ErrorType.FORBIDDEN -> "Недостаточно прав"
        ErrorType.NOT_FOUND -> "Не найдено"
        ErrorType.CONFLICT -> "Конфликт данных"
        ErrorType.SERVER_ERROR -> "Ошибка сервера, попробуйте позже"
        ErrorType.UNKNOWN -> "Что-то пошло не так"
    }
