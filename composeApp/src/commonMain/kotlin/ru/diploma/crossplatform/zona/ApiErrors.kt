package ru.diploma.crossplatform.zona

internal fun friendlyApiError(t: Throwable): String {
    val raw = t.message ?: ""
    val m = raw.lowercase()
    return when {
        "timeout" in m ||
            "timed out" in m ||
            "connect timed out" in m -> """
Сервер недоступен (таймаут).

На компьютере запустите бэкенд в каталоге проекта:
  gradlew.bat :server:run

Должен слушать порт 8080. Для эмулятора Android адрес уже верный: 10.0.2.2 — это ваш ПК.

На телефоне укажите IP компьютера в локальной сети (файл Platform.android.kt).
            """.trimIndent()

        "connection refused" in m ||
            "failed to connect" in m ||
            "unreachable" in m ||
            "connection reset" in m -> """
Не удалось подключиться к серверу.

Запустите: gradlew.bat :server:run
Проверьте, что брандмауэр Windows разрешает входящие подключения к Java на порту 8080.
            """.trimIndent()

        t is ZonaApiException -> raw.ifBlank { "Ошибка API" }

        else -> raw.ifBlank { "Ошибка сети" }
    }
}
